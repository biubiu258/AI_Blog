package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.tech.TechTermRequest;
import com.aiblog.entity.TechTerm;
import com.aiblog.mapper.TechTermMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 技术术语表：CRUD 与术语规则检查。
 * 规则引擎不依赖 LLM：按变体与规范写法生成正则，长词优先、命中区间不重叠。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechTermService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    private static final String DEFAULT_CATEGORY = "通用";
    /** 变体分隔符：英文/中文逗号、顿号、分号、换行 */
    private static final Pattern VARIANT_SPLIT = Pattern.compile("[,，、;；\\n]+");

    private final TechTermMapper termMapper;

    /** 公开术语列表：默认只返回启用项。 */
    public List<TechTerm> list(String keyword, String category, boolean onlyActive) {
        return termMapper.selectList(Wrappers.<TechTerm>lambdaQuery()
                .eq(onlyActive, TechTerm::getStatus, STATUS_ACTIVE)
                .eq(StringUtils.hasText(category), TechTerm::getCategory, category)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(TechTerm::getCanonical, keyword)
                        .or().like(TechTerm::getDefinition, keyword)
                        .or().like(TechTerm::getVariants, keyword))
                .orderByAsc(TechTerm::getCategory)
                .orderByAsc(TechTerm::getCanonical));
    }

    public TechTerm create(TechTermRequest request, Long userId) {
        TechTerm term = new TechTerm();
        apply(term, request);
        term.setCreatedBy(userId);
        try {
            termMapper.insert(term);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一分类下已存在该规范写法");
        }
        return term;
    }

    public TechTerm update(Long id, TechTermRequest request) {
        TechTerm existing = detail(id);
        apply(existing, request);
        try {
            termMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一分类下已存在该规范写法");
        }
        return existing;
    }

    public void delete(Long id) {
        detail(id);
        termMapper.deleteById(id);
    }

    /**
     * 批量导入：按「规范写法 + 分类」幂等写入，返回新增/更新数量。
     */
    @Transactional
    public Map<String, Object> importTerms(List<TechTermRequest> items, Long userId) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (TechTermRequest request : items) {
            if (request == null || !StringUtils.hasText(request.getCanonical())) {
                skipped++;
                continue;
            }
            String category = StringUtils.hasText(request.getCategory())
                    ? request.getCategory().trim() : DEFAULT_CATEGORY;
            TechTerm existing = termMapper.selectOne(Wrappers.<TechTerm>lambdaQuery()
                    .eq(TechTerm::getCanonical, request.getCanonical().trim())
                    .eq(TechTerm::getCategory, category)
                    .last("LIMIT 1"));
            if (existing == null) {
                create(request, userId);
                inserted++;
            } else {
                apply(existing, request);
                termMapper.updateById(existing);
                updated++;
            }
        }
        return Map.of("inserted", inserted, "updated", updated, "skipped", skipped);
    }

    /**
     * 术语规则检查：返回 type=terminology 的问题项，按出现位置排序。
     */
    public List<Map<String, Object>> check(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<TechTerm> terms = list(null, null, true);
        List<Candidate> candidates = new ArrayList<>();
        for (TechTerm term : terms) {
            if (term == null || !STATUS_ACTIVE.equals(term.getStatus())
                    || !StringUtils.hasText(term.getCanonical())) {
                continue;
            }
            for (Pattern pattern : patternsOf(term)) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String quote = matcher.group();
                    // 已经是规范写法时无需提示
                    if (!StringUtils.hasText(quote) || quote.equals(term.getCanonical())) {
                        continue;
                    }
                    candidates.add(new Candidate(matcher.start(), matcher.end(), quote, term));
                }
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 长词优先：同起点时长词先占位，避免短词截断长词
        candidates.sort(Comparator.comparingInt(Candidate::length).reversed()
                .thenComparingInt(Candidate::start));
        List<Candidate> accepted = new ArrayList<>();
        List<int[]> ranges = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean overlap = false;
            for (int[] range : ranges) {
                if (candidate.start() < range[1] && candidate.end() > range[0]) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                accepted.add(candidate);
                ranges.add(new int[]{candidate.start(), candidate.end()});
            }
        }
        accepted.sort(Comparator.comparingInt(Candidate::start));

        List<Map<String, Object>> issues = new ArrayList<>(accepted.size());
        for (Candidate candidate : accepted) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("id", "rule-" + candidate.term().getId() + "-" + candidate.start());
            issue.put("type", "terminology");
            issue.put("quote", candidate.quote());
            issue.put("replacement", candidate.term().getCanonical());
            issue.put("reason", "统一使用规范写法「" + candidate.term().getCanonical() + "」");
            issue.put("severity", "mid");
            issue.put("evidenceChunkId", null);
            issue.put("evidence", null);
            issues.add(issue);
        }
        return issues;
    }

    private TechTerm detail(Long id) {
        TechTerm term = termMapper.selectById(id);
        if (term == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }
        return term;
    }

    private void apply(TechTerm term, TechTermRequest request) {
        if (request == null || !StringUtils.hasText(request.getCanonical())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "规范写法不能为空");
        }
        term.setCanonical(request.getCanonical().trim());
        term.setCategory(StringUtils.hasText(request.getCategory())
                ? request.getCategory().trim() : DEFAULT_CATEGORY);
        term.setDefinition(trimOrNull(request.getDefinition()));
        term.setVariants(trimOrNull(request.getVariants()));
        term.setCaseSensitive(Boolean.TRUE.equals(request.getCaseSensitive()) ? 1 : 0);
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ACTIVE;
        if (!STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态仅支持 active/disabled");
        }
        term.setStatus(status);
    }

    private static String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 生成该术语的正则集合：规范写法 + 拆分后的变体。
     * 同一写法只保留一份，避免重复命中。
     */
    private List<Pattern> patternsOf(TechTerm term) {
        LinkedHashSet<String> literals = new LinkedHashSet<>();
        literals.add(term.getCanonical().trim());
        if (StringUtils.hasText(term.getVariants())) {
            for (String part : VARIANT_SPLIT.split(term.getVariants())) {
                String literal = part.trim();
                if (!literal.isEmpty()) {
                    literals.add(literal);
                }
            }
        }
        int flags = Integer.valueOf(1).equals(term.getCaseSensitive())
                ? 0 : Pattern.CASE_INSENSITIVE;
        List<Pattern> patterns = new ArrayList<>(literals.size());
        for (String literal : literals) {
            try {
                patterns.add(Pattern.compile(wrapBoundary(literal), flags));
            } catch (PatternSyntaxException e) {
                log.warn("术语正则非法，已跳过 canonical={} literal={}", term.getCanonical(), literal);
            }
        }
        return patterns;
    }

    /**
     * ASCII 词加词边界，避免 token 命中 tokenizer；中文不加边界。
     */
    private static String wrapBoundary(String literal) {
        StringBuilder sb = new StringBuilder();
        if (isAsciiWord(literal.charAt(0))) {
            sb.append("(?<![A-Za-z0-9_])");
        }
        sb.append(Pattern.quote(literal));
        if (isAsciiWord(literal.charAt(literal.length() - 1))) {
            sb.append("(?![A-Za-z0-9_])");
        }
        return sb.toString();
    }

    private static boolean isAsciiWord(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private record Candidate(int start, int end, String quote, TechTerm term) {
        int length() {
            return end - start;
        }
    }
}

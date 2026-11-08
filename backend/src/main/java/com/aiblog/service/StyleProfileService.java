package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.ai.PythonErrorTranslator;
import com.aiblog.ai.PythonResponseException;
import com.aiblog.config.AiProperties;
import com.aiblog.entity.Article;
import com.aiblog.entity.StyleProfile;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.StyleProfileMapper;
import com.aiblog.security.CurrentUser;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 个人文风库：用户选 3–10 篇旧文，调用 Python /v1/style-profile 提炼风格特征，
 * 生成/审阅时注入个人文风卡。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleProfileService {

    public static final int MIN_SAMPLES = 3;
    public static final int MAX_SAMPLES = 10;
    /** 单篇样本送审的纯文本上限，与 Python 契约一致 */
    private static final int MAX_SAMPLE_TEXT = 20000;
    private static final int MIN_SAMPLE_TEXT = 50;

    public static final String STATUS_BUILDING = "building";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    private final StyleProfileMapper styleProfileMapper;
    private final ArticleMapper articleMapper;
    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final PythonErrorTranslator errorTranslator;
    private final ObjectMapper objectMapper;

    /** 提炼并创建文风档案（同步调用；失败时落 failed 记录供删除/重试） */
    public Map<String, Object> create(String name, List<Long> articleIds) {
        if (!StringUtils.hasText(name) || name.strip().length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风格名称必填且不超过 64 字");
        }
        if (articleIds == null || articleIds.size() < MIN_SAMPLES || articleIds.size() > MAX_SAMPLES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "需选择 " + MIN_SAMPLES + "–" + MAX_SAMPLES + " 篇自己的文章作为样本");
        }
        Long userId = CurrentUser.id();
        List<Article> samples = loadSamples(userId, articleIds);

        StyleProfile profile = new StyleProfile();
        profile.setUserId(userId);
        profile.setName(name.strip());
        profile.setSampleCount(samples.size());
        profile.setStatus(STATUS_BUILDING);
        profile.setIsDefault(0);
        styleProfileMapper.insert(profile);

        try {
            Map<String, Object> response = callProfiler(userId, samples);
            profile.setSummary((String) response.get("summary"));
            profile.setFeatures(writeJson(response.get("features")));
            profile.setStatus(STATUS_READY);
        } catch (Exception e) {
            log.warn("文风提炼失败 userId={} profile={}: {}", userId, profile.getId(), e.getMessage());
            profile.setStatus(STATUS_FAILED);
        }
        profile.setUpdatedAt(LocalDateTime.now());
        styleProfileMapper.updateById(profile);
        return toMap(profile);
    }

    /** 我的文风列表 */
    public List<Map<String, Object>> listMine() {
        return styleProfileMapper.selectList(Wrappers.<StyleProfile>lambdaQuery()
                        .eq(StyleProfile::getUserId, CurrentUser.id())
                        .orderByDesc(StyleProfile::getIsDefault)
                        .orderByDesc(StyleProfile::getId))
                .stream().map(this::toMap).toList();
    }

    /** 删除自己的文风档案 */
    public void delete(Long id) {
        StyleProfile profile = mustGetOwned(id);
        styleProfileMapper.deleteById(profile.getId());
    }

    /** 设为默认文风：生成时未显式指定即使用默认档 */
    @Transactional
    public Map<String, Object> setDefault(Long id) {
        StyleProfile profile = mustGetOwned(id);
        if (!STATUS_READY.equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅提炼完成的文风可设为默认");
        }
        List<StyleProfile> defaults = styleProfileMapper.selectList(
                Wrappers.<StyleProfile>lambdaQuery()
                        .eq(StyleProfile::getUserId, profile.getUserId())
                        .eq(StyleProfile::getIsDefault, 1));
        for (StyleProfile other : defaults) {
            if (!Objects.equals(other.getId(), profile.getId())) {
                StyleProfile patch = new StyleProfile();
                patch.setId(other.getId());
                patch.setIsDefault(0);
                styleProfileMapper.updateById(patch);
            }
        }
        profile.setIsDefault(1);
        styleProfileMapper.updateById(profile);
        return toMap(profile);
    }

    /**
     * 解析生成时注入的文风档案：显式指定 id 时校验归属；否则取用户默认档。
     * 仅 ready 状态返回，其他情况返回 null（不阻塞生成）。
     */
    public StyleProfile resolveForGeneration(Long userId, Long profileId) {
        if (profileId != null) {
            StyleProfile profile = styleProfileMapper.selectById(profileId);
            if (profile == null || !Objects.equals(profile.getUserId(), userId)
                    || !STATUS_READY.equals(profile.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "指定的文风档案不可用");
            }
            return profile;
        }
        StyleProfile profile = styleProfileMapper.selectOne(Wrappers.<StyleProfile>lambdaQuery()
                .eq(StyleProfile::getUserId, userId)
                .eq(StyleProfile::getIsDefault, 1)
                .eq(StyleProfile::getStatus, STATUS_READY)
                .last("LIMIT 1"));
        return profile;
    }

    private Map<String, Object> callProfiler(Long userId, List<Article> samples) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        List<Map<String, Object>> payloadSamples = new ArrayList<>();
        for (Article article : samples) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", article.getTitle());
            item.put("text", Jsoup.parse(article.getContentHtml()).text());
            payloadSamples.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trace_id", traceId);
        payload.put("user_id", userId);
        payload.put("samples", payloadSamples);

        String body = aiWebClient.post()
                .uri("/v1/style-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(raw -> Mono.error(
                                        new PythonResponseException(response.statusCode(), raw))))
                .bodyToMono(String.class)
                .block(Duration.ofMillis(properties.getStyleProfileReadTimeoutMs()));
        if (!StringUtils.hasText(body)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_OUTPUT, "文风提炼结果为空，请重试");
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", node.path("summary").asText(""));
            Map<String, Object> features = new LinkedHashMap<>();
            JsonNode featuresNode = node.path("features");
            if (featuresNode.isObject()) {
                featuresNode.fields().forEachRemaining(entry ->
                        features.put(entry.getKey(), entry.getValue().asText("")));
            }
            result.put("features", features);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "文风提炼结果无法解析，请重试");
        }
    }

    /** 样本必须是本人的文章，正文转纯文本后不少于 50 字 */
    private List<Article> loadSamples(Long userId, List<Long> articleIds) {
        List<Article> articles = new ArrayList<>();
        for (Long id : articleIds.stream().distinct().toList()) {
            Article article = articleMapper.selectById(id);
            if (article == null || !Objects.equals(article.getAuthorId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "样本只能选择自己的文章");
            }
            String text = Jsoup.parse(article.getContentHtml() == null ? "" : article.getContentHtml()).text();
            if (text.strip().length() < MIN_SAMPLE_TEXT) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "《" + article.getTitle() + "》正文过短，请选择内容更充实的老文章");
            }
            articles.add(article);
        }
        return articles;
    }

    private StyleProfile mustGetOwned(Long id) {
        StyleProfile profile = styleProfileMapper.selectById(id);
        if (profile == null || !Objects.equals(profile.getUserId(), CurrentUser.id())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文风档案不存在");
        }
        return profile;
    }

    private Map<String, Object> toMap(StyleProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", profile.getId());
        map.put("name", profile.getName());
        map.put("summary", profile.getSummary());
        map.put("sampleCount", profile.getSampleCount());
        map.put("isDefault", profile.getIsDefault() != null && profile.getIsDefault() == 1);
        map.put("status", profile.getStatus());
        map.put("createdAt", profile.getCreatedAt());
        return map;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }
}

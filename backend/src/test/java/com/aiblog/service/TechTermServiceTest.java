package com.aiblog.service;

import com.aiblog.entity.TechTerm;
import com.aiblog.mapper.TechTermMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 术语规则引擎纯逻辑单测：大小写、变体、ASCII 边界、禁用术语与长词优先。
 */
class TechTermServiceTest {

    private final TechTermMapper termMapper = mock(TechTermMapper.class);
    private final TechTermService service = new TechTermService(termMapper);

    private TechTerm term(String canonical, String variants, int caseSensitive, String status) {
        TechTerm term = new TechTerm();
        term.setId(1L);
        term.setCanonical(canonical);
        term.setCategory("通用");
        term.setVariants(variants);
        term.setCaseSensitive(caseSensitive);
        term.setStatus(status);
        return term;
    }

    private List<Map<String, Object>> check(List<TechTerm> terms, String text) {
        when(termMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(terms);
        return service.check(text);
    }

    @Test
    void matchesVariantIgnoringCase() {
        List<Map<String, Object>> issues = check(
                List.of(term("令牌", "token", 0, "active")), "请检查 Token 是否过期");
        assertEquals(1, issues.size());
        Map<String, Object> issue = issues.get(0);
        assertEquals("terminology", issue.get("type"));
        assertEquals("Token", issue.get("quote"));
        assertEquals("令牌", issue.get("replacement"));
    }

    @Test
    void prefersLongestVariant() {
        List<Map<String, Object>> issues = check(
                List.of(term("令牌", "token、token 过期", 0, "active")), "token 过期后需要重新登录");
        assertEquals(1, issues.size());
        assertEquals("token 过期", issues.get(0).get("quote"));
    }

    @Test
    void asciiBoundaryAvoidsPartialWord() {
        List<Map<String, Object>> issues = check(
                List.of(term("令牌", "token", 0, "active")), "tokenizer 与 token 不是一回事");
        // 只命中独立的 token，不命中 tokenizer 中的 token
        assertEquals(1, issues.size());
        assertEquals("token", issues.get(0).get("quote"));
    }

    @Test
    void disabledTermIsIgnoredAndCorrectCanonicalIsSilent() {
        List<Map<String, Object>> disabled = check(
                List.of(term("令牌", "token", 0, "disabled")), "请检查 token");
        assertTrue(disabled.isEmpty());

        List<Map<String, Object>> canonical = check(
                List.of(term("令牌", "token", 0, "active")), "请检查令牌是否过期");
        assertTrue(canonical.isEmpty());
    }

    @Test
    void caseSensitiveTermKeepsCase() {
        List<Map<String, Object>> issues = check(
                List.of(term("API", "api", 1, "active")), "这里写 api 不规范");
        // case_sensitive=1 时 api 不匹配规范写法 API，也不匹配变体 api？变体本身是 api
        // 变体命中仍属规则命中，replacement 仍是 API
        assertFalse(issues.isEmpty());
        assertEquals("api", issues.get(0).get("quote"));
        assertEquals("API", issues.get(0).get("replacement"));
    }
}

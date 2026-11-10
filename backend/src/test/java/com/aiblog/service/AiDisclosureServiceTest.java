package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.Article;
import com.aiblog.entity.ContentLabelLog;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.ContentLabelLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiDisclosureService 纯逻辑单测：等级校验、默认建议、trace 关联与导出标识。
 */
class AiDisclosureServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final AiOutputMapper aiOutputMapper = mock(AiOutputMapper.class);
    private final ContentLabelLogMapper labelLogMapper = mock(ContentLabelLogMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiDisclosureService service =
            new AiDisclosureService(articleMapper, aiOutputMapper, labelLogMapper, objectMapper);

    @Test
    void normalizeAcceptsKnownLevelsAndRejectsUnknown() {
        assertEquals("none", service.normalizeOrNull("NONE"));
        assertEquals("assisted", service.normalizeOrNull(" assisted "));
        assertEquals("generated", service.normalizeOrNull("generated"));
        assertNull(service.normalizeOrNull(null));
        assertNull(service.normalizeOrNull("  "));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.normalizeOrNull("partially"));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    void requireDeclaredRejectsMissingLevel() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireDeclared(null));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("generated", service.requireDeclared("Generated"));
    }

    @Test
    void suggestRecommendsNoneWithoutAiCalls() {
        when(aiOutputMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = service.suggest(11L);

        assertEquals("none", result.get("suggestedLevel"));
        assertEquals(0L, result.get("aiCallCount"));
    }

    @Test
    void suggestRecommendsAssistedWithAdoptedCalls() {
        when(aiOutputMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> result = service.suggest(11L);

        assertEquals("assisted", result.get("suggestedLevel"));
        assertEquals(3L, result.get("aiCallCount"));
    }

    @Test
    void applyDeclarationWritesLevelTimeAndTraceIds() {
        Article article = new Article();
        article.setId(1L);
        article.setAuthorId(2L);
        when(aiOutputMapper.selectList(any())).thenReturn(List.of(
                aiOutput("t-aaa"), aiOutput("t-bbb"), aiOutput("t-aaa")));

        service.applyDeclaration(article, "assisted");

        assertEquals("assisted", article.getAiUsageLevel());
        assertNotNull(article.getAiDeclaredAt());
        List<String> traceIds = readTraceIds(article.getAiTraceIds());
        assertEquals(List.of("t-aaa", "t-bbb"), traceIds);
    }

    @Test
    void applyDeclarationSkipsNullLevel() {
        Article article = new Article();
        article.setId(1L);

        service.applyDeclaration(article, null);

        assertNull(article.getAiUsageLevel());
        assertNull(article.getAiDeclaredAt());
        verify(aiOutputMapper, never()).selectList(any());
    }

    @Test
    void logChangeSkipsSameLevel() {
        service.logChange(1L, "assisted", "assisted", 9L, "重复");

        verify(labelLogMapper, never()).insert(any(ContentLabelLog.class));
    }

    @Test
    void logChangeWritesRecordForLevelChange() {
        service.logChange(1L, "none", "generated", 9L, "升级声明");

        ArgumentCaptor<ContentLabelLog> captor = ArgumentCaptor.forClass(ContentLabelLog.class);
        verify(labelLogMapper).insert(captor.capture());
        ContentLabelLog saved = captor.getValue();
        assertEquals(1L, saved.getArticleId());
        assertEquals("none", saved.getFromLevel());
        assertEquals("generated", saved.getToLevel());
        assertEquals(9L, saved.getOperatorId());
        assertEquals("升级声明", saved.getReason());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void exportAddsExplicitLabelForAssistedContent() {
        Article article = baseArticle();

        String markdown = service.buildExportMarkdown(article, "作者", "散文", List.of("随笔"));

        assertTrue(markdown.contains("ai_usage_level: assisted"));
        assertTrue(markdown.contains("ai_content_id: AIB-ARTICLE-1"));
        assertEquals(2, countOccurrences(markdown, "【AI 内容标识】"),
                "显式标识应同时出现在正文首尾");
        assertTrue(markdown.contains("# 标题"));
        assertTrue(markdown.contains("作者：作者"));
        assertTrue(markdown.endsWith("<!-- AI 内容标识结束 -->\n"));
    }

    @Test
    void exportOmitsExplicitLabelForHumanOnlyContent() {
        Article article = baseArticle();
        article.setAiUsageLevel("none");

        String markdown = service.buildExportMarkdown(article, null, null, List.of());

        assertFalse(markdown.contains("【AI 内容标识】"));
        assertTrue(markdown.contains("ai_usage_level: none"),
                "隐式标识（元数据）无论等级都必须存在");
        assertTrue(markdown.contains("ai_service: AI_blog"));
    }

    @Test
    void exportWritesTraceIdsIntoMetadata() {
        Article article = baseArticle();
        article.setAiTraceIds("[\"t-1\",\"t-2\"]");

        String markdown = service.buildExportMarkdown(article, "作者", null, List.of());

        assertTrue(markdown.contains("ai_trace_ids: t-1,t-2"));
    }

    @Test
    void exportConvertsHtmlParagraphsToPlainLines() {
        Article article = baseArticle();
        article.setContentHtml("<p>第一段。</p><p>第二段。</p>");

        String markdown = service.buildExportMarkdown(article, null, null, List.of());

        assertTrue(markdown.contains("第一段。"));
        assertTrue(markdown.contains("第二段。"));
        assertFalse(markdown.contains("<p>"), "导出正文不应残留 HTML 标签");
    }

    @Test
    void labelTextCoversAllLevels() {
        assertEquals("未使用 AI 生成内容", AiDisclosureService.labelText("none"));
        assertEquals("未使用 AI 生成内容", AiDisclosureService.labelText(null));
        assertTrue(AiDisclosureService.labelText("assisted").contains("AI 辅助"));
        assertTrue(AiDisclosureService.labelText("generated").contains("AI 生成"));
    }

    private Article baseArticle() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("标题");
        article.setSummary("摘要");
        article.setContentHtml("<p>正文内容。</p>");
        article.setAiUsageLevel("assisted");
        return article;
    }

    private AiOutput aiOutput(String traceId) {
        AiOutput output = new AiOutput();
        output.setTraceId(traceId);
        output.setAdopted(1);
        return output;
    }

    private List<String> readTraceIds(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

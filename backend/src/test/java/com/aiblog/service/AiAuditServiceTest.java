package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.entity.AiOutput;
import com.aiblog.mapper.AiOutputMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AiAuditService 纯逻辑单测：输入摘要、输出全文、审核状态与文章关联。
 */
class AiAuditServiceTest {

    private final AiOutputMapper mapper = mock(AiOutputMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAuditService service = new AiAuditService(mapper, objectMapper);

    @Test
    void beginWritesTraceDigestAndStreamingStatus() {
        when(mapper.insert(any(AiOutput.class))).thenAnswer(invocation -> {
            AiOutput output = invocation.getArgument(0);
            output.setId(7L);
            return 1;
        });
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("trace_id", "t1");
        request.put("topic", "春");

        Long id = service.begin("t1", 2L, 3L, "generate", "draft", request);

        assertEquals(7L, id);
        ArgumentCaptor<AiOutput> captor = ArgumentCaptor.forClass(AiOutput.class);
        verify(mapper).insert(captor.capture());
        AiOutput saved = captor.getValue();
        assertEquals("t1", saved.getTraceId());
        assertEquals(2L, saved.getUserId());
        assertEquals(3L, saved.getArticleId());
        assertEquals("generate", saved.getAction());
        assertEquals("draft", saved.getTask());
        assertEquals("streaming", saved.getStatus());
        assertEquals("pending", saved.getReviewStatus());
        assertEquals(0, saved.getOutputLength());
        assertEquals(sha256("{\"trace_id\":\"t1\",\"topic\":\"春\"}"), saved.getRequestDigest());
        assertEquals("{\"trace_id\":\"t1\",\"topic\":\"春\"}", saved.getRequestSnapshot());
    }

    @Test
    void beginFailureNeverBlocksBusiness() {
        when(mapper.insert(any(AiOutput.class))).thenThrow(new RuntimeException("db down"));

        assertNull(service.begin("t2", 1L, null, "review", null, Map.of("x", 1)));
    }

    @Test
    void finishWritesFullOutputAndPreview() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longText.append("字");
        }

        service.finish(9L, AiAuditService.AiResult.success(
                longText.toString(), "python", 11, 22, 1500L, "glm-4", "p3"));

        ArgumentCaptor<AiOutput> captor = ArgumentCaptor.forClass(AiOutput.class);
        verify(mapper).updateById(captor.capture());
        AiOutput patch = captor.getValue();
        assertEquals(9L, patch.getId());
        assertEquals(longText.toString(), patch.getOutput());
        assertEquals(400, patch.getOutputLength());
        assertEquals(300, patch.getOutputPreview().length());
        assertEquals("success", patch.getStatus());
        assertEquals("python", patch.getSource());
        assertEquals(11, patch.getTokensIn());
        assertEquals(22, patch.getTokensOut());
        assertEquals(1500, patch.getDurationMs());
        assertEquals("glm-4", patch.getModel());
        assertEquals("p3", patch.getPromptVersion());
        assertNotNull(patch.getFinishedAt());
    }

    @Test
    void finishWithoutIdIsNoop() {
        service.finish(null, AiAuditService.AiResult.success("x", "python", 0, 0, 1L, null, null));
        verifyNoInteractions(mapper);
    }

    @Test
    void reviewRejectsUnknownStatus() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.review(1L, 9L, "done", null));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    void reviewWritesStatusAndNote() {
        AiOutput existing = new AiOutput();
        existing.setId(5L);
        when(mapper.selectById(5L)).thenReturn(existing);

        service.review(5L, 9L, AiAuditService.REVIEW_FLAGGED, "内容与主题无关");

        ArgumentCaptor<AiOutput> captor = ArgumentCaptor.forClass(AiOutput.class);
        verify(mapper).updateById(captor.capture());
        AiOutput patch = captor.getValue();
        assertEquals("flagged", patch.getReviewStatus());
        assertEquals("内容与主题无关", patch.getReviewNote());
        assertEquals(9L, patch.getReviewedBy());
        assertNotNull(patch.getReviewedAt());
    }

    @Test
    void attachArticleBindsLatestUnboundRecord() {
        AiOutput latest = new AiOutput();
        latest.setId(12L);
        when(mapper.selectOne(any())).thenReturn(latest);

        service.attachArticle(3L, "care", 88L);

        ArgumentCaptor<AiOutput> captor = ArgumentCaptor.forClass(AiOutput.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(12L, captor.getValue().getId());
        assertEquals(88L, captor.getValue().getArticleId());
    }

    @Test
    void markLatestAdoptedSkipsAlreadyAdopted() {
        AiOutput latest = new AiOutput();
        latest.setId(15L);
        latest.setAdopted(1);
        when(mapper.selectOne(any())).thenReturn(latest);

        service.markLatestAdopted(3L, "review");

        verify(mapper, never()).updateById(any(AiOutput.class));
    }

    @Test
    void markLatestAdoptedUpdatesPendingRecord() {
        AiOutput latest = new AiOutput();
        latest.setId(16L);
        latest.setAdopted(0);
        when(mapper.selectOne(any())).thenReturn(latest);

        service.markLatestAdopted(3L, "review");

        ArgumentCaptor<AiOutput> captor = ArgumentCaptor.forClass(AiOutput.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getAdopted());
    }

    @Test
    void pendingCountQueriesPending() {
        when(mapper.selectCount(any())).thenReturn(4L);
        assertEquals(4L, service.pendingCount());
        assertTrue(true);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

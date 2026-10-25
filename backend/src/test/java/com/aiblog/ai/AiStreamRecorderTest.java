package com.aiblog.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式采集器纯逻辑单测：delta 累积、done 覆盖、error 失败、无终态取消。
 */
class AiStreamRecorderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void accumulatesDeltasWhenStreamInterrupted() {
        AiStreamRecorder recorder = new AiStreamRecorder(objectMapper);
        recorder.accept("delta", "{\"content\":\"春\"}");
        recorder.accept("delta", "{\"content\":\"天\"}");

        assertEquals("春天", recorder.content());
        assertFalse(recorder.done());
        assertEquals(AiStreamRecorder.STATUS_CANCELED, recorder.status());
        assertNull(recorder.errorCode());
    }

    @Test
    void doneFrameCarriesAuthoritativeContentAndMetadata() {
        AiStreamRecorder recorder = new AiStreamRecorder(objectMapper);
        recorder.accept("delta", "{\"content\":\"草稿\"}");
        recorder.accept("done", "{\"content\":\"终稿\",\"usage\":{\"prompt_tokens\":12,"
                + "\"completion_tokens\":34},\"model\":\"glm-4\",\"prompt_version\":\"p3\"}");

        assertTrue(recorder.done());
        assertEquals("终稿", recorder.content());
        assertEquals(AiStreamRecorder.STATUS_SUCCESS, recorder.status());
        assertEquals(12, recorder.tokensIn());
        assertEquals(34, recorder.tokensOut());
        assertEquals("glm-4", recorder.model());
        assertEquals("p3", recorder.promptVersion());
    }

    @Test
    void errorFrameMarksFailed() {
        AiStreamRecorder recorder = new AiStreamRecorder(objectMapper);
        recorder.accept("delta", "{\"content\":\"半句\"}");
        recorder.accept("error", "{\"code\":\"LLM_TIMEOUT\",\"message\":\"生成超时\"}");

        assertEquals(AiStreamRecorder.STATUS_FAILED, recorder.status());
        assertEquals("LLM_TIMEOUT", recorder.errorCode());
        assertEquals("生成超时", recorder.errorMessage());
        // 失败前的增量仍保留，便于审核定位问题
        assertEquals("半句", recorder.content());
    }

    @Test
    void malformedFramesAreIgnored() {
        AiStreamRecorder recorder = new AiStreamRecorder(objectMapper);
        recorder.accept("delta", "not-json");
        recorder.accept("delta", "");
        recorder.accept("ping", "{\"content\":\"忽略\"}");

        assertEquals("", recorder.content());
        assertEquals(AiStreamRecorder.STATUS_CANCELED, recorder.status());
    }

    @Test
    void doneWithoutContentFallsBackToDeltas() {
        AiStreamRecorder recorder = new AiStreamRecorder(objectMapper);
        recorder.accept("delta", "{\"content\":\"已有内容\"}");
        recorder.accept("done", "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}");

        assertEquals("已有内容", recorder.content());
        assertEquals(AiStreamRecorder.STATUS_SUCCESS, recorder.status());
    }
}

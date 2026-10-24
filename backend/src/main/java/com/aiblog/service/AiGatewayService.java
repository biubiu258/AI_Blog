package com.aiblog.service;

import com.aiblog.ai.PythonErrorTranslator;
import com.aiblog.ai.PythonResponseException;
import com.aiblog.ai.SseParser;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.dto.ai.AiGenerateRequest;
import com.aiblog.entity.UserPreference;
import com.aiblog.mapper.UserPreferenceMapper;
import com.aiblog.security.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 网关：鉴权后把生成请求转发 Python（/v1/generate），逐帧透传 SSE，
 * 并把 Python 错误统一转译为 Java 业务码（契约 5.4 / 5.7）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGatewayService {

    /** 契约 5.4.2：task 取值范围固定 */
    private static final Set<String> TASKS = Set.of(
            "outline", "draft", "continue", "expand", "condense", "rewrite", "polish", "prose",
            "suggest");

    private static final int MAX_TOPIC = 200;
    private static final int MAX_INSTRUCTION = 2000;
    private static final int MAX_OUTLINE = 50000;
    private static final int MAX_ORIGINAL_TEXT = 100000;
    /** 散文任务：情绪描述上限 */
    private static final int MAX_EMOTION = 200;
    /** 散文任务：近况描述上限 */
    private static final int MAX_RECENT_STATE = 1000;
    /** 散文任务：图片张数上限 */
    private static final int MAX_IMAGES = 5;
    /** 划词建议：选中片段上限 */
    private static final int MAX_SELECTED_TEXT = 2000;
    /** 划词建议：前后文各 300 字 */
    private static final int MAX_TEXT_CONTEXT = 300;
    private static final Set<String> SUGGESTION_MODES = Set.of(
            "advice", "polish", "rewrite", "expand", "condense");
    /** 只放行站内 /uploads/ 下的图片，避免用户构造地址让 Python 去请求任意主机 */
    private static final java.util.regex.Pattern UPLOAD_PATH =
            java.util.regex.Pattern.compile("^/uploads/[A-Za-z0-9/_.-]+$");

    private final WebClient aiWebClient;
    private final AiProperties properties;
    private final UserPreferenceMapper preferenceMapper;
    private final PythonErrorTranslator errorTranslator;

    public SseEmitter generate(LoginUser user, AiGenerateRequest request) {
        validateForTask(request);
        Map<String, Object> payload = buildPayload(user, request);

        // 等待 Python 返回响应头（body 为流，头到达即完成，不等整流）；
        // 流建立前失败返回普通 JSON（2001/2002），一旦 2xx 把响应体作为流转发给 SseEmitter。
        // 注意：不能用 exchangeToMono 保留 body Flux 稍后消费——连接会被提前释放，
        // 必须用 toEntityFlux 让 WebClient 正确管理流式响应的连接。
        ResponseEntity<Flux<DataBuffer>> upstream;
        try {
            upstream = aiWebClient.post()
                    .uri("/v1/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(
                                            new PythonResponseException(response.statusCode(), body))))
                    .toEntityFlux(DataBuffer.class)
                    .block(java.time.Duration.ofSeconds(properties.getPreStreamWaitSeconds()));
        } catch (PythonResponseException e) {
            throw errorTranslator.translate(e, "生成超时，请重试");
        } catch (IllegalStateException e) {
            // block() 超时抛 IllegalStateException
            log.warn("等待 Python 建立流超时: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务响应超时，请稍后重试");
        } catch (Exception e) {
            log.warn("连接 Python AI 服务失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
        if (upstream == null || upstream.getBody() == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务无响应，请稍后重试");
        }

        return forwardStream(upstream.getBody());
    }

    /**
     * 把 Python 的 SSE 字节流解析成事件帧，逐帧写入 SseEmitter 并 flush。
     * 客户端断开（onTimeout/onError/onCompletion）时取消上游连接，避免泄漏。
     */
    private SseEmitter forwardStream(Flux<DataBuffer> body) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        // 解析器持有跨分块状态，必须每个请求独立实例
        SseParser parser = new SseParser();
        // 解码器同样跨分块：DataBuffer 边界可能切在中文等多字节字符中间，
        // 逐块 new String(bytes, UTF_8) 会产出替换字符 �
        Utf8StreamDecoder decoder = new Utf8StreamDecoder();

        Flux<SseParser.SseEvent> events = body
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return decoder.decode(bytes);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .concatMap(chunk -> Flux.fromIterable(parser.feed(chunk)))
                .concatWith(Flux.defer(() -> Flux.fromIterable(parser.flush())))
                // done/error 之后连接即将关闭；此处不改写任何内容，只做帧转发
                .takeUntil(e -> "done".equals(e.event()) || "error".equals(e.event()));

        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        Runnable cleanup = () -> {
            Disposable d = disposableRef.get();
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
        };
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        emitter.onCompletion(cleanup);

        Disposable disposable = events.subscribe(
                event -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(event.event())
                                .data(event.data(), MediaType.TEXT_PLAIN));
                    } catch (IOException | IllegalStateException e) {
                        // 客户端已断开：取消上游到 Python 的连接
                        log.debug("SSE 客户端断开，取消上游连接: {}", e.getMessage());
                        cleanup.run();
                    }
                },
                err -> {
                    log.warn("AI 流式转发异常: {}", err.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data("{\"code\":\"LLM_ERROR\",\"message\":\"AI 服务流中断，请重试\"}",
                                        MediaType.TEXT_PLAIN));
                        emitter.complete();
                    } catch (Exception ignored) {
                        cleanup.run();
                    }
                },
                emitter::complete);
        disposableRef.set(disposable);
        return emitter;
    }

    private Map<String, Object> buildPayload(LoginUser user, AiGenerateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", user.getId());
        payload.put("task", request.getTask());
        payload.put("params", request.paramsAsMap());
        payload.put("context", buildContext(request));

        Map<String, Object> userContext = new LinkedHashMap<>();
        userContext.put("preferences", loadPreferences(user.getId()));
        userContext.put("history_style", List.of());
        payload.put("user_context", userContext);
        return payload;
    }

    /**
     * 散文任务图片以 URL 形式传给 Python：把站内 /uploads/** 相对路径
     * 拼成 publicBaseUrl 下的绝对 URL，Python 可直接下载（该路径 permitAll）。
     */
    private Map<String, Object> buildContext(AiGenerateRequest request) {
        Map<String, Object> context = new LinkedHashMap<>(request.contextAsMap());
        List<String> images = request.getContext() == null ? null : request.getContext().getImageUrls();
        if (images != null && !images.isEmpty()) {
            context.put("image_urls", images.stream().map(this::toAbsoluteUrl).toList());
        }
        return context;
    }

    private String toAbsoluteUrl(String url) {
        if (url == null || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = properties.getPublicBaseUrl();
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + (url.startsWith("/") ? url : "/" + url);
    }

    /**
     * 写作偏好（P1 可选上下文）：来自 user_preference 表，未配置传 null，不阻塞生成。
     */
    private Map<String, Object> loadPreferences(Long userId) {
        UserPreference pref = preferenceMapper.selectOne(
                Wrappers.<UserPreference>lambdaQuery().eq(UserPreference::getUserId, userId));
        if (pref == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        if (pref.getTone() != null) map.put("tone", pref.getTone());
        if (pref.getTargetReader() != null) map.put("target_reader", pref.getTargetReader());
        if (pref.getStructure() != null) map.put("structure", pref.getStructure());
        if (pref.getExtra() != null) map.put("extra", pref.getExtra());
        return map.isEmpty() ? null : map;
    }

    /**
     * 任务级校验（契约 5.4.2），发生在流式开始之前。
     */
    private void validateForTask(AiGenerateRequest request) {
        String task = request.getTask();
        if (!TASKS.contains(task)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的任务类型: " + task);
        }
        AiGenerateRequest.Params params = request.getParams();
        AiGenerateRequest.Context context = request.getContext();
        String topic = params == null ? null : params.getTopic();
        String outline = context == null ? null : context.getOutline();
        String originalText = context == null ? null : context.getOriginalText();
        String instruction = context == null ? null : context.getInstruction();

        checkLength("topic", topic, MAX_TOPIC);
        checkLength("instruction", instruction, MAX_INSTRUCTION);
        checkLength("outline", outline, MAX_OUTLINE);
        checkLength("original_text", originalText, MAX_ORIGINAL_TEXT);

        if ("prose".equals(task)) {
            validateProse(params, context);
            return;
        }
        if ("suggest".equals(task)) {
            validateSuggest(params, context);
            return;
        }
        switch (task) {
            case "outline" -> require(hasText(topic), "生成大纲需要填写主题（topic）");
            case "draft" -> require(hasText(outline), "生成正文需要提供大纲（outline）");
            default -> require(hasText(originalText), "该任务需要提供原文（original_text）");
        }
    }

    /**
     * 散文任务校验：情绪或近况至少其一（图片可选）；有图片时校验数量与地址合法性。
     * 图片只允许站内 /uploads/ 相对路径或指向本站的绝对地址，避免 SSRF。
     */
    private void validateProse(AiGenerateRequest.Params params, AiGenerateRequest.Context context) {
        String emotion = params == null ? null : params.getEmotion();
        String recentState = params == null ? null : params.getRecentState();
        checkLength("emotion", emotion, MAX_EMOTION);
        checkLength("recent_state", recentState, MAX_RECENT_STATE);

        List<String> images = context == null ? null : context.getImageUrls();
        if (images != null && !images.isEmpty()) {
            require(images.size() <= MAX_IMAGES, "图片数量不能超过 " + MAX_IMAGES + " 张");
            for (String url : images) {
                require(isAllowedImageUrl(url), "图片地址不合法，仅支持站内上传的图片");
            }
        }
        require(hasText(emotion) || hasText(recentState),
                "请至少填写情绪或近况，AI 才能贴合你的心境");
    }

    /** 划词建议：必须带选中片段，模式可选但必须在枚举内。 */
    private void validateSuggest(AiGenerateRequest.Params params, AiGenerateRequest.Context context) {
        String selectedText = context == null ? null : context.getSelectedText();
        String textBefore = context == null ? null : context.getTextBefore();
        String textAfter = context == null ? null : context.getTextAfter();
        checkLength("selected_text", selectedText, MAX_SELECTED_TEXT);
        checkLength("text_before", textBefore, MAX_TEXT_CONTEXT);
        checkLength("text_after", textAfter, MAX_TEXT_CONTEXT);

        require(hasText(selectedText), "请先选中要问 AI 的片段");
        String mode = params == null ? null : params.getSuggestionMode();
        require(mode == null || SUGGESTION_MODES.contains(mode),
                "不支持的划词建议模式: " + mode);
    }

    private boolean isAllowedImageUrl(String url) {
        if (url == null) return false;
        if (UPLOAD_PATH.matcher(url).matches()) return true;
        String base = properties.getPublicBaseUrl();
        if (base != null && !base.isBlank()) {
            String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            if (url.startsWith(normalized + "/uploads/")) return true;
        }
        return false;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private void checkLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, field + " 超过长度上限 " + max);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
    }

    /**
     * 跨分块的 UTF-8 增量解码器。
     * WebClient 的 DataBuffer 边界与字符边界无关，可能把一个中文字符的 3 个字节
     * 切到两块里；逐块独立解码会把残缺字节变成 U+FFFD（前端显示为 �）。
     * 这里把上一块未消费的尾部字节留到下一块拼接后再解码。
     */
    private static final class Utf8StreamDecoder {

        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        private ByteBuffer carry = ByteBuffer.allocate(0);

        String decode(byte[] bytes) {
            ByteBuffer input = ByteBuffer.allocate(carry.remaining() + bytes.length);
            input.put(carry).put(bytes).flip();
            // UTF-8 每字节最多解出 1 个 char，容量取字节数即可
            CharBuffer output = CharBuffer.allocate(input.remaining() + 1);
            decoder.decode(input, output, false);
            output.flip();
            // decode 遇到不完整的多字节序列时会把这些字节留在 input 中
            carry = ByteBuffer.allocate(input.remaining());
            carry.put(input).flip();
            return output.toString();
        }
    }
}

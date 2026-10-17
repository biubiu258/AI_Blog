package com.aiblog.ai;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * Python 返回非 2xx 时的原始响应（错误体 JSON：{"error":{code,message}}），
 * 由 AiGatewayService 统一转译为 Java 业务码，不向客户端泄露细节。
 */
@Getter
public class PythonResponseException extends RuntimeException {

    private final HttpStatusCode status;
    private final String body;

    public PythonResponseException(HttpStatusCode status, String body) {
        super("python responded " + status.value());
        this.status = status;
        this.body = body;
    }
}

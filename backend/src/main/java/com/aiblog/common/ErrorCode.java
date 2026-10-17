package com.aiblog.common;

import lombok.Getter;

/**
 * 业务错误码，映射关系见 docs/05-接口契约.md 5.7.2
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(1001, 400, "参数错误"),
    UNAUTHORIZED(1002, 401, "未登录或令牌失效"),
    FORBIDDEN(1003, 403, "无权限"),
    NOT_FOUND(1004, 404, "资源不存在"),
    CONFLICT(1005, 409, "资源冲突"),
    AI_TIMEOUT(2001, 504, "AI 生成超时"),
    AI_SERVICE_ERROR(2002, 502, "AI 服务异常"),
    AI_EMPTY_OUTPUT(2003, 422, "生成结果为空"),
    INTERNAL_ERROR(500, 500, "系统繁忙，请稍后重试");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}

package com.blog.common;

/**
 * 统一业务错误码枚举：REST API 所有错误响应的 code 来源。
 * <p>0 表示成功；业务失败码按语义分段，避免散落魔术数字。</p>
 */
public enum ResultCode {

    /** 成功 */
    OK(0, "成功"),

    /** 通用业务失败 */
    BIZ_ERROR(1000, "业务处理失败"),

    /** 参数不合法 */
    BAD_REQUEST(1001, "参数不合法"),

    /** 未登录或令牌无效 */
    UNAUTHORIZED(1002, "未登录或登录已过期"),

    /** 已登录但权限不足 */
    FORBIDDEN(1003, "权限不足"),

    /** 触发限流 */
    TOO_MANY_REQUESTS(1004, "操作太频繁，请稍后再试"),

    /** 系统内部错误 */
    INTERNAL_ERROR(5000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}

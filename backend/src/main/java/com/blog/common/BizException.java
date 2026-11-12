package com.blog.common;

/**
 * 业务异常：REST API 与页面控制器的统一业务失败载体。
 * <p>全局处理器会把本异常转换为对应的 {@link Result} 或错误页；禁止直接抛 RuntimeException。</p>
 */
public class BizException extends RuntimeException {

    private final int code;

    /**
     * 以默认业务错误码构建异常。
     *
     * @param message 用户可读的错误提示
     */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.code();
    }

    /**
     * 以指定错误码构建异常（如限流、未登录等语义码）。
     *
     * @param code    业务码，取值见 {@link ResultCode}
     * @param message 用户可读的错误提示
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 返回业务码。
     *
     * @return 业务码，0 之外的值代表失败语义
     */
    public int code() {
        return code;
    }
}

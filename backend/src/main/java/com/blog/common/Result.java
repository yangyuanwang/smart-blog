package com.blog.common;

/**
 * REST API 统一响应包装：所有 /api/v1 接口必须返回本类型，禁止裸返回基础类型。
 *
 * @param code    业务码，0 表示成功，其余取值见 {@link ResultCode}
 * @param message 提示信息，可直接展示给用户
 * @param data    业务数据载荷，可为 null
 * @param <T>     数据载荷类型
 */
public record Result<T>(int code, String message, T data) {

    /**
     * 构建成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据载荷类型
     * @return code=0 的成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<T>(ResultCode.OK.code(), ResultCode.OK.message(), data);
    }

    /**
     * 构建无数据载荷的成功响应。
     *
     * @param <T> 数据载荷类型
     * @return code=0、data=null 的成功响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 以指定错误码构建失败响应。
     *
     * @param code    业务码
     * @param message 提示信息
     * @param <T>     数据载荷类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }

    /**
     * 以业务异常构建失败响应（沿用异常自带 code 与提示）。
     *
     * @param ex 业务异常
     * @param <T> 数据载荷类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(BizException ex) {
        return new Result<T>(ex.code(), ex.getMessage(), null);
    }
}

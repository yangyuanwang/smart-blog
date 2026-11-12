package com.blog.common;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理：/api/v1 路径输出统一 Result JSON（RestControllerAdvice 语义），其余路径沿用错误页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：API 返回业务码 JSON，页面渲染错误提示。
     *
     * @param ex      业务异常
     * @param request 当前请求
     * @return API 场景返回 Result 对象，页面场景返回 ModelAndView
     */
    @ExceptionHandler(BizException.class)
    public Object biz(BizException ex, HttpServletRequest request) {
        if (isApi(request)) {
            return Result.fail(ex);
        }
        ModelAndView view = new ModelAndView("error");
        view.addObject("message", ex.getMessage());
        return view;
    }

    /**
     * 参数校验异常：API 返回首个字段错误提示。
     *
     * @param ex      校验异常
     * @param request 当前请求
     * @return API 场景返回 Result，页面场景返回错误页
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object invalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ResultCode.BAD_REQUEST.message();
        if (ex.getBindingResult().getFieldError() != null) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (isApi(request)) {
            return Result.fail(ResultCode.BAD_REQUEST.code(), message);
        }
        ModelAndView view = new ModelAndView("error");
        view.addObject("message", message);
        return view;
    }

    /**
     * 兜底异常：API 返回 5000 内部错误 JSON，页面交给默认错误页。
     *
     * @param ex      未知异常
     * @param request 当前请求
     * @return API 场景返回 Result
     */
    @ExceptionHandler(Exception.class)
    public Object unexpected(Exception ex, HttpServletRequest request) {
        if (isApi(request)) {
            return Result.fail(ResultCode.INTERNAL_ERROR.code(), ResultCode.INTERNAL_ERROR.message());
        }
        ModelAndView view = new ModelAndView("error");
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("message", "系统繁忙，请稍后再试");
        view.addAllObjects(model);
        return view;
    }

    /**
     * 判断是否 REST API 请求。
     *
     * @param request 当前请求
     * @return /api/v1 前缀返回 true
     */
    private boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/");
    }
}

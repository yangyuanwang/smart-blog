package com.blog.common;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ModelAndView biz(BizException ex) {
        ModelAndView view = new ModelAndView("error");
        view.addObject("message", ex.getMessage());
        return view;
    }
}

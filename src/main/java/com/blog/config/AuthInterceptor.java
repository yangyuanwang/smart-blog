package com.blog.config;

import com.blog.entity.User;
import com.blog.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        User user = (User) request.getSession().getAttribute(UserService.LOGIN_USER);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/admin/login");
            return false;
        }
        if (!UserService.isAdmin(user)) {
            response.sendRedirect(request.getContextPath() + "/admin/login?denied=1");
            return false;
        }
        return true;
    }
}

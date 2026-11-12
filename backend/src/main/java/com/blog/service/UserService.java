package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.Texts;
import com.blog.entity.LoginLog;
import com.blog.entity.User;
import com.blog.mapper.LoginLogMapper;
import com.blog.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    public static final String LOGIN_USER = "loginUser";

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;

    public UserService(UserMapper userMapper, LoginLogMapper loginLogMapper) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
    }

    public static boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        return "0".equals(user.getRole()) || "admin".equalsIgnoreCase(user.getUsername());
    }

    public User login(String username, String password, String code, String rightCode, String ip, boolean adminOnly) {
        String name = Texts.trim(username);
        if (rightCode == null || Texts.blank(code) || !rightCode.equalsIgnoreCase(code.trim())) {
            writeLog(null, name, ip, false, "验证码错误");
            throw new BizException("验证码错误");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", name));
        String encoded = Texts.md5(password);
        if (user == null || user.getPassword() == null || !user.getPassword().equalsIgnoreCase(encoded)) {
            writeLog(user == null ? null : user.getUid(), name, ip, false, "账号或密码错误");
            throw new BizException("账号或密码错误");
        }
        if ("0".equals(user.getState())) {
            writeLog(user.getUid(), name, ip, false, "账号已锁定");
            throw new BizException("账号已锁定");
        }
        if (adminOnly && !isAdmin(user)) {
            writeLog(user.getUid(), name, ip, false, "没有后台权限");
            throw new BizException("没有后台权限");
        }
        int count = user.getLoginCount() == null ? 1 : user.getLoginCount() + 1;
        String now = Texts.now();
        String loginIp = Texts.ip(ip);
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("uid", user.getUid())
                .set("lastLoginTime", now)
                .set("loginIp", loginIp)
                .set("loginCount", count));
        user.setLastLoginTime(now);
        user.setLoginIp(loginIp);
        user.setLoginCount(count);
        writeLog(user.getUid(), name, ip, true, "登录成功");
        return user;
    }

    public void register(String username, String password, String nickname, String phone) {
        String name = Texts.trim(username);
        if (name.length() < 2 || name.length() > 32) {
            throw new BizException("用户名需要 2 到 32 个字符");
        }
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new BizException("密码需要 6 到 32 个字符");
        }
        Long exists = userMapper.selectCount(new QueryWrapper<User>().eq("username", name));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        String mobile = Texts.trim(phone);
        if (!mobile.isEmpty() && !mobile.matches("\\d{11}")) {
            throw new BizException("手机号需要 11 位数字");
        }
        User user = new User();
        user.setUsername(name);
        user.setNickname(Texts.blank(nickname) ? name : Texts.cut(nickname, 32));
        user.setPassword(Texts.md5(password));
        user.setRole("1");
        user.setState("1");
        user.setCreateTime(Texts.today());
        user.setPhone(mobile.isEmpty() ? null : mobile);
        user.setLoginCount(0);
        userMapper.insert(user);
    }

    public void resetPassword(String username, String phone, String password) {
        if (password == null || password.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", Texts.trim(username)));
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (Texts.blank(user.getPhone()) || !user.getPhone().equals(Texts.trim(phone))) {
            throw new BizException("用户名和手机号不匹配");
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("uid", user.getUid())
                .set("password", Texts.md5(password)));
    }

    public void updateProfile(User current, String nickname, String phone, String img) {
        String mobile = Texts.trim(phone);
        if (!mobile.isEmpty() && !mobile.matches("\\d{11}")) {
            throw new BizException("手机号需要 11 位数字");
        }
        UpdateWrapper<User> wrapper = new UpdateWrapper<User>()
                .eq("uid", current.getUid())
                .set("nickname", Texts.cut(nickname, 32))
                .set("phone", mobile.isEmpty() ? null : mobile);
        if (!Texts.blank(img)) {
            wrapper.set("img", img);
        }
        userMapper.update(null, wrapper);
        current.setNickname(Texts.cut(nickname, 32));
        current.setPhone(mobile.isEmpty() ? null : mobile);
        if (!Texts.blank(img)) {
            current.setImg(img);
        }
    }

    public void changePassword(User current, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        if (current.getPassword() == null || !current.getPassword().equalsIgnoreCase(Texts.md5(oldPassword))) {
            throw new BizException("原密码不正确");
        }
        String encoded = Texts.md5(newPassword);
        userMapper.update(null, new UpdateWrapper<User>().eq("uid", current.getUid()).set("password", encoded));
        current.setPassword(encoded);
    }

    public Page<User> page(long page, long size, String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<User>().orderByAsc("uid");
        if (!Texts.blank(keyword)) {
            wrapper.and(w -> w.like("username", keyword.trim()).or().like("nickname", keyword.trim()));
        }
        return userMapper.selectPage(new Page<User>(page, size), wrapper);
    }

    public void changeState(Integer uid, String state, Integer selfId) {
        if (uid.equals(selfId)) {
            throw new BizException("不能锁定当前登录账号");
        }
        userMapper.update(null, new UpdateWrapper<User>().eq("uid", uid).set("state", state));
    }

    public void changeRole(Integer uid, String role, Integer selfId) {
        if (uid.equals(selfId)) {
            throw new BizException("不能修改当前登录账号的角色");
        }
        User user = userMapper.selectById(uid);
        if (user != null && "admin".equalsIgnoreCase(user.getUsername())) {
            throw new BizException("不能修改 admin 的角色");
        }
        userMapper.update(null, new UpdateWrapper<User>().eq("uid", uid).set("role", role));
    }

    public List<User> all() {
        return userMapper.selectList(new QueryWrapper<User>().orderByAsc("uid"));
    }

    public void repairPlainPasswords() {
        userMapper.encodePlainPasswords(Texts.md5("123"));
    }

    public Page<LoginLog> logs(long page, long size) {
        return loginLogMapper.selectPage(new Page<LoginLog>(page, size),
                new QueryWrapper<LoginLog>().orderByDesc("id"));
    }

    private void writeLog(Integer uid, String username, String ip, boolean success, String message) {
        LoginLog log = new LoginLog();
        log.setUid(uid);
        log.setUsername(Texts.cut(username, 32));
        log.setIp(Texts.cut(ip, 64));
        log.setSuccess(success ? "1" : "0");
        log.setMessage(Texts.cut(message, 100));
        log.setLoginTime(Texts.now());
        loginLogMapper.insert(log);
    }
}

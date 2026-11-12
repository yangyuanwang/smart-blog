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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务：资料、密码（BCrypt 与历史 MD5 登录期升级）、用户管理、登录日志。
 */
@Service
public class UserService {

    /** Session 中登录用户的键名（旧 Thymeleaf 链路沿用） */
    public static final String LOGIN_USER = "loginUser";

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造用户服务。
     *
     * @param userMapper      用户 Mapper
     * @param loginLogMapper  登录日志 Mapper
     * @param passwordEncoder BCrypt 编码器
     */
    public UserService(UserMapper userMapper, LoginLogMapper loginLogMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 是否管理员：仅依据 role 字段判定，不存在用户名硬编码特权。
     *
     * @param user 用户实体
     * @return 管理员返回 true
     */
    public static boolean isAdmin(User user) {
        return user != null && "0".equals(user.getRole());
    }

    /**
     * 旧页面链路登录：验证码由控制器校验，本方法只做口令匹配（含 MD5→BCrypt 自动升级）与状态检查。
     *
     * @param username  用户名
     * @param password  明文密码
     * @param ip        客户端 IP
     * @param adminOnly 是否要求管理员角色
     * @return 登录成功的用户实体
     */
    public User login(String username, String password, String ip, boolean adminOnly) {
        String name = Texts.trim(username);
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", name));
        if (user == null || !matchAndUpgrade(user, password)) {
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
        String loginIp = Texts.ip(ip);
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("uid", user.getUid())
                .set("lastLoginTime", Texts.now())
                .set("loginIp", loginIp)
                .set("loginCount", count));
        user.setLastLoginTime(Texts.now());
        user.setLoginIp(loginIp);
        user.setLoginCount(count);
        writeLog(user.getUid(), name, ip, true, "登录成功");
        return user;
    }

    /**
     * 注册访客账号（密码 BCrypt 存储）。
     *
     * @param username 用户名
     * @param password 明文密码
     * @param nickname 昵称（可空）
     * @param email    邮箱（可空）
     */
    public void register(String username, String password, String nickname, String email) {
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
        User user = new User();
        user.setUsername(name);
        user.setNickname(Texts.blank(nickname) ? name : Texts.cut(nickname, 32));
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("1");
        user.setState("1");
        user.setCreateTime(Texts.today());
        user.setEmail(Texts.blank(email) ? null : Texts.cut(email, 128));
        user.setLoginCount(0);
        userMapper.insert(user);
    }

    /**
     * 旧「用户名+手机号」找回方式已停用（高危：知道手机号即可接管账号）。
     *
     * @param username 用户名
     * @param phone    手机号
     * @param password 新密码
     * @throws BizException 一律抛出停用提示
     */
    public void resetPassword(String username, String phone, String password) {
        throw new BizException("该找回方式已停用，请使用邮箱验证码找回，或联系管理员重置");
    }

    /**
     * 管理员重置用户密码（BCrypt 存储，并吊销该用户全部 refresh 会话由控制器完成）。
     *
     * @param uid        用户 ID
     * @param newPassword 新密码
     */
    public void resetPasswordByAdmin(Integer uid, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("uid", uid)
                .set("password", passwordEncoder.encode(newPassword)));
    }

    /**
     * 修改资料（昵称/手机号/头像）。
     *
     * @param current  当前用户
     * @param nickname 昵称
     * @param phone    手机号（可空）
     * @param img      头像地址（可空）
     */
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

    /**
     * 修改密码：原密码按 BCrypt 或历史 MD5 校验，新密码 BCrypt 落库。
     *
     * @param current     当前用户
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    public void changePassword(User current, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        if (!matchPlain(current, oldPassword)) {
            throw new BizException("原密码不正确");
        }
        String encoded = passwordEncoder.encode(newPassword);
        userMapper.update(null, new UpdateWrapper<User>().eq("uid", current.getUid()).set("password", encoded));
        current.setPassword(encoded);
    }

    /**
     * 用户分页（用户名/昵称搜索）。
     *
     * @param page    页码
     * @param size    条数
     * @param keyword 关键词
     * @return 分页用户
     */
    public Page<User> page(long page, long size, String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<User>().orderByAsc("uid");
        if (!Texts.blank(keyword)) {
            wrapper.and(w -> w.like("username", keyword.trim()).or().like("nickname", keyword.trim()));
        }
        return userMapper.selectPage(new Page<User>(page, size), wrapper);
    }

    /**
     * 锁定/解锁用户（不能操作自己）。
     *
     * @param uid     目标用户 ID
     * @param state   0 锁定 1 正常
     * @param selfId  当前管理员 ID
     */
    public void changeState(Integer uid, String state, Integer selfId) {
        if (uid.equals(selfId)) {
            throw new BizException("不能锁定当前登录账号");
        }
        userMapper.update(null, new UpdateWrapper<User>().eq("uid", uid).set("state", state));
    }

    /**
     * 修改用户角色（不能操作自己）。
     *
     * @param uid    目标用户 ID
     * @param role   0 管理员 1 普通用户
     * @param selfId 当前管理员 ID
     */
    public void changeRole(Integer uid, String role, Integer selfId) {
        if (uid.equals(selfId)) {
            throw new BizException("不能修改当前登录账号的角色");
        }
        if (!"0".equals(role) && !"1".equals(role)) {
            throw new BizException("角色不正确");
        }
        userMapper.update(null, new UpdateWrapper<User>().eq("uid", uid).set("role", role));
    }

    /**
     * 全部用户（旧页面下拉用）。
     *
     * @return 用户列表（含密码，仅旧链路内部使用）
     */
    public List<User> all() {
        return userMapper.selectList(new QueryWrapper<User>().orderByAsc("uid"));
    }

    /**
     * 登录日志分页。
     *
     * @param page 页码
     * @param size 条数
     * @return 分页日志
     */
    public Page<LoginLog> logs(long page, long size) {
        return loginLogMapper.selectPage(new Page<LoginLog>(page, size),
                new QueryWrapper<LoginLog>().orderByDesc("id"));
    }

    /**
     * 口令匹配（BCrypt 优先，历史 MD5 命中即升级 BCrypt）。
     *
     * @param user     用户实体
     * @param password 明文口令
     * @return 匹配成功返回 true
     */
    private boolean matchAndUpgrade(User user, String password) {
        String stored = user.getPassword();
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(password == null ? "" : password, stored);
        }
        if (Texts.md5(password).equalsIgnoreCase(stored)) {
            String encoded = passwordEncoder.encode(password);
            userMapper.update(null, new UpdateWrapper<User>().eq("uid", user.getUid()).set("password", encoded));
            user.setPassword(encoded);
            return true;
        }
        return false;
    }

    /**
     * 仅校验口令（不升级），供改密入口使用。
     *
     * @param user     用户实体
     * @param password 明文口令
     * @return 匹配成功返回 true
     */
    private boolean matchPlain(User user, String password) {
        String stored = user.getPassword();
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(password == null ? "" : password, stored);
        }
        return Texts.md5(password).equalsIgnoreCase(stored);
    }

    /**
     * 写登录日志。
     *
     * @param uid      用户 ID
     * @param username 用户名
     * @param ip       客户端 IP
     * @param success  是否成功
     * @param message  说明
     */
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

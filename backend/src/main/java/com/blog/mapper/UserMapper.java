package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（历史明文修复逻辑已由登录期 BCrypt 升级替代）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

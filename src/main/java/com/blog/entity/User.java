package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_user")
public class User {
    @TableId(value = "uid", type = IdType.AUTO)
    private Integer uid;
    private String username;
    private String nickname;
    private String password;
    private String role;
    @TableField("createTime")
    private String createTime;
    @TableField("lastLoginTime")
    private String lastLoginTime;
    private String state;
    @TableField("loginIp")
    private String loginIp;
    private String phone;
    @TableField("loginCount")
    private Integer loginCount;
    private String img;
}

package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_login_log")
public class LoginLog {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer uid;
    private String username;
    private String ip;
    private String success;
    private String message;
    private String loginTime;
}

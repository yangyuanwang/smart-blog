package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@TableName("t_message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String nickname;
    private String content;
    private String reply;
    private String createTime;
    private String status;
    private Integer uid;
    private String ip;
    private Integer parentId;
    @TableField(exist = false)
    private List<Message> replies = new ArrayList<Message>();
    @TableField(exist = false)
    private String avatarColor;
    @TableField(exist = false)
    private String initial;
    @TableField(exist = false)
    private boolean canDelete;
}

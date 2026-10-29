package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_diary")
public class Diary {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String title;
    private String content;
    private String mood;
    private String createTime;
}

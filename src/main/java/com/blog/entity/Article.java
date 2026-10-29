package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("t_article")
public class Article {
    @TableId(value = "aid", type = IdType.AUTO)
    private Integer aid;
    private String title;
    private String digest;
    private String content;
    private Integer cid;
    private Integer visitCount;
    private String createTime;
    private String updateTime;
    private Integer isHot;
    private String logo;
    private Integer uid;
    @TableField("isOpen")
    private String isOpen;
    @TableField("thumbsUp")
    private Long thumbsUp;
    @TableField("tagNames")
    private String tagNames;
    @TableField("isCommented")
    private String isCommented;

    @TableField(exist = false)
    private String categoryName;
    @TableField(exist = false)
    private String authorName;
    @TableField(exist = false)
    private Integer readingMinutes;
    @TableField(exist = false)
    private List<String> keywords;
}

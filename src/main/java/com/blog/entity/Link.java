package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_link")
public class Link {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String siteName;
    private String siteUrl;
    private String siteDesc;
    private Integer sort;
}

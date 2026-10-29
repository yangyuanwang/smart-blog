package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_category")
public class Category {
    @TableId(value = "cid", type = IdType.AUTO)
    private Integer cid;
    private String cname;

    @TableField(exist = false)
    private Long articleCount;
}

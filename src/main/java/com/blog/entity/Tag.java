package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_tag")
public class Tag {
    @TableId(value = "tid", type = IdType.AUTO)
    private Integer tid;
    private String tname;
    private Integer cid;
}

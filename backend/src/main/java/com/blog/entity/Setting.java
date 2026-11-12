package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_setting")
public class Setting {
    @TableId(value = "skey", type = IdType.INPUT)
    private String skey;
    private String svalue;
}

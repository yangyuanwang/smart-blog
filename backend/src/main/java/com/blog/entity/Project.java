package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品集项目实体，对应 t_project。
 */
@Data
@TableName("t_project")
public class Project {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 项目名 */
    private String name;

    /** 一句话简介 */
    private String summary;

    /** 详细介绍（Markdown） */
    private String description;

    /** 技术栈，逗号分隔 */
    private String techStack;

    /** 封面图地址 */
    private String cover;

    /** 仓库地址 */
    private String repoUrl;

    /** 演示地址 */
    private String demoUrl;

    /** 1 精选（进首页展示位） */
    private String featured;

    /** 排序，小在前 */
    private Integer sort;

    /** 1 展示 0 隐藏 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

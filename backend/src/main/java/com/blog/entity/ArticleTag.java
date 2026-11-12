package com.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章-标签关联实体，对应 t_article_tag（复合主键 article_id + tag_id）。
 */
@Data
@TableName("t_article_tag")
public class ArticleTag {

    /** 文章 ID */
    private Integer articleId;

    /** 标签 ID */
    private Integer tagId;
}

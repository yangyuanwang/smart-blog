package com.blog.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Article;

/**
 * 文章 Mapper：聚合统计走 SQL，避免全表载入内存计算。
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 全站公开文章访问量合计。
     *
     * @return 访问量总和，无数据时为 0
     */
    @Select("SELECT IFNULL(SUM(visit_count),0) FROM t_article")
    Long sumVisits();

    /**
     * 公开文章按栏目计数（替代全表内存统计）。
     *
     * @return 每行含 cid 与 cnt 两个字段
     */
    @Select("SELECT cid, COUNT(*) AS cnt FROM t_article WHERE isOpen = '1' AND cid IS NOT NULL GROUP BY cid")
    List<Map<String, Object>> countByCategory();

    /**
     * 公开文章按标签 ID 计数（基于 V3 关联表，替代全表内存统计）。
     *
     * @return 每行含 tagId 与 cnt 两个字段
     */
    @Select("SELECT rt.tag_id AS tagId, COUNT(*) AS cnt FROM t_article_tag rt "
            + "JOIN t_article a ON a.aid = rt.article_id AND a.isOpen = '1' "
            + "GROUP BY rt.tag_id")
    List<Map<String, Object>> countByTag();
}

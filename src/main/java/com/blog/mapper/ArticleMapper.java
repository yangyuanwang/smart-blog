package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Article;
import org.apache.ibatis.annotations.Select;

public interface ArticleMapper extends BaseMapper<Article> {

    @Select("SELECT IFNULL(SUM(visit_count),0) FROM t_article")
    Long sumVisits();
}

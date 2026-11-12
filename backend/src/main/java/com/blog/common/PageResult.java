package com.blog.common;

import java.util.List;

/**
 * 分页响应包装：统一 REST API 的分页结构，字段与前端约定一致。
 *
 * @param list  当前页数据
 * @param total 总条数
 * @param page  当前页码，从 1 开始
 * @param size  每页条数
 * @param <T>   数据载荷类型
 */
public record PageResult<T>(List<T> list, long total, long page, long size) {

    /**
     * 由 MyBatis-Plus 分页结果转换成分页响应。
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  数据载荷类型
     * @return 统一分页响应
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return new PageResult<T>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}

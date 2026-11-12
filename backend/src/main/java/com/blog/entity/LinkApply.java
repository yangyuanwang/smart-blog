package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 友链申请实体，对应 t_link_apply。访客前台提交，管理员审核通过后转正为友链。
 */
@Data
@TableName("t_link_apply")
public class LinkApply {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 站点名 */
    private String siteName;

    /** 站点地址 */
    private String siteUrl;

    /** 站点描述 */
    private String siteDesc;

    /** 联系方式（选填） */
    private String contact;

    /** 0 待审 1 通过 2 拒绝 */
    private String status;

    /** 审核备注（拒绝理由） */
    private String remark;

    /** 申请来源 IP */
    private String ip;

    /** 申请时间 */
    private LocalDateTime createTime;
}

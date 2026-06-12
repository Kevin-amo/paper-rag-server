package com.lqr.papermind.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 系统角色实体，对应 RBAC 中的角色定义。
 */
@Data
@TableName("public.sys_role")
public class SysRole {

    /** 角色主键ID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /** 角色编码，如 ADMIN、USER、REVIEWER。 */
    private String code;

    /** 角色名称。 */
    private String name;

    /** 角色描述。 */
    private String description;

    /** 记录创建时间。 */
    private OffsetDateTime createdAt;
}

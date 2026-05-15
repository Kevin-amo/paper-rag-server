package com.lqr.paperragserver.auth.entity;

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

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String code;
    private String name;
    private String description;
    private OffsetDateTime createdAt;
}
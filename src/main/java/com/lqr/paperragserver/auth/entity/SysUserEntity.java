package com.lqr.paperragserver.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 系统用户实体，对应登录账号及账号状态信息。
 */
@Data
@TableName("public.sys_user")
public class SysUserEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String phone;
    private String status;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
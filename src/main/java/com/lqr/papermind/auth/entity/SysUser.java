package com.lqr.papermind.auth.entity;

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
public class SysUser {

    /** 用户主键ID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /** 用户名，用于登录。 */
    private String username;

    /** 密码哈希值。 */
    private String passwordHash;

    /** 用户昵称。 */
    private String displayName;

    /** 邮箱地址。 */
    private String email;

    /** 手机号。 */
    private String phone;

    /** 头像对象存储键。 */
    private String avatarObjectKey;

    /** 头像更新时间。 */
    private OffsetDateTime avatarUpdatedAt;

    /** 用户状态，ACTIVE 或 DISABLED。 */
    private String status;

    /** 最后登录时间。 */
    private OffsetDateTime lastLoginAt;

    /** 记录创建时间。 */
    private OffsetDateTime createdAt;

    /** 记录更新时间。 */
    private OffsetDateTime updatedAt;
}

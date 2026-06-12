package com.lqr.papermind.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户角色关联实体，用于维护一个用户拥有的角色集合。
 */
@Data
@TableName("public.sys_user_role")
public class SysUserRole {

    /** 用户ID。 */
    private UUID userId;

    /** 角色ID。 */
    private UUID roleId;

    /** 记录创建时间。 */
    private OffsetDateTime createdAt;
}

package com.lqr.papermind.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.auth.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * 系统用户 Mapper，提供账号查询、统计和登录时间更新能力。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 统计指定角色下的活跃用户数量。
     *
     * @param roleCode 角色编码
     * @return 活跃用户数量
     */
    @Select("""
            select count(*)
            from public.sys_user u
            join public.sys_user_role ur on ur.user_id = u.id
            join public.sys_role r on r.id = ur.role_id
            where r.code = #{roleCode}
              and u.status = 'ACTIVE'
            """)
    long countActiveByRole(@Param("roleCode") String roleCode);

    /**
     * 查询指定角色下的所有活跃用户列表，按昵称或用户名排序。
     *
     * @param roleCode 角色编码
     * @return 活跃用户列表
     */
    @Select("""
            select u.*
            from public.sys_user u
            join public.sys_user_role ur on ur.user_id = u.id
            join public.sys_role r on r.id = ur.role_id
            where r.code = #{roleCode}
              and u.status = 'ACTIVE'
            order by coalesce(u.display_name, u.username), u.username
            """)
    List<SysUser> selectActiveByRole(@Param("roleCode") String roleCode);

    /**
     * 更新用户最后登录时间。
     *
     * @param id 用户ID
     * @return 影响行数
     */
    @Update("""
            update public.sys_user
            set last_login_at = now(), updated_at = now()
            where id = #{id}
            """)
    int updateLastLoginAt(@Param("id") UUID id);

    /**
     * 更新用户头像对象键。
     *
     * @param id 用户ID
     * @param avatarObjectKey 头像对象存储键
     * @return 影响行数
     */
    @Update("""
            update public.sys_user
            set avatar_object_key = #{avatarObjectKey}, avatar_updated_at = now(), updated_at = now()
            where id = #{id}
            """)
    int updateAvatar(@Param("id") UUID id, @Param("avatarObjectKey") String avatarObjectKey);

    /**
     * 更新用户密码哈希。
     *
     * @param id 用户ID
     * @param passwordHash 新密码哈希值
     * @return 影响行数
     */
    @Update("""
            update public.sys_user
            set password_hash = #{passwordHash}, updated_at = now()
            where id = #{id}
            """)
    int updatePassword(@Param("id") UUID id, @Param("passwordHash") String passwordHash);

    /**
     * 更新用户昵称。
     *
     * @param id 用户ID
     * @param displayName 新昵称
     * @return 影响行数
     */
    @Update("""
            update public.sys_user
            set display_name = #{displayName}, updated_at = now()
            where id = #{id}
            """)
    int updateDisplayName(@Param("id") UUID id, @Param("displayName") String displayName);

    /**
     * 更新用户邮箱。
     *
     * @param id 用户ID
     * @param email 新邮箱地址
     * @return 影响行数
     */
    @Update("""
            update public.sys_user
            set email = #{email}, updated_at = now()
            where id = #{id}
            """)
    int updateEmail(@Param("id") UUID id, @Param("email") String email);
}

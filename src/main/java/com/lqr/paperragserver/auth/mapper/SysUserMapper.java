package com.lqr.paperragserver.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.auth.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 系统用户 Mapper，提供账号查询、统计和登录时间更新能力。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            select count(*)
            from public.sys_user u
            join public.sys_user_role ur on ur.user_id = u.id
            join public.sys_role r on r.id = ur.role_id
            where r.code = #{roleCode}
              and u.status = 'ACTIVE'
            """)
    long countActiveByRole(@Param("roleCode") String roleCode);

    @Update("""
            update public.sys_user
            set last_login_at = now(), updated_at = now()
            where id = #{id}
            """)
    int updateLastLoginAt(@Param("id") UUID id);

    @Update("""
            update public.sys_user
            set avatar_object_key = #{avatarObjectKey}, avatar_updated_at = now(), updated_at = now()
            where id = #{id}
            """)
    int updateAvatar(@Param("id") UUID id, @Param("avatarObjectKey") String avatarObjectKey);

    @Update("""
            update public.sys_user
            set password_hash = #{passwordHash}, updated_at = now()
            where id = #{id}
            """)
    int updatePassword(@Param("id") UUID id, @Param("passwordHash") String passwordHash);

    @Update("""
            update public.sys_user
            set display_name = #{displayName}, updated_at = now()
            where id = #{id}
            """)
    int updateDisplayName(@Param("id") UUID id, @Param("displayName") String displayName);

    @Update("""
            update public.sys_user
            set email = #{email}, updated_at = now()
            where id = #{id}
            """)
    int updateEmail(@Param("id") UUID id, @Param("email") String email);
}
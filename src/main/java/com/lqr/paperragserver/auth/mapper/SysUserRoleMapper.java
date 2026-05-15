package com.lqr.paperragserver.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * 用户角色关联 Mapper，负责维护用户与角色之间的绑定关系。
 */
public interface SysUserRoleMapper {

    @Insert("""
            insert into public.sys_user_role (user_id, role_id)
            values (#{userId}, #{roleId})
            on conflict (user_id, role_id) do nothing
            """)
    int insertIgnore(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    @Delete("""
            delete from public.sys_user_role
            where user_id = #{userId}
            """)
    int deleteByUserId(@Param("userId") UUID userId);
}
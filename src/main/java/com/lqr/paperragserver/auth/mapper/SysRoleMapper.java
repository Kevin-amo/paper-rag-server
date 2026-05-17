package com.lqr.paperragserver.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.paperragserver.auth.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 系统角色 Mapper，提供角色查询和用户角色编码查询能力。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            select r.code
            from public.sys_role r
            join public.sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId}
            order by r.code
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") UUID userId);

    @Select("""
            select *
            from public.sys_role
            where code = #{code}
            """)
    SysRole selectByCode(@Param("code") String code);
}
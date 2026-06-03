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

    /**
     * 根据用户ID查询其拥有的角色编码列表。
     *
     * @param userId 用户ID
     * @return 角色编码列表，按编码排序
     */
    @Select("""
            select r.code
            from public.sys_role r
            join public.sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId}
            order by r.code
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") UUID userId);

    /**
     * 根据角色编码查询角色信息。
     *
     * @param code 角色编码
     * @return 角色实体，不存在时返回 null
     */
    @Select("""
            select *
            from public.sys_role
            where code = #{code}
            """)
    SysRole selectByCode(@Param("code") String code);
}
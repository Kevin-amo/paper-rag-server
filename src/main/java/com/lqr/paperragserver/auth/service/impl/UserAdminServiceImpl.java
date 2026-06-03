package com.lqr.paperragserver.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lqr.paperragserver.auth.entity.SysRole;
import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.mapper.SysUserRoleMapper;
import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.auth.service.TokenRevocationService;
import com.lqr.paperragserver.auth.service.UserAdminService;
import com.lqr.paperragserver.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理员用户管理服务实现，负责用户资料、角色绑定和账号状态维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectStorageService objectStorageService;
    private final TokenRevocationService tokenRevocationService;

    /**
     * 分页查询用户列表，支持关键字和状态筛选。
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param keyword 搜索关键字，匹配用户名、昵称或邮箱
     * @param status 用户状态筛选
     * @return 分页查询结果
     */
    @Override
    public PageResult listUsers(int page, int size, String keyword, String status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(item -> item.like(SysUser::getUsername, like)
                    .or()
                    .like(SysUser::getDisplayName, like)
                    .or()
                    .like(SysUser::getEmail, like));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysUser::getStatus, status.trim().toUpperCase());
        }
        Page<SysUser> result = userMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
        List<UserView> items = result.getRecords().stream().map(this::toUserView).toList();
        return new PageResult(items, safePage, safeSize, result.getTotal());
    }

    /**
     * 创建新用户，校验用户名唯一性后插入记录并绑定角色。
     *
     * @param command 创建用户命令
     * @return 创建后的用户信息
     */
    @Override
    @Transactional
    public UserView createUser(CreateUserCommand command) {
        String username = requireText(command.username(), "用户名不能为空");
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(requireText(command.password(), "密码不能为空")));
        user.setDisplayName(blankToNull(command.displayName()));
        user.setEmail(blankToNull(command.email()));
        user.setStatus("ACTIVE");
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        replaceRoles(user.getId(), normalizeRoles(command.roles()));
        return toUserView(userMapper.selectById(user.getId()));
    }

    /**
     * 更新用户基础资料（昵称和邮箱）。
     *
     * @param id 用户ID
     * @param command 更新用户命令
     * @return 更新后的用户信息
     */
    @Override
    public UserView updateUser(UUID id, UpdateUserCommand command) {
        SysUser user = requireUser(id);
        user.setDisplayName(blankToNull(command.displayName()));
        user.setEmail(blankToNull(command.email()));
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        return toUserView(userMapper.selectById(id));
    }

    /**
     * 更新用户角色列表，移除最后一个管理员角色时校验系统中至少保留一个活跃管理员。
     *
     * @param id 用户ID
     * @param roles 新的角色编码列表
     * @return 更新后的用户信息
     */
    @Override
    @Transactional
    public UserView updateRoles(UUID id, List<String> roles) {
        requireUser(id);
        List<String> nextRoles = normalizeRoles(roles);
        List<String> currentRoles = roleMapper.selectRoleCodesByUserId(id);
        if (currentRoles.contains(RoleCodes.ADMIN) && !nextRoles.contains(RoleCodes.ADMIN)) {
            ensureAnotherActiveAdmin(id);
        }
        replaceRoles(id, nextRoles);
        return toUserView(userMapper.selectById(id));
    }

    /**
     * 更新用户状态，禁用管理员时校验系统中至少保留一个活跃管理员。
     *
     * @param id 用户ID
     * @param status 新状态（ACTIVE 或 DISABLED）
     * @return 更新后的用户信息
     */
    @Override
    public UserView updateStatus(UUID id, String status) {
        SysUser user = requireUser(id);
        String nextStatus = requireText(status, "状态不能为空").toUpperCase();
        if (!Set.of("ACTIVE", "DISABLED").contains(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户状态不合法");
        }
        if ("DISABLED".equals(nextStatus) && roleMapper.selectRoleCodesByUserId(id).contains(RoleCodes.ADMIN)) {
            ensureAnotherActiveAdmin(id);
        }
        user.setStatus(nextStatus);
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        return toUserView(userMapper.selectById(id));
    }

    /**
     * 重置用户密码，同时撤销该用户所有已签发令牌。
     *
     * @param id 用户ID
     * @param password 新密码
     */
    @Override
    public void resetPassword(UUID id, String password) {
        SysUser user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(requireText(password, "密码不能为空")));
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        tokenRevocationService.revokeAllTokensForUser(id);
        log.info("Admin reset password, all tokens revoked: userId={}", id);
    }

    /**
     * 删除用户及其角色关联，删除管理员时校验系统中至少保留一个活跃管理员。
     *
     * @param id 用户ID
     */
    @Override
    @Transactional
    public void deleteUser(UUID id) {
        SysUser user = requireUser(id);
        if (roleMapper.selectRoleCodesByUserId(id).contains(RoleCodes.ADMIN)) {
            ensureAnotherActiveAdmin(id);
        }
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(user.getId());
    }

    /**
     * 替换用户的角色列表，先删除旧关联再逐个插入新关联。
     *
     * @param userId 用户ID
     * @param roles 角色编码列表
     */
    private void replaceRoles(UUID userId, List<String> roles) {
        userRoleMapper.deleteByUserId(userId);
        for (String roleCode : roles) {
            SysRole role = roleMapper.selectByCode(roleCode);
            if (role == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在：" + roleCode);
            }
            userRoleMapper.insertIgnore(userId, role.getId());
        }
    }

    /**
     * 标准化角色列表，去重并校验合法性，为空时默认赋予 USER 角色。
     *
     * @param roles 原始角色编码列表
     * @return 标准化后的角色编码列表
     * @throws ResponseStatusException 角色不合法时抛出
     */
    private List<String> normalizeRoles(List<String> roles) {
        List<String> normalized = roles == null || roles.isEmpty() ? List.of(RoleCodes.USER) : roles;
        LinkedHashSet<String> uniqueRoles = normalized.stream()
                .map(role -> requireText(role, "角色不能为空").toUpperCase())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        uniqueRoles.forEach(role -> {
            if (!Set.of(RoleCodes.ADMIN, RoleCodes.USER).contains(role)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不合法：" + role);
            }
        });
        return List.copyOf(uniqueRoles);
    }

    /**
     * 校验系统中至少还有一个其他活跃管理员，防止移除或禁用最后一个管理员。
     *
     * @param currentUserId 当前操作的用户ID
     * @throws ResponseStatusException 系统中仅剩一个活跃管理员时抛出
     */
    private void ensureAnotherActiveAdmin(UUID currentUserId) {
        long activeAdminCount = userMapper.countActiveByRole(RoleCodes.ADMIN);
        if (activeAdminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移除或禁用最后一个管理员");
        }
    }

    /**
     * 根据ID查询用户，不存在时抛出404异常。
     *
     * @param id 用户ID
     * @return 用户实体
     * @throws ResponseStatusException 用户不存在时抛出
     */
    private SysUser requireUser(UUID id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 将用户实体转换为管理后台展示的用户视图。
     *
     * @param user 系统用户实体
     * @return 用户视图
     */
    private UserView toUserView(SysUser user) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        return new UserView(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getStatus(),
                roles,
                objectStorageService.publicUrl(user.getAvatarObjectKey()),
                format(user.getLastLoginAt()),
                format(user.getCreatedAt()),
                format(user.getUpdatedAt())
        );
    }

    /**
     * 校验字符串非空，为空时抛出400异常。
     *
     * @param value 待校验字符串
     * @param message 异常提示信息
     * @return 去除首尾空白后的字符串
     * @throws ResponseStatusException 字符串为空时抛出
     */
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /**
     * 将空白字符串转为 null，非空白则去除首尾空白。
     *
     * @param value 原始字符串
     * @return 处理后的字符串或 null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 将时间戳格式化为字符串，为 null 时返回 null。
     *
     * @param value 时间戳
     * @return 格式化后的字符串或 null
     */
    private String format(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
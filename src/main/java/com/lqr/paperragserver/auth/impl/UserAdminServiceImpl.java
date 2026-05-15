package com.lqr.paperragserver.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lqr.paperragserver.auth.entity.SysRoleEntity;
import com.lqr.paperragserver.auth.entity.SysUserEntity;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.mapper.SysUserRoleMapper;
import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.auth.service.UserAdminService;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult listUsers(int page, int size, String keyword, String status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .orderByDesc(SysUserEntity::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(item -> item.like(SysUserEntity::getUsername, like)
                    .or()
                    .like(SysUserEntity::getDisplayName, like)
                    .or()
                    .like(SysUserEntity::getEmail, like));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysUserEntity::getStatus, status.trim().toUpperCase());
        }
        Page<SysUserEntity> result = userMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
        List<UserView> items = result.getRecords().stream().map(this::toUserView).toList();
        return new PageResult(items, safePage, safeSize, result.getTotal());
    }

    @Override
    @Transactional
    public UserView createUser(CreateUserCommand command) {
        String username = requireText(command.username(), "用户名不能为空");
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getUsername, username)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        SysUserEntity user = new SysUserEntity();
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

    @Override
    public UserView updateUser(UUID id, UpdateUserCommand command) {
        SysUserEntity user = requireUser(id);
        user.setDisplayName(blankToNull(command.displayName()));
        user.setEmail(blankToNull(command.email()));
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        return toUserView(userMapper.selectById(id));
    }

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

    @Override
    public UserView updateStatus(UUID id, String status) {
        SysUserEntity user = requireUser(id);
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

    @Override
    public void resetPassword(UUID id, String password) {
        SysUserEntity user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(requireText(password, "密码不能为空")));
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
    }

    private void replaceRoles(UUID userId, List<String> roles) {
        userRoleMapper.deleteByUserId(userId);
        for (String roleCode : roles) {
            SysRoleEntity role = roleMapper.selectByCode(roleCode);
            if (role == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在：" + roleCode);
            }
            userRoleMapper.insertIgnore(userId, role.getId());
        }
    }

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

    private void ensureAnotherActiveAdmin(UUID currentUserId) {
        long activeAdminCount = userMapper.countActiveByRole(RoleCodes.ADMIN);
        if (activeAdminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移除或禁用最后一个管理员");
        }
    }

    private SysUserEntity requireUser(UUID id) {
        SysUserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserView toUserView(SysUserEntity user) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        return new UserView(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getStatus(),
                roles,
                format(user.getLastLoginAt()),
                format(user.getCreatedAt()),
                format(user.getUpdatedAt())
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
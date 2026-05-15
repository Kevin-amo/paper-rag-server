package com.lqr.paperragserver.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lqr.paperragserver.auth.entity.SysRoleEntity;
import com.lqr.paperragserver.auth.entity.SysUserEntity;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.mapper.SysUserRoleMapper;
import com.lqr.paperragserver.auth.security.DatabaseUserDetailsService;
import com.lqr.paperragserver.auth.security.JwtTokenService;
import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.auth.service.AuthService;
import com.lqr.paperragserver.auth.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 默认认证服务实现，使用数据库账号、BCrypt 密码和 JWT 令牌完成登录流程。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final DatabaseUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final VerificationCodeService verificationCodeService;

    @Override
    public LoginResult login(String username, String password) {
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!userDetails.isEnabled()) {
            throw new BadCredentialsException("用户已被禁用");
        }
        SecurityUserPrincipal principal = (SecurityUserPrincipal) userDetails;
        userMapper.updateLastLoginAt(principal.getId());
        return createLoginResult(principal);
    }

    @Override
    public void createRegisterEmailCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getEmail, normalizedEmail)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }
        verificationCodeService.createRegisterEmailCode(normalizedEmail);
    }

    @Override
    @Transactional
    public LoginResult registerWithEmailCode(String username, String password, String email, String emailCode) {
        String normalizedUsername = requireText(username, "用户名不能为空");
        String normalizedPassword = requireText(password, "密码不能为空");
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = requireText(emailCode, "验证码不能为空");
        verificationCodeService.requireRegisterEmailCodeMatches(normalizedEmail, normalizedCode);

        if (userMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getUsername, normalizedUsername)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getEmail, normalizedEmail)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }

        SysUserEntity user = new SysUserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(normalizedPassword));
        user.setDisplayName(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setStatus("ACTIVE");
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        SysRoleEntity role = roleMapper.selectByCode(RoleCodes.USER);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "默认用户角色不存在");
        }
        userRoleMapper.insertIgnore(user.getId(), role.getId());
        verificationCodeService.deleteRegisterEmailCode(normalizedEmail);

        SecurityUserPrincipal principal = new SecurityUserPrincipal(user, List.of(RoleCodes.USER));
        userMapper.updateLastLoginAt(principal.getId());
        return createLoginResult(principal);
    }

    @Override
    public CurrentUser currentUser(SecurityUserPrincipal principal) {
        return new CurrentUser(
                principal.getId().toString(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getEmail(),
                principal.getRoles()
        );
    }

    private LoginResult createLoginResult(SecurityUserPrincipal principal) {
        return new LoginResult(
                jwtTokenService.createAccessToken(principal),
                "Bearer",
                jwtTokenService.accessTokenExpiresInSeconds(),
                currentUser(principal)
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = requireText(email, "邮箱不能为空").toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不合法");
        }
        return normalizedEmail;
    }
}
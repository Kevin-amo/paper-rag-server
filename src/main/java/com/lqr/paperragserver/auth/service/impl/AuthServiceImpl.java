package com.lqr.paperragserver.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lqr.paperragserver.auth.entity.SysRole;
import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.mapper.SysUserRoleMapper;
import com.lqr.paperragserver.auth.security.DatabaseUserDetailsService;
import com.lqr.paperragserver.auth.security.JwtTokenService;
import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.auth.service.AuthService;
import com.lqr.paperragserver.auth.service.LoginAttemptService;
import com.lqr.paperragserver.auth.service.TokenRevocationService;
import com.lqr.paperragserver.auth.service.VerificationCodeService;
import com.lqr.paperragserver.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
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
@Slf4j
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
    private final LoginAttemptService loginAttemptService;
    private final TokenRevocationService tokenRevocationService;
    private final ObjectStorageService objectStorageService;

    /**
     * 用户登录校验，校验密码和账号状态后签发访问令牌。
     *
     * @param username 用户名
     * @param password 密码
     * @param clientIp 客户端IP地址
     * @return 登录结果，包含令牌和用户信息
     */
    @Override
    public LoginResult login(String username, String password, String clientIp) {
        loginAttemptService.assertNotLocked(username, clientIp);
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ex) {
            loginAttemptService.recordFailure(username, clientIp);
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            loginAttemptService.recordFailure(username, clientIp);
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!userDetails.isEnabled()) {
            throw new BadCredentialsException("用户已被禁用");
        }
        SecurityUserPrincipal principal = (SecurityUserPrincipal) userDetails;
        loginAttemptService.clearOnSuccess(username, clientIp);
        userMapper.updateLastLoginAt(principal.getId());
        return createLoginResult(principal);
    }

    /**
     * 发送邮箱注册验证码，校验邮箱是否已被注册。
     *
     * @param email 邮箱地址
     * @param clientIp 客户端IP地址
     */
    @Override
    public void createRegisterEmailCode(String email, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, normalizedEmail)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }
        verificationCodeService.createRegisterEmailCode(normalizedEmail, clientIp);
    }

    /**
     * 使用邮箱验证码完成注册，创建用户并分配默认角色后自动登录。
     *
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱地址
     * @param emailCode 邮箱验证码
     * @return 登录结果，包含令牌和用户信息
     */
    @Override
    @Transactional
    public LoginResult registerWithEmailCode(String username, String password, String email, String emailCode) {
        String normalizedUsername = requireText(username, "用户名不能为空");
        String normalizedPassword = requireText(password, "密码不能为空");
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = requireText(emailCode, "验证码不能为空");
        verificationCodeService.requireRegisterEmailCodeMatches(normalizedEmail, normalizedCode);

        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, normalizedUsername)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, normalizedEmail)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }

        SysUser user = new SysUser();
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

        SysRole role = roleMapper.selectByCode(RoleCodes.USER);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "默认用户角色不存在");
        }
        userRoleMapper.insertIgnore(user.getId(), role.getId());
        verificationCodeService.deleteRegisterEmailCode(normalizedEmail);

        SecurityUserPrincipal principal = new SecurityUserPrincipal(user, List.of(RoleCodes.USER));
        userMapper.updateLastLoginAt(principal.getId());
        return createLoginResult(principal);
    }

    /**
     * 用户登出，撤销当前访问令牌。令牌无效时静默处理以保持幂等。
     *
     * @param token 访问令牌
     */
    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            tokenRevocationService.revoke(token, jwtTokenService.decode(token).getExpiresAt());
        } catch (JwtException ex) {
            // 退出接口保持幂等，已失效或非法 token 无需阻断前端清理本地会话。
        }
    }

    /**
     * 获取当前登录用户信息。
     *
     * @param principal 当前用户主体
     * @return 当前用户信息
     */
    @Override
    public CurrentUser currentUser(SecurityUserPrincipal principal) {
        return new CurrentUser(
                principal.getId().toString(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getEmail(),
                objectStorageService.publicUrl(principal.getAvatarObjectKey()),
                principal.getRoles()
        );
    }

    /**
     * 修改当前用户密码，校验当前密码正确性和新密码差异性，成功后撤销该用户所有令牌。
     *
     * @param principal 当前用户主体
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     * @return 更新后的用户信息
     */
    @Override
    @Transactional
    public CurrentUser changePassword(SecurityUserPrincipal principal, String currentPassword, String newPassword) {
        SysUser user = requireUser(principal.getId());
        String normalizedCurrentPassword = requireText(currentPassword, "当前密码不能为空");
        String normalizedNewPassword = requireText(newPassword, "新密码不能为空");
        if (!passwordEncoder.matches(normalizedCurrentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        if (passwordEncoder.matches(normalizedNewPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(normalizedNewPassword));
        tokenRevocationService.revokeAllTokensForUser(user.getId());
        log.info("Password changed successfully, all tokens revoked: userId={}", user.getId());
        return toCurrentUser(userMapper.selectById(user.getId()));
    }

    /**
     * 修改当前用户昵称。
     *
     * @param principal 当前用户主体
     * @param displayName 新昵称
     * @return 更新后的用户信息
     */
    @Override
    @Transactional
    public CurrentUser changeDisplayName(SecurityUserPrincipal principal, String displayName) {
        SysUser user = requireUser(principal.getId());
        String normalizedDisplayName = requireText(displayName, "昵称不能为空");
        userMapper.updateDisplayName(user.getId(), normalizedDisplayName);
        return toCurrentUser(userMapper.selectById(user.getId()));
    }

    /**
     * 发送换绑邮箱验证码，校验新邮箱可用性。
     *
     * @param principal 当前用户主体
     * @param email 新邮箱地址
     * @param clientIp 客户端IP地址
     */
    @Override
    public void createChangeEmailCode(SecurityUserPrincipal principal, String email, String clientIp) {
        SysUser user = requireUser(principal.getId());
        String normalizedEmail = normalizeEmail(email);
        ensureEmailAvailable(user, normalizedEmail);
        verificationCodeService.createChangeEmailCode(normalizedEmail, clientIp);
    }

    /**
     * 使用验证码换绑邮箱，校验新邮箱可用性和验证码正确性。
     *
     * @param principal 当前用户主体
     * @param email 新邮箱地址
     * @param emailCode 邮箱验证码
     * @return 更新后的用户信息
     */
    @Override
    @Transactional
    public CurrentUser changeEmail(SecurityUserPrincipal principal, String email, String emailCode) {
        SysUser user = requireUser(principal.getId());
        String normalizedEmail = normalizeEmail(email);
        ensureEmailAvailable(user, normalizedEmail);
        verificationCodeService.requireChangeEmailCodeMatches(normalizedEmail, requireText(emailCode, "验证码不能为空"));
        userMapper.updateEmail(user.getId(), normalizedEmail);
        verificationCodeService.deleteChangeEmailCode(normalizedEmail);
        return toCurrentUser(userMapper.selectById(user.getId()));
    }

    /**
     * 构造登录结果，包含访问令牌和当前用户信息。
     *
     * @param principal 用户主体
     * @return 登录结果
     */
    private LoginResult createLoginResult(SecurityUserPrincipal principal) {
        return new LoginResult(
                jwtTokenService.createAccessToken(principal),
                "Bearer",
                jwtTokenService.accessTokenExpiresInSeconds(),
                currentUser(principal)
        );
    }

    /**
     * 将用户实体转换为当前用户信息。
     *
     * @param user 系统用户实体
     * @return 当前用户信息
     */
    private CurrentUser toCurrentUser(SysUser user) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        return new CurrentUser(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                objectStorageService.publicUrl(user.getAvatarObjectKey()),
                roles
        );
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
     * 校验新邮箱可用性，确保不与当前邮箱相同且未被其他用户注册。
     *
     * @param currentUser 当前用户实体
     * @param normalizedEmail 标准化后的邮箱地址
     * @throws ResponseStatusException 邮箱不可用时抛出
     */
    private void ensureEmailAvailable(SysUser currentUser, String normalizedEmail) {
        if (currentUser.getEmail() != null
                && normalizedEmail.equals(currentUser.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新邮箱不能与当前邮箱相同");
        }
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, normalizedEmail));
        if (existing != null && !existing.getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }
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
     * 标准化邮箱地址，校验非空和格式合法性后转为小写。
     *
     * @param email 邮箱地址
     * @return 标准化后的邮箱地址
     * @throws ResponseStatusException 邮箱为空或格式不合法时抛出
     */
    private String normalizeEmail(String email) {
        String normalizedEmail = requireText(email, "邮箱不能为空").toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不合法");
        }
        return normalizedEmail;
    }
}
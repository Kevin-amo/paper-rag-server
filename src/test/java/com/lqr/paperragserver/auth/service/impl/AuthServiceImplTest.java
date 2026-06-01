package com.lqr.paperragserver.auth.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final TokenRevocationService tokenRevocationService = mock(TokenRevocationService.class);
    private final ObjectStorageService objectStorageService = mock(ObjectStorageService.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userDetailsService,
                passwordEncoder,
                jwtTokenService,
                userMapper,
                roleMapper,
                userRoleMapper,
                verificationCodeService,
                loginAttemptService,
                tokenRevocationService,
                objectStorageService
        );
    }

    @Test
    void changePasswordShouldRejectSameAsCurrentPassword() {
        SysUser user = user("current@example.com");
        when(userMapper.selectById(user.getId())).thenReturn(user);
        when(passwordEncoder.matches("password", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(principal(user), "password", "password"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("新密码不能与当前密码相同");
                });

        verify(userMapper, never()).updatePassword(any(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userDetailsService, never()).evictUserDetails(anyString());
    }

    @Test
    void changeDisplayNameShouldRejectBlankDisplayName() {
        SysUser user = user("current@example.com");
        when(userMapper.selectById(user.getId())).thenReturn(user);

        assertThatThrownBy(() -> service.changeDisplayName(principal(user), " "))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("昵称不能为空");
                });

        verify(userMapper, never()).updateDisplayName(any(), anyString());
        verify(userDetailsService, never()).evictUserDetails(anyString());
    }

    @Test
    void changeDisplayNameShouldUpdateCurrentUserDisplayName() {
        SysUser user = user("current@example.com");
        SysUser updatedUser = user("current@example.com");
        updatedUser.setId(user.getId());
        updatedUser.setDisplayName("New Nick");
        when(userMapper.selectById(user.getId())).thenReturn(user, updatedUser);
        when(roleMapper.selectRoleCodesByUserId(user.getId())).thenReturn(List.of(RoleCodes.USER));

        AuthService.CurrentUser result = service.changeDisplayName(principal(user), " New Nick ");

        assertThat(result.displayName()).isEqualTo("New Nick");
        verify(userMapper).updateDisplayName(user.getId(), "New Nick");
        verify(userDetailsService).evictUserDetails(user.getUsername());
    }

    @Test
    void createChangeEmailCodeShouldRejectCurrentEmail() {
        SysUser user = user("current@example.com");
        when(userMapper.selectById(user.getId())).thenReturn(user);

        assertThatThrownBy(() -> service.createChangeEmailCode(principal(user), " Current@Example.com ", "127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("新邮箱不能与当前邮箱相同");
                });

        verify(userMapper, never()).selectOne(any());
        verify(verificationCodeService, never()).createChangeEmailCode(anyString(), anyString());
    }

    @Test
    void changeEmailShouldRejectCurrentEmail() {
        SysUser user = user("current@example.com");
        when(userMapper.selectById(user.getId())).thenReturn(user);

        assertThatThrownBy(() -> service.changeEmail(principal(user), "current@example.com", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("新邮箱不能与当前邮箱相同");
                });

        verify(userMapper, never()).selectOne(any());
        verify(verificationCodeService, never()).requireChangeEmailCodeMatches(anyString(), anyString());
    }

    private SecurityUserPrincipal principal(SysUser user) {
        return new SecurityUserPrincipal(user, List.of(RoleCodes.USER));
    }

    private SysUser user(String email) {
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setPasswordHash("{noop}password");
        user.setDisplayName("Alice");
        user.setEmail(email);
        user.setStatus("ACTIVE");
        return user;
    }
}
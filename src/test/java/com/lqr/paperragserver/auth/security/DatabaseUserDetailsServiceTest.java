package com.lqr.paperragserver.auth.security;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private DatabaseUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseUserDetailsService(userMapper, roleMapper);
    }

    @Test
    void loadUserByUsernameShouldQueryDatabaseAndRolesEveryTime() {
        SysUser user = user("alice");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectRoleCodesByUserId(user.getId())).thenReturn(List.of(RoleCodes.USER));

        SecurityUserPrincipal firstPrincipal = (SecurityUserPrincipal) service.loadUserByUsername("alice");
        SecurityUserPrincipal secondPrincipal = (SecurityUserPrincipal) service.loadUserByUsername("alice");

        assertThat(firstPrincipal.getUsername()).isEqualTo("alice");
        assertThat(firstPrincipal.getRoles()).containsExactly(RoleCodes.USER);
        assertThat(secondPrincipal.getUsername()).isEqualTo("alice");
        assertThat(secondPrincipal.getRoles()).containsExactly(RoleCodes.USER);
        verify(userMapper, times(2)).selectOne(any());
        verify(roleMapper, times(2)).selectRoleCodesByUserId(user.getId());
    }

    @Test
    void loadUserByUsernameShouldThrowWhenUserDoesNotExist() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("用户不存在");

        verify(roleMapper, never()).selectRoleCodesByUserId(any());
    }

    private SysUser user(String username) {
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash("{noop}password");
        user.setDisplayName("Alice");
        user.setEmail(username + "@example.com");
        user.setAvatarObjectKey("avatars/" + username + ".png");
        user.setStatus("ACTIVE");
        return user;
    }
}

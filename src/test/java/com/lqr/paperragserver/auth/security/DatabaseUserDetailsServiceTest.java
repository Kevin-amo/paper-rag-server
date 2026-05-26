package com.lqr.paperragserver.auth.security;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.config.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class DatabaseUserDetailsServiceTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DatabaseUserDetailsService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DatabaseUserDetailsService(
                userMapper,
                roleMapper,
                redisTemplate,
                objectMapper,
                new SecurityProperties(null, null, null, null,
                        new SecurityProperties.UserDetailsCache(true, Duration.ofMinutes(10)))
        );
    }

    @Test
    void loadUserByUsernameShouldQueryDatabaseAndWriteCacheWhenCacheMiss() {
        SysUser user = user("alice");
        when(valueOperations.get("auth:user-details:alice")).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectRoleCodesByUserId(user.getId())).thenReturn(List.of(RoleCodes.USER));

        SecurityUserPrincipal principal = (SecurityUserPrincipal) service.loadUserByUsername("alice");

        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getRoles()).containsExactly(RoleCodes.USER);
        verify(userMapper).selectOne(any());
        verify(roleMapper).selectRoleCodesByUserId(user.getId());
        verify(valueOperations).set(eq("auth:user-details:alice"), any(String.class), eq(Duration.ofMinutes(10)));
    }

    @Test
    void loadUserByUsernameShouldReturnCachedPrincipalWhenCacheIsBackedByDatabase() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SysUser user = user("alice");
        user.setId(userId);
        when(valueOperations.get("auth:user-details:alice")).thenReturn("""
                {
                  "id":"00000000-0000-0000-0000-000000000001",
                  "username":"alice",
                  "passwordHash":"{noop}password",
                  "displayName":"Alice",
                  "email":"alice@example.com",
                  "avatarObjectKey":"avatars/alice.png",
                  "status":"ACTIVE",
                  "roles":["USER"]
                }
                """);
        when(userMapper.selectById(userId)).thenReturn(user);

        SecurityUserPrincipal principal = (SecurityUserPrincipal) service.loadUserByUsername("alice");

        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getDisplayName()).isEqualTo("Alice");
        assertThat(principal.getAvatarObjectKey()).isEqualTo("avatars/alice.png");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(userMapper).selectById(userId);
        verify(userMapper, never()).selectOne(any());
        verify(roleMapper, never()).selectRoleCodesByUserId(any());
    }

    @Test
    void loadUserByUsernameShouldDiscardStaleCachedPrincipalAndReloadDatabase() {
        UUID staleUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SysUser user = user("alice");
        when(valueOperations.get("auth:user-details:alice")).thenReturn("""
                {
                  "id":"00000000-0000-0000-0000-000000000001",
                  "username":"alice",
                  "passwordHash":"{noop}password",
                  "displayName":"Old Alice",
                  "email":"old-alice@example.com",
                  "avatarObjectKey":null,
                  "status":"ACTIVE",
                  "roles":["USER"]
                }
                """);
        when(userMapper.selectById(staleUserId)).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectRoleCodesByUserId(user.getId())).thenReturn(List.of(RoleCodes.USER));

        SecurityUserPrincipal principal = (SecurityUserPrincipal) service.loadUserByUsername("alice");

        assertThat(principal.getId()).isEqualTo(user.getId());
        assertThat(principal.getDisplayName()).isEqualTo("Alice");
        verify(redisTemplate).delete("auth:user-details:alice");
        verify(valueOperations).set(eq("auth:user-details:alice"), any(String.class), eq(Duration.ofMinutes(10)));
    }

    @Test
    void loadUserByUsernameShouldFallbackToDatabaseAndRewriteCacheWhenCachePayloadIsInvalid() {
        SysUser user = user("alice");
        when(valueOperations.get("auth:user-details:alice")).thenReturn("not-json");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectRoleCodesByUserId(user.getId())).thenReturn(List.of(RoleCodes.ADMIN, RoleCodes.USER));

        SecurityUserPrincipal principal = (SecurityUserPrincipal) service.loadUserByUsername("alice");

        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getRoles()).containsExactly(RoleCodes.ADMIN, RoleCodes.USER);
        verify(userMapper).selectOne(any());
        verify(roleMapper).selectRoleCodesByUserId(user.getId());
        verify(valueOperations).set(eq("auth:user-details:alice"), any(String.class), eq(Duration.ofMinutes(10)));
    }

    @Test
    void loadUserByUsernameShouldNotWriteCacheWhenUserDoesNotExist() {
        when(valueOperations.get("auth:user-details:missing")).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("用户不存在");

        verify(roleMapper, never()).selectRoleCodesByUserId(any());
        verify(valueOperations, never()).set(any(String.class), any(String.class), any(Duration.class));
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
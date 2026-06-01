package com.lqr.paperragserver.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security 用户详情服务，从数据库加载账号及其角色信息。
 */
@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final String USER_DETAILS_KEY_PREFIX = "auth:user-details:";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SecurityUserPrincipal cachedPrincipal = readCachedPrincipal(username);
        if (cachedPrincipal != null && isCacheBackedByDatabase(username, cachedPrincipal)) {
            return cachedPrincipal;
        }
        SecurityUserPrincipal principal = loadPrincipalFromDatabase(username);
        writeCachedPrincipal(principal);
        return principal;
    }

    private SecurityUserPrincipal loadPrincipalFromDatabase(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        return new SecurityUserPrincipal(user, roles);
    }

    private SecurityUserPrincipal readCachedPrincipal(String username) {
        SecurityProperties.UserDetailsCache config = securityProperties.userDetailsCache();
        if (!config.enabled()) {
            return null;
        }
        try {
            String payload = redisTemplate.opsForValue().get(userDetailsKey(username));
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, CachedUserDetails.class).toPrincipal();
        } catch (RuntimeException | JsonProcessingException ex) {
            return null;
        }
    }

    private boolean isCacheBackedByDatabase(String username, SecurityUserPrincipal cachedPrincipal) {
        try {
            SysUser user = userMapper.selectById(cachedPrincipal.getId());
            boolean valid = user != null && username.equals(user.getUsername());
            if (!valid) {
                redisTemplate.delete(userDetailsKey(username));
            }
            return valid;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void writeCachedPrincipal(SecurityUserPrincipal principal) {
        SecurityProperties.UserDetailsCache config = securityProperties.userDetailsCache();
        if (!config.enabled()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(CachedUserDetails.from(principal));
            redisTemplate.opsForValue().set(userDetailsKey(principal.getUsername()), payload, config.ttl());
        } catch (RuntimeException | JsonProcessingException ex) {
            // Redis 缓存只做性能优化，写入失败不影响认证主链路。
        }
    }

    public void evictUserDetails(String username) {
        SecurityProperties.UserDetailsCache config = securityProperties.userDetailsCache();
        if (!config.enabled() || username == null || username.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(userDetailsKey(username));
        } catch (RuntimeException ex) {
            // 缓存清理失败不阻断账号资料更新。
        }
    }

    private String userDetailsKey(String username) {
        return USER_DETAILS_KEY_PREFIX + username;
    }

    private record CachedUserDetails(
            UUID id,
            String username,
            String passwordHash,
            String displayName,
            String email,
            String avatarObjectKey,
            String status,
            List<String> roles
    ) {
        private static CachedUserDetails from(SecurityUserPrincipal principal) {
            return new CachedUserDetails(
                    principal.getId(),
                    principal.getUsername(),
                    principal.getPassword(),
                    principal.getDisplayName(),
                    principal.getEmail(),
                    principal.getAvatarObjectKey(),
                    principal.getStatus(),
                    principal.getRoles()
            );
        }

        private SecurityUserPrincipal toPrincipal() {
            SysUser user = new SysUser();
            user.setId(id);
            user.setUsername(username);
            user.setPasswordHash(passwordHash);
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.setAvatarObjectKey(avatarObjectKey);
            user.setStatus(status);
            return new SecurityUserPrincipal(user, roles == null ? List.of() : roles);
        }
    }
}

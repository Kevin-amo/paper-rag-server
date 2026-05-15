package com.lqr.paperragserver.auth.security;

import com.lqr.paperragserver.auth.entity.SysUserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security 当前用户主体，封装用户基础信息、角色和授权标识。
 */
public class SecurityUserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String password;
    private final String displayName;
    private final String email;
    private final String status;
    private final List<String> roles;
    private final List<GrantedAuthority> authorities;

    public SecurityUserPrincipal(SysUserEntity user, List<String> roles) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.email = user.getEmail();
        this.status = user.getStatus();
        this.roles = List.copyOf(roles);
        this.authorities = roles.stream()
                .map(RoleCodes::authority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}
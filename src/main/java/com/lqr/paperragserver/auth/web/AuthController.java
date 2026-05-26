package com.lqr.paperragserver.auth.web;

import com.lqr.paperragserver.auth.dto.LoginRequest;
import com.lqr.paperragserver.auth.dto.RegisterEmailCodeRequest;
import com.lqr.paperragserver.auth.dto.RegisterRequest;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.auth.service.AuthService;
import com.lqr.paperragserver.auth.service.UserAvatarService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证接口控制器，提供登录、注册、当前用户信息和无状态退出入口。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserAvatarService userAvatarService;

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request.username(), request.password(), clientIp(httpRequest));
    }

    @PostMapping("/register/email-code")
    public void createRegisterEmailCode(@Valid @RequestBody RegisterEmailCodeRequest request, HttpServletRequest httpRequest) {
        authService.createRegisterEmailCode(request.email(), clientIp(httpRequest));
    }

    @PostMapping("/register")
    public AuthService.LoginResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.registerWithEmailCode(request.username(), request.password(), request.email(), request.emailCode());
    }

    @GetMapping("/me")
    public AuthService.CurrentUser me(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        return authService.currentUser(principal);
    }

    @PostMapping("/me/avatar")
    public AuthService.CurrentUser uploadAvatar(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                @RequestParam("file") MultipartFile file) {
        return userAvatarService.uploadAvatar(principal, file);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        authService.logout(resolveBearerToken(request));
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
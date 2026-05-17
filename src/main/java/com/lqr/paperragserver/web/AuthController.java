package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.auth.service.AuthService;
import com.lqr.paperragserver.auth.service.UserAvatarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
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
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/register/email-code")
    public void createRegisterEmailCode(@Valid @RequestBody RegisterEmailCodeRequest request) {
        authService.createRegisterEmailCode(request.email());
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
    public void logout() {
        // JWT 第一版为无状态退出，前端清理本地 token 即可。
    }

    /**
     * 登录请求体。
     */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    /**
     * 邮箱注册验证码请求体。
     */
    public record RegisterEmailCodeRequest(
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email
    ) {
    }

    /**
     * 邮箱验证码注册请求体。
     */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email,
            @NotBlank(message = "验证码不能为空") @Pattern(regexp = "\\d{6}", message = "验证码必须是6位数字") String emailCode
    ) {
    }
}
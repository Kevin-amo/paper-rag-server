package com.lqr.papermind.auth.web;

import com.lqr.papermind.auth.dto.ChangeDisplayNameRequest;
import com.lqr.papermind.auth.dto.ChangeEmailCodeRequest;
import com.lqr.papermind.auth.dto.ChangeEmailRequest;
import com.lqr.papermind.auth.dto.ChangePasswordRequest;
import com.lqr.papermind.auth.dto.LoginRequest;
import com.lqr.papermind.auth.dto.RegisterEmailCodeRequest;
import com.lqr.papermind.auth.dto.RegisterRequest;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.auth.service.AuthService;
import com.lqr.papermind.auth.service.UserAvatarService;
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

    /**
     * 用户登录接口，校验用户名和密码后返回访问令牌。
     *
     * @param request 登录请求体，包含用户名和密码
     * @param httpRequest HTTP 请求，用于获取客户端IP
     * @return 登录结果，包含访问令牌和用户信息
     */
    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request.username(), request.password(), clientIp(httpRequest));
    }

    /**
     * 发送注册邮箱验证码接口。
     *
     * @param request 邮箱验证码请求体，包含邮箱地址
     * @param httpRequest HTTP 请求，用于获取客户端IP
     */
    @PostMapping("/register/email-code")
    public void createRegisterEmailCode(@Valid @RequestBody RegisterEmailCodeRequest request, HttpServletRequest httpRequest) {
        authService.createRegisterEmailCode(request.email(), clientIp(httpRequest));
    }

    /**
     * 用户注册接口，使用邮箱验证码完成注册并自动登录。
     *
     * @param request 注册请求体，包含用户名、密码、邮箱和验证码
     * @return 登录结果，包含访问令牌和用户信息
     */
    @PostMapping("/register")
    public AuthService.LoginResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.registerWithEmailCode(request.username(), request.password(), request.email(), request.emailCode());
    }

    /**
     * 获取当前登录用户信息接口。
     *
     * @param principal 当前认证用户主体
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public AuthService.CurrentUser me(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        return authService.currentUser(principal);
    }

    /**
     * 上传当前用户头像接口。
     *
     * @param principal 当前认证用户主体
     * @param file 头像文件
     * @return 更新后的用户信息
     */
    @PostMapping("/me/avatar")
    public AuthService.CurrentUser uploadAvatar(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                @RequestParam("file") MultipartFile file) {
        return userAvatarService.uploadAvatar(principal, file);
    }

    /**
     * 修改当前用户密码接口。
     *
     * @param principal 当前认证用户主体
     * @param request 修改密码请求体，包含当前密码和新密码
     * @return 更新后的用户信息
     */
    @PostMapping("/me/password")
    public AuthService.CurrentUser changePassword(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(principal, request.currentPassword(), request.newPassword());
    }

    /**
     * 修改当前用户昵称接口。
     *
     * @param principal 当前认证用户主体
     * @param request 修改昵称请求体，包含新昵称
     * @return 更新后的用户信息
     */
    @PostMapping("/me/display-name")
    public AuthService.CurrentUser changeDisplayName(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @Valid @RequestBody ChangeDisplayNameRequest request) {
        return authService.changeDisplayName(principal, request.displayName());
    }

    /**
     * 发送换绑邮箱验证码接口。
     *
     * @param principal 当前认证用户主体
     * @param request 邮箱验证码请求体，包含新邮箱地址
     * @param httpRequest HTTP 请求，用于获取客户端IP
     */
    @PostMapping("/me/email-code")
    public void createChangeEmailCode(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                       @Valid @RequestBody ChangeEmailCodeRequest request,
                                       HttpServletRequest httpRequest) {
        authService.createChangeEmailCode(principal, request.email(), clientIp(httpRequest));
    }

    /**
     * 换绑邮箱接口，使用验证码完成邮箱更换。
     *
     * @param principal 当前认证用户主体
     * @param request 换绑邮箱请求体，包含新邮箱和验证码
     * @return 更新后的用户信息
     */
    @PostMapping("/me/email")
    public AuthService.CurrentUser changeEmail(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                @Valid @RequestBody ChangeEmailRequest request) {
        return authService.changeEmail(principal, request.email(), request.emailCode());
    }

    /**
     * 用户登出接口，撤销当前访问令牌。
     *
     * @param request HTTP 请求，用于提取访问令牌
     */
    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        authService.logout(resolveBearerToken(request));
    }

    /**
     * 从请求头中提取 Bearer 令牌。
     *
     * @param request HTTP 请求
     * @return 令牌字符串，不存在时返回 null
     */
    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    /**
     * 获取客户端真实IP地址，支持 X-Forwarded-For 和 X-Real-IP 头。
     *
     * @param request HTTP 请求
     * @return 客户端IP地址
     */
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
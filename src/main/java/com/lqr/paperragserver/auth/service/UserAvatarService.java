package com.lqr.paperragserver.auth.service;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户头像服务，负责头像文件校验、上传和当前用户头像状态更新。
 */
public interface UserAvatarService {

    AuthService.CurrentUser uploadAvatar(SecurityUserPrincipal principal, MultipartFile file);
}
package com.lqr.paperragserver.auth.service;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户头像服务，负责头像文件校验、上传和当前用户头像状态更新。
 */
public interface UserAvatarService {

    /**
     * 上传用户头像，校验文件类型和大小后存储并更新用户资料。
     *
     * @param principal 当前用户主体
     * @param file 头像文件
     * @return 更新后的用户信息
     */
    AuthService.CurrentUser uploadAvatar(SecurityUserPrincipal principal, MultipartFile file);
}
package com.lqr.paperragserver.auth.impl;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysRoleMapper;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.auth.service.AuthService;
import com.lqr.paperragserver.auth.service.UserAvatarService;
import com.lqr.paperragserver.config.OssProperties;
import com.lqr.paperragserver.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 默认头像服务实现，后端代理上传头像到 OSS 并更新当前用户资料。
 */
@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarServiceImpl.class);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ObjectStorageService objectStorageService;
    private final OssProperties ossProperties;
    private final Tika tika;

    @Override
    @Transactional
    public AuthService.CurrentUser uploadAvatar(SecurityUserPrincipal principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件不能为空");
        }
        long maxBytes = ossProperties.avatarMaxSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件不能超过 " + ossProperties.avatarMaxSize());
        }

        String contentType = detectContentType(file);
        if (!ossProperties.allowedContentTypes().contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像仅支持 JPG、PNG 或 WebP 图片");
        }

        SysUser user = userMapper.selectById(principal.getId());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        String oldObjectKey = user.getAvatarObjectKey();
        String objectKey = buildObjectKey(user.getId(), contentType);

        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.putObject(objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取头像文件失败");
        }

        userMapper.updateAvatar(user.getId(), objectKey);
        deleteOldAvatarQuietly(oldObjectKey, objectKey);

        SysUser updated = userMapper.selectById(user.getId());
        List<String> roles = roleMapper.selectRoleCodesByUserId(updated.getId());
        return toCurrentUser(updated, roles);
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            String detected = tika.detect(inputStream, file.getOriginalFilename());
            if (detected == null || detected.isBlank()) {
                detected = file.getContentType();
            }
            return detected == null ? "" : detected.toLowerCase(Locale.ROOT);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取头像文件失败");
        }
    }

    private String buildObjectKey(UUID userId, String contentType) {
        OffsetDateTime now = OffsetDateTime.now();
        return String.join("/",
                ossProperties.avatarPrefix(),
                userId.toString(),
                YEAR.format(now),
                MONTH.format(now),
                UUID.randomUUID() + extension(contentType)
        );
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像图片类型不支持");
        };
    }

    private void deleteOldAvatarQuietly(String oldObjectKey, String newObjectKey) {
        if (oldObjectKey == null || oldObjectKey.isBlank() || oldObjectKey.equals(newObjectKey)) {
            return;
        }
        try {
            objectStorageService.deleteObject(oldObjectKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete old avatar object: {}", oldObjectKey, ex);
        }
    }

    private AuthService.CurrentUser toCurrentUser(SysUser user, List<String> roles) {
        return new AuthService.CurrentUser(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                objectStorageService.publicUrl(user.getAvatarObjectKey()),
                roles
        );
    }
}
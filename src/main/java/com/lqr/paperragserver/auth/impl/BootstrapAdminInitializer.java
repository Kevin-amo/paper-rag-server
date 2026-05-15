package com.lqr.paperragserver.auth.impl;

import com.lqr.paperragserver.auth.service.UserAdminService;
import com.lqr.paperragserver.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 在空用户库中按配置初始化第一个管理员。
 */
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final SecurityProperties securityProperties;
    private final UserAdminService userAdminService;

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin config = securityProperties.bootstrapAdmin();
        if (!config.enabled()) {
            return;
        }
        UserAdminService.PageResult users = userAdminService.listUsers(0, 1, null, null);
        if (users.total() > 0) {
            return;
        }
        if (config.password() == null || config.password().isBlank()) {
            log.warn("未配置 BOOTSTRAP_ADMIN_PASSWORD，跳过默认管理员初始化");
            return;
        }
        try {
            userAdminService.createUser(new UserAdminService.CreateUserCommand(
                    config.username(),
                    config.password(),
                    config.displayName(),
                    null,
                    List.of("ADMIN")
            ));
            log.info("默认管理员已初始化：{}", config.username());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                throw ex;
            }
        }
    }
}
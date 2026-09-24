package com.xqfx.requirements.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次部署时创建初始管理员账号，否则没有人能登录系统。
 *
 * <p>初始密码从环境变量 {@code ADMIN_INITIAL_PASSWORD} 读取；未配置时随机生成并打印到启动日志，
 * 由运维从日志中取出。无论哪种方式，该账号首次登录都会被强制要求修改密码。
 * users 表非空时本类不做任何事。
 */
@Configuration
class UserBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    @Bean
    ApplicationRunner createInitialAdmin(UserRepository repository,
                                         AuthService authService,
                                         @Value("${app.auth.admin-initial-password:}") String configuredPassword) {
        return args -> initialize(repository, authService, configuredPassword);
    }

    @Transactional
    void initialize(UserRepository repository, AuthService authService, String configuredPassword) {
        if (repository.count() > 0) {
            return;
        }
        var password = configuredPassword == null || configuredPassword.isBlank()
                ? randomPassword()
                : configuredPassword;
        var admin = new UserEntity("admin", authService.encodePassword(password), "系统管理员", null, UserRole.ADMIN);
        repository.save(admin);

        if (configuredPassword == null || configuredPassword.isBlank()) {
            log.warn("已创建初始管理员账号 admin，随机初始密码为：{}（仅本次启动打印，请立即登录并修改）", password);
        } else {
            log.info("已创建初始管理员账号 admin，初始密码取自 ADMIN_INITIAL_PASSWORD 配置");
        }
    }

    private static String randomPassword() {
        var alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
        var random = new java.security.SecureRandom();
        var builder = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}

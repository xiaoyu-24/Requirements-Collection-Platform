package com.xqfx.requirements.user;

import org.springframework.core.NamedThreadLocal;

/**
 * 当前请求的登录用户。由 {@link AuthInterceptor} 在请求开始时写入、结束时清除，
 * 业务代码通过 {@link #require()} 获取，不必层层传参。
 */
public final class CurrentUser {

    private static final ThreadLocal<UserEntity> HOLDER = new NamedThreadLocal<>("当前登录用户");

    private CurrentUser() {
    }

    static void set(UserEntity user) {
        HOLDER.set(user);
    }

    static void clear() {
        HOLDER.remove();
    }

    /** 获取当前登录用户；拦截器已保证受保护接口必然有值。 */
    public static UserEntity require() {
        var user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前请求没有登录用户");
        }
        return user;
    }

    public static UserEntity getOrNull() {
        return HOLDER.get();
    }
}

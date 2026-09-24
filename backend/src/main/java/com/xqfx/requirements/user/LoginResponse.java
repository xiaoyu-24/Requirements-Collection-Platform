package com.xqfx.requirements.user;

/** 登录成功后返回的令牌与用户信息。令牌明文只在此处返回一次。 */
public record LoginResponse(String token, UserResponse user) {
}

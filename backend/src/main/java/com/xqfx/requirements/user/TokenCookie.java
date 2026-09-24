package com.xqfx.requirements.user;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.Optional;

/**
 * 登录令牌的传输方式。
 *
 * <p>令牌放在 HttpOnly Cookie 中，而不是由前端 JavaScript 保存后手动加请求头。原因有两个：
 * 附件预览和下载走的是 {@code <img src>}、{@code <iframe src>}、{@code <a href>}，
 * 这类浏览器原生请求无法附加自定义请求头，只有 Cookie 能被自动带上；
 * 同时 HttpOnly 让前端脚本读不到令牌，即使页面存在 XSS 也无法把令牌取走。
 *
 * <p>SameSite=Lax 保证跨站发起的写操作不会携带该 Cookie，用于防范 CSRF。
 */
final class TokenCookie {

    static final String NAME = "xqfx_token";

    private TokenCookie() {
    }

    static Optional<String> read(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    static void write(HttpServletResponse response, String token, int maxAgeSeconds, boolean secure) {
        response.addHeader("Set-Cookie", buildHeader(token, maxAgeSeconds, secure));
    }

    static void clear(HttpServletResponse response, boolean secure) {
        response.addHeader("Set-Cookie", buildHeader("", 0, secure));
    }

    /**
     * 手动拼接 Set-Cookie，因为 Servlet 的 Cookie 类不支持 SameSite 属性。
     */
    private static String buildHeader(String value, int maxAgeSeconds, boolean secure) {
        var builder = new StringBuilder()
                .append(NAME).append('=').append(value)
                .append("; Path=/")
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (secure) {
            builder.append("; Secure");
        }
        return builder.toString();
    }
}

package com.xqfx.requirements.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入当前密码") String currentPassword,
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 64, message = "新密码长度需在 8 到 64 位之间") String newPassword) {
}

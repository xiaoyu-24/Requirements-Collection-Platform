package com.xqfx.requirements.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record UserSaveRequest(
        @NotBlank(message = "请输入账号")
        @Size(max = 50, message = "账号最长 50 位")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "账号只能包含字母、数字、点、下划线和短横线")
        String username,

        @NotBlank(message = "请输入姓名")
        @Size(max = 50, message = "姓名最长 50 位")
        String displayName,

        @Positive(message = "部门选择无效")
        Long departmentId,

        @NotNull(message = "请选择角色")
        UserRole role) {
}

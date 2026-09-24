package com.xqfx.requirements.user;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        Long departmentId,
        String department,
        UserRole role,
        boolean disabled,
        boolean mustChangePassword) {

    static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.department() == null ? null : user.department().id(),
                user.department() == null ? null : user.department().name(),
                user.role(),
                user.isDisabled(),
                user.mustChangePassword());
    }
}

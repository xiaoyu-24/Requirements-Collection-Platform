package com.xqfx.requirements.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 人员管理，仅管理员可访问。权限校验由拦截器统一处理。
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserService service;

    UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    List<UserResponse> listUsers() {
        return service.listUsers();
    }

    @GetMapping("/active")
    List<UserResponse> listActiveUsers() {
        return service.listActiveUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createUser(@Valid @RequestBody UserSaveRequest request) {
        var created = service.createUser(request);
        return Map.of("user", created.user(), "initialPassword", created.initialPassword());
    }

    @PutMapping("/{id}")
    UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        return service.updateUser(id, request);
    }

    @PostMapping("/{id}/reset-password")
    Map<String, String> resetPassword(@PathVariable Long id) {
        var newPassword = service.resetPassword(id);
        return Map.of("newPassword", newPassword);
    }

    @PatchMapping("/{id}/disabled")
    UserResponse updateDisabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        var disabled = body.getOrDefault("disabled", false);
        return service.updateDisabled(id, disabled);
    }
}

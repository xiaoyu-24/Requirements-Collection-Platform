package com.xqfx.requirements.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-versions")
class SystemVersionManagementController {

    private final SystemVersionService service;

    SystemVersionManagementController(SystemVersionService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    SystemVersionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    SystemVersionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateVersionRequest request) {
        return service.update(id, request.name(), request.description(), request.recordVersion());
    }

    @PatchMapping("/{id}/status")
    SystemVersionResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateVersionStatusRequest request) {
        return service.updateStatus(id, request.status(), request.recordVersion());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    record UpdateVersionRequest(@NotBlank String name, @Size(max = 2000) String description, @NotNull Long recordVersion) {
    }

    record UpdateVersionStatusRequest(@NotNull SystemVersionStatus status, @NotNull Long recordVersion) {
    }
}

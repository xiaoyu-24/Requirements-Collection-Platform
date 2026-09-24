package com.xqfx.requirements.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.xqfx.requirements.user.CurrentUser;

@RestController
@RequestMapping("/api/systems")
class SystemController {

    private final SystemService service;

    SystemController(SystemService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SystemResponse create(@Valid @RequestBody CreateSystemRequest request) {
        return service.create(request.name(), request.ownerUserId(), request.collaboratorUserIds());
    }

    @GetMapping
    List<SystemResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    SystemResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSystemRequest request) {
        return service.update(id, request.name(), request.ownerUserId(), request.collaboratorUserIds(), request.recordVersion());
    }

    @PatchMapping("/{id}/status")
    SystemResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return service.updateStatus(id, request.status(), request.recordVersion());
    }

    @PostMapping("/{id}/migrate")
    SystemMigrationResponse migrate(@PathVariable Long id, @RequestBody MigrateSystemRequest request) {
        return service.migrate(id, request.targetSystemId(), CurrentUser.require());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    record CreateSystemRequest(
            @NotBlank String name,
            @jakarta.validation.constraints.NotNull Long ownerUserId,
            List<Long> collaboratorUserIds
    ) {
        CreateSystemRequest {
            collaboratorUserIds = collaboratorUserIds == null ? List.of() : List.copyOf(collaboratorUserIds);
        }
    }

    record UpdateSystemRequest(
            @NotBlank String name,
            @jakarta.validation.constraints.NotNull Long ownerUserId,
            List<Long> collaboratorUserIds,
            @jakarta.validation.constraints.NotNull Long recordVersion
    ) {
        UpdateSystemRequest {
            collaboratorUserIds = collaboratorUserIds == null ? List.of() : List.copyOf(collaboratorUserIds);
        }
    }

    record UpdateStatusRequest(@jakarta.validation.constraints.NotNull SystemStatus status, @jakarta.validation.constraints.NotNull Long recordVersion) {
    }

    record MigrateSystemRequest(Long targetSystemId) {
    }
}

package com.xqfx.requirements.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/systems/{systemId}/versions")
class SystemVersionController {

    private final SystemVersionService service;

    SystemVersionController(SystemVersionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SystemVersionResponse create(@PathVariable Long systemId, @Valid @RequestBody CreateVersionRequest request) {
        return service.create(systemId, request.name(), request.description());
    }

    @GetMapping
    java.util.List<SystemVersionResponse> list(@PathVariable Long systemId) {
        return service.list(systemId);
    }

    record CreateVersionRequest(@NotBlank String name, @Size(max = 2000) String description) {
    }
}

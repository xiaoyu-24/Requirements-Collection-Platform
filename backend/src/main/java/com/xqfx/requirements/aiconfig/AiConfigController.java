package com.xqfx.requirements.aiconfig;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-config")
class AiConfigController {

    private final AiConfigService service;

    AiConfigController(AiConfigService service) {
        this.service = service;
    }

    @GetMapping
    List<AiConfigResponse> list() {
        return service.listConfigs();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AiConfigResponse create(@RequestBody AiConfigSaveRequest request) {
        return service.createConfig(request);
    }

    @PutMapping("/{id}")
    AiConfigResponse update(@PathVariable Long id, @RequestBody AiConfigSaveRequest request) {
        return service.updateConfig(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.deleteConfig(id);
    }

    @PostMapping("/{id}/activate")
    Map<String, Object> activate(@PathVariable Long id) {
        service.activateConfig(id);
        return Map.of("success", true);
    }

    @PostMapping("/{id}/test")
    Map<String, Object> test(@PathVariable Long id) {
        try {
            boolean success = service.testConnection(id);
            return Map.of("success", success);
        } catch (AiConfigService.AiConnectionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}

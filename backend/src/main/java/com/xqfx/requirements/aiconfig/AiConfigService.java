package com.xqfx.requirements.aiconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
class AiConfigService {

    private final AiConfigRepository repository;
    private final int requestTimeoutSeconds;

    AiConfigService(AiConfigRepository repository,
                    @Value("${app.ai.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.repository = repository;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    @Transactional(readOnly = true)
    List<AiConfigResponse> listConfigs() {
        return repository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    AiConfigResponse getConfigById(Long id) {
        var config = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        return toResponse(config);
    }

    @Transactional
    AiConfigResponse createConfig(AiConfigSaveRequest request) {
        var config = new AiConfigEntity(request.name() == null || request.name().isBlank() ? "新配置" : request.name().trim());
        config.update(config.name(), request.enabled(), blankToNull(request.serviceUrl()), blankToNull(request.modelName()));
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.updateApiKey(request.apiKey().trim());
        }
        repository.save(config);
        return toResponse(config);
    }

    @Transactional
    AiConfigResponse updateConfig(Long id, AiConfigSaveRequest request) {
        var config = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        config.update(request.name() == null || request.name().isBlank() ? config.name() : request.name().trim(),
                request.enabled(), blankToNull(request.serviceUrl()), blankToNull(request.modelName()));
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.updateApiKey(request.apiKey().trim());
        }
        repository.save(config);
        return toResponse(config);
    }

    @Transactional
    void activateConfig(Long id) {
        var config = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        repository.findByIsActiveTrue().ifPresent(active -> {
            active.deactivate();
            repository.save(active);
        });
        config.activate();
        repository.save(config);
    }

    @Transactional
    void deleteConfig(Long id) {
        var config = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        if (config.isActive()) {
            throw new IllegalArgumentException("不能删除当前激活的配置，请先切换激活其他配置");
        }
        repository.delete(config);
    }

    boolean testConnection(Long id) {
        var config = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        if (config.serviceUrl() == null || config.serviceUrl().isBlank()) {
            throw new IllegalArgumentException("请先配置服务地址");
        }
        try {
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.serviceUrl().replaceAll("/+$", "") + "/models"))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .GET();
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.apiKey());
            }
            var response = AiHttpClientFactory.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString(), requestTimeoutSeconds);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            throw new AiConnectionException("连接测试失败：" + e.getMessage());
        }
    }

    AiConfigEntity getActiveEntity() {
        return repository.findByIsActiveTrue().orElse(null);
    }

    String getApiKey(AiConfigEntity config) {
        return config == null ? null : config.apiKey();
    }

    private AiConfigResponse toResponse(AiConfigEntity config) {
        String mask = null;
        if (config.apiKey() != null) {
            mask = maskApiKey(config.apiKey());
        }
        return new AiConfigResponse(config.id(), config.name(), config.enabled(), config.isActive(),
                config.serviceUrl(), config.modelName(), mask);
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static class AiConnectionException extends RuntimeException {
        AiConnectionException(String message) {
            super(message);
        }
    }
}

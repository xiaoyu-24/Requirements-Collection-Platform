package com.xqfx.requirements.aiconfig;

record AiConfigResponse(Long id, String name, boolean enabled, boolean isActive, String serviceUrl, String modelName, String apiKeyMask) {
}

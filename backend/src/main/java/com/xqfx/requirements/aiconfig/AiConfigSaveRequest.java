package com.xqfx.requirements.aiconfig;

record AiConfigSaveRequest(String name, boolean enabled, String serviceUrl, String modelName, String apiKey) {
}

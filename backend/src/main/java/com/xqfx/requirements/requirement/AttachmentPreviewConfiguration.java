package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class AttachmentPreviewConfiguration {
    @Bean("attachmentPreviewTaskExecutor")
    ThreadPoolTaskExecutor attachmentPreviewTaskExecutor(@Value("${app.attachments.preview-concurrency:2}") int concurrency) {
        var size = Math.max(1, concurrency);
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("attachment-preview-");
        executor.initialize();
        return executor;
    }
}

package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
class AttachmentPreviewService {
    private final AttachmentRepository attachments;
    private final Path attachmentRoot;
    private final Path previewRoot;
    private final OfficePreviewGenerator generator;
    private final TaskExecutor executor;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    AttachmentPreviewService(AttachmentRepository attachments,
                             @Value("${app.attachments.root-directory:./uploads}") String attachmentRoot,
                             @Value("${app.attachments.preview-directory:./previews}") String previewRoot,
                             OfficePreviewGenerator generator,
                             @Qualifier("attachmentPreviewTaskExecutor") TaskExecutor executor) {
        this(attachments, Path.of(attachmentRoot).toAbsolutePath().normalize(), Path.of(previewRoot).toAbsolutePath().normalize(), generator, executor);
    }

    AttachmentPreviewService(AttachmentRepository attachments, Path attachmentRoot, Path previewRoot, OfficePreviewGenerator generator, TaskExecutor executor) {
        this.attachments = attachments;
        this.attachmentRoot = attachmentRoot.toAbsolutePath().normalize();
        this.previewRoot = previewRoot.toAbsolutePath().normalize();
        this.generator = generator;
        this.executor = executor;
    }

    void schedule(Long attachmentId) {
        if (!inFlight.add(attachmentId)) return;
        try {
            executor.execute(() -> {
                try { convert(attachmentId); }
                finally { inFlight.remove(attachmentId); }
            });
        } catch (RuntimeException exception) {
            inFlight.remove(attachmentId);
            throw exception;
        }
    }

    void requestRetry(Long attachmentId) {
        var attachment = findActive(attachmentId);
        if (attachment.previewStatus() == AttachmentPreviewStatus.DIRECT) return;
        attachment.requestPreviewRetry();
        attachments.save(attachment);
        schedule(attachmentId);
    }

    private void convert(Long attachmentId) {
        var attachment = findActive(attachmentId);
        if (attachment.previewStatus() != AttachmentPreviewStatus.PENDING && attachment.previewStatus() != AttachmentPreviewStatus.FAILED) return;
        attachment.beginPreviewConversion();
        attachments.save(attachment);
        Path target = previewPath(attachment);
        try {
            Files.createDirectories(previewRoot);
            if (!isValidPdf(target)) {
                generator.convert(sourcePath(attachment), target);
            }
            if (!isValidPdf(target)) throw new IOException("转换结果不是有效PDF");
            attachment.completePreviewConversion(previewRoot.relativize(target).toString());
            attachments.save(attachment);
        } catch (Exception exception) {
            deleteQuietly(target);
            attachment.failPreviewConversion(safeMessage(exception));
            attachments.save(attachment);
        }
    }

    private AttachmentEntity findActive(Long id) {
        return attachments.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
    }

    private Path sourcePath(AttachmentEntity attachment) {
        var path = attachmentRoot.resolve(attachment.storedName()).normalize();
        if (!path.startsWith(attachmentRoot) || !Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件文件不存在");
        return path;
    }

    private Path previewPath(AttachmentEntity attachment) {
        var path = previewRoot.resolve(attachment.checksum() + ".pdf").normalize();
        if (!path.startsWith(previewRoot)) throw new IllegalArgumentException("预览路径无效");
        return path;
    }

    private static boolean isValidPdf(Path path) {
        if (!Files.isRegularFile(path)) return false;
        try (var input = Files.newInputStream(path)) {
            return new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        } catch (IOException exception) {
            return false;
        }
    }

    private static String safeMessage(Exception exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank() ? "预览生成失败" : message;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}

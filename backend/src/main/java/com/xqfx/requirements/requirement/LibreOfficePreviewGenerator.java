package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

@Component
class LibreOfficePreviewGenerator implements OfficePreviewGenerator {
    private final String executable;
    private final Duration timeout;

    LibreOfficePreviewGenerator(@Value("${app.attachments.libreoffice-executable:libreoffice}") String executable,
                                @Value("${app.attachments.preview-timeout-seconds:60}") long timeoutSeconds) {
        this.executable = executable;
        this.timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 1));
    }

    @Override
    public void convert(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        var workDirectory = Files.createTempDirectory(target.getParent(), "office-preview-");
        try {
            var profile = workDirectory.resolve("profile");
            Files.createDirectories(profile);
            var process = new ProcessBuilder(
                    executable,
                    "-env:UserInstallation=" + profile.toUri(),
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", workDirectory.toString(),
                    source.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(workDirectory.resolve("conversion.log").toFile())
                    .start();
            boolean finished;
            try {
                finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Office预览转换被中断", exception);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Office预览转换超时");
            }
            if (process.exitValue() != 0) throw new IOException("LibreOffice转换失败，退出码：" + process.exitValue());
            var fileName = source.getFileName().toString();
            var dot = fileName.lastIndexOf('.');
            var converted = workDirectory.resolve((dot < 0 ? fileName : fileName.substring(0, dot)) + ".pdf");
            if (!Files.isRegularFile(converted)) throw new IOException("LibreOffice未生成PDF文件");
            try {
                Files.move(converted, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(converted, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            deleteTree(workDirectory);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}

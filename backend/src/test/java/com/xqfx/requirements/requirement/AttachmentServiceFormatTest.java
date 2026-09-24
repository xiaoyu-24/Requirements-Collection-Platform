package com.xqfx.requirements.requirement;

import com.xqfx.requirements.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentServiceFormatTest {

    @TempDir
    Path root;

    private AttachmentService service;
    private AttachmentRepository attachments;
    private UserEntity actor;

    @BeforeEach
    void setUp() {
        var requirements = mock(RequirementRepository.class);
        attachments = mock(AttachmentRepository.class);
        var requirement = mock(RequirementEntity.class);
        actor = mock(UserEntity.class);
        when(requirements.findByIdAndDeletedFalse(anyLong())).thenReturn(Optional.of(requirement));
        when(actor.isHandler()).thenReturn(true);
        when(attachments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AttachmentService(requirements, attachments, root.toString(), 0);
    }

    private AttachmentEntity upload(String filename, byte[] content) {
        var file = new MockMultipartFile("file", filename, "application/octet-stream", content);
        service.upload(1L, actor, file);
        var captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        org.mockito.Mockito.verify(attachments).save(captor.capture());
        return captor.getValue();
    }

    private static byte[] concat(byte[]... parts) {
        try (var output = new ByteArrayOutputStream()) {
            for (var part : parts) output.write(part);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] jpeg() { return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46}; }
    private static byte[] png() { return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D}; }
    private static byte[] webp() { return concat("RIFF".getBytes(), new byte[] {0x18, 0x00, 0x00, 0x00}, "WEBP".getBytes(), "VP8 ".getBytes()); }
    private static byte[] ole() { return new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0x00, 0x00}; }

    private static byte[] docx() {
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<doc/>".getBytes());
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void pngRenamedJpgIsAcceptedAndStoredAsPng() {
        var attachment = upload("1788069800213.jpg", png());
        assertTrue(attachment.storedName().endsWith(".png"));
        assertEquals("image/png", attachment.contentType());
        assertEquals("1788069800213.jpg", attachment.originalName());
    }

    @Test
    void webpRenamedJpgIsAcceptedAndStoredAsWebp() {
        var attachment = upload("photo.jpg", webp());
        assertTrue(attachment.storedName().endsWith(".webp"));
        assertEquals("image/webp", attachment.contentType());
    }

    @Test
    void realJpgIsAccepted() {
        var attachment = upload("photo.jpg", jpeg());
        assertTrue(attachment.storedName().endsWith(".jpg"));
        assertEquals("image/jpeg", attachment.contentType());
    }

    @Test
    void textDisguisedAsJpgIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> upload("notes.jpg", "hello world".getBytes()));
        assertTrue(exception.getMessage().contains("无法识别附件内容格式"));
    }

    @Test
    void emptyFileIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> upload("empty.jpg", new byte[0]));
        assertTrue(exception.getMessage().contains("无法识别附件内容格式"));
    }

    @Test
    void plainZipDisguisedAsDocxIsRejected() {
        var fakeZip = concat(new byte[] {0x50, 0x4B, 0x03, 0x04}, "not-a-real-zip".getBytes());
        var exception = assertThrows(IllegalArgumentException.class, () -> upload("archive.docx", fakeZip));
        assertTrue(exception.getMessage().contains("ZIP 压缩包"));
    }

    @Test
    void realDocxRenamedIsAccepted() {
        var attachment = upload("document.bin", docx());
        assertTrue(attachment.storedName().endsWith(".docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", attachment.contentType());
    }

    @Test
    void oleContentWithExecutableExtensionIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> upload("setup.exe", ole()));
        assertEquals("不支持的附件格式", exception.getMessage());
    }

    @Test
    void oleContentWithXlsExtensionIsAccepted() {
        var attachment = upload("sheet.xls", ole());
        assertTrue(attachment.storedName().endsWith(".xls"));
        assertEquals("application/vnd.ms-excel", attachment.contentType());
    }
}

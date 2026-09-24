package com.xqfx.requirements.requirement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;
import com.xqfx.requirements.user.UserEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;

@Service
class AttachmentService {
    private static final Set<String> OLE_EXTENSIONS = Set.of("doc", "xls");
    private final RequirementRepository requirements; private final AttachmentRepository attachments; private final Path root; private final Path previewRoot; private final long minimumFreeSpaceBytes; private final AttachmentPreviewService previewService;
    @Autowired AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, @Value("${app.attachments.root-directory:./uploads}") String rootDirectory, @Value("${app.attachments.minimum-free-space-bytes:0}") long minimumFreeSpaceBytes, @Value("${app.attachments.preview-directory:./previews}") String previewDirectory, AttachmentPreviewService previewService) { this(requirements,attachments,rootDirectory,minimumFreeSpaceBytes,Path.of(previewDirectory),previewService); }
    AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, String rootDirectory, long minimumFreeSpaceBytes) { this(requirements,attachments,rootDirectory,minimumFreeSpaceBytes,Path.of(rootDirectory).resolve("previews"),null); }
    private AttachmentService(RequirementRepository requirements, AttachmentRepository attachments, String rootDirectory, long minimumFreeSpaceBytes, Path previewDirectory, AttachmentPreviewService previewService) { this.requirements=requirements;this.attachments=attachments;this.root=Path.of(rootDirectory).toAbsolutePath().normalize();this.previewRoot=previewDirectory.toAbsolutePath().normalize();this.minimumFreeSpaceBytes=minimumFreeSpaceBytes;this.previewService=previewService; }
    AttachmentResponse upload(Long requirementId, UserEntity actor, MultipartFile file) {
        var requirement=requirements.findByIdAndDeletedFalse(requirementId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在"));
        assertCanEdit(requirement, actor);
        var originalName=file.getOriginalFilename()==null?"":Path.of(file.getOriginalFilename()).getFileName().toString();
        var extension=extension(originalName);
        Path temporary = null;
        Path target = null;
        boolean saved = false;
        try {
            Files.createDirectories(root);
            if (Files.getFileStore(root).getUsableSpace() - minimumFreeSpaceBytes < file.getSize()) throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,"附件目录剩余空间不足");
            temporary=Files.createTempFile(root,"upload-",".tmp");
            var checksum=copyAndChecksum(file.getInputStream(),temporary);
            var format=detectActualFormat(temporary);
            if (format == null) throw new IllegalArgumentException("无法识别附件内容格式，请确认文件完整或转换为受支持格式（PDF / Word / 图片等）");
            var storedExtension = format.extension();
            var storedContentType = format.contentType();
            if (storedExtension.equals("ole")) {
                if (!OLE_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("不支持的附件格式");
                storedExtension = extension;
                storedContentType = extension.equals("xls") ? "application/vnd.ms-excel" : "application/msword";
            } else if (storedExtension.equals("zip")) {
                throw new IllegalArgumentException("附件内容为 ZIP 压缩包，不在支持的格式内");
            }
            var storedName=UUID.randomUUID()+"."+storedExtension;
            target=root.resolve(storedName).normalize();
            if(!target.startsWith(root)) throw new IllegalArgumentException("附件路径无效");
            try {
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary,target);
            }
            temporary=null;
            var attachment = attachments.save(new AttachmentEntity(requirement,originalName,storedName,storedName,storedContentType,file.getSize(),checksum));
            saved = true;
            if(previewService!=null&&attachment.id()!=null&&attachment.previewStatus()==AttachmentPreviewStatus.PENDING) previewService.schedule(attachment.id());
            return AttachmentResponse.from(attachment);
        } catch (ResponseStatusException | IllegalArgumentException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"附件保存失败",exception);
        } finally {
            deleteQuietly(temporary);
            if (!saved) deleteQuietly(target);
        }
    }
    @Transactional(readOnly=true)
    java.util.List<AttachmentResponse> list(Long requirementId, UserEntity actor) {
        var requirement = requirements.findByIdAndDeletedFalse(requirementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"需求不存在"));
        assertCanView(requirement, actor);
        var activeAttachments = attachments.findByRequirementIdAndDeletedFalseOrderByIdAsc(requirementId);
        if (previewService != null) {
            activeAttachments.stream()
                    .filter(attachment -> attachment.previewStatus() == AttachmentPreviewStatus.PENDING)
                    .map(AttachmentEntity::id)
                    .filter(java.util.Objects::nonNull)
                    .forEach(previewService::schedule);
        }
        return activeAttachments.stream().map(AttachmentResponse::from).toList();
    }
    AttachmentFile download(Long id, UserEntity actor) { var attachment=findActive(id); assertCanView(attachment.requirement(), actor); var file=root.resolve(attachment.storedName()).normalize(); if(!file.startsWith(root)||!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件文件不存在"); return new AttachmentFile(new FileSystemResource(file),attachment.originalName(),attachment.contentType()); }
    AttachmentFile preview(Long id, UserEntity actor) { var attachment=findActive(id); assertCanView(attachment.requirement(), actor); Path file; if(attachment.previewStatus()==AttachmentPreviewStatus.DIRECT) file=root.resolve(attachment.storedName()).normalize(); else if(attachment.previewStatus()==AttachmentPreviewStatus.READY&&attachment.previewRelativePath()!=null) file=previewRoot.resolve(attachment.previewRelativePath()).normalize(); else throw new ResponseStatusException(HttpStatus.CONFLICT,"附件预览尚未生成"); var expectedRoot=attachment.previewStatus()==AttachmentPreviewStatus.DIRECT?root:previewRoot; if(!file.startsWith(expectedRoot)||!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"附件预览文件不存在"); return new AttachmentFile(new FileSystemResource(file),attachment.originalName(),attachment.previewContentType()); }
    AttachmentResponse retryPreview(Long id, UserEntity actor) { var attachment=findActive(id); assertCanEdit(attachment.requirement(), actor); if(attachment.previewStatus()!=AttachmentPreviewStatus.DIRECT){if(previewService!=null)previewService.requestRetry(id);else{attachment.requestPreviewRetry();attachments.save(attachment);}} return AttachmentResponse.from(findActive(id)); }
    @Transactional void delete(Long id, UserEntity actor) { var attachment=findActive(id); assertCanEdit(attachment.requirement(), actor); deleteQuietly(root.resolve(attachment.storedName()).normalize()); if(attachment.previewRelativePath()!=null){var preview=previewRoot.resolve(attachment.previewRelativePath()).normalize();if(preview.startsWith(previewRoot))deleteQuietly(preview);} attachment.delete(); }
    private AttachmentEntity findActive(Long id) { return attachments.findByIdAndDeletedFalse(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"附件不存在")); }
    private static void assertCanView(RequirementEntity requirement, UserEntity actor) { if (!actor.isHandler() && (requirement.requesterUser() == null || !requirement.requesterUser().id().equals(actor.id()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该需求附件"); }
    private static void assertCanEdit(RequirementEntity requirement, UserEntity actor) {
        assertCanView(requirement, actor);
        if (!actor.isHandler() && !requirement.isDraft()
                && requirement.status() != RequirementStatus.PENDING_EVALUATION
                && requirement.status() != RequirementStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通用户只能编辑待评估或已确认的需求");
        }
    }
    record AttachmentFile(Resource resource,String originalName,String contentType) { }
    private static String extension(String name) { var index=name.lastIndexOf('.'); return index<0?"":name.substring(index+1).toLowerCase(); }
    record ActualFormat(String extension, String contentType) { }
    private static ActualFormat detectActualFormat(Path file) {
        byte[] header;
        try (var input = Files.newInputStream(file)) {
            header = input.readNBytes(12);
        } catch (IOException exception) {
            return null;
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) return new ActualFormat("jpg", "image/jpeg");
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return new ActualFormat("png", "image/png");
        if (startsWith(header, "GIF87a") || startsWith(header, "GIF89a")) return new ActualFormat("gif", "image/gif");
        if (startsWith(header, "RIFF") && startsWith(header, 8, "WEBP")) return new ActualFormat("webp", "image/webp");
        if (startsWith(header, "%PDF-")) return new ActualFormat("pdf", "application/pdf");
        if (startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) return new ActualFormat("ole", null);
        if (startsWith(header, 0x50, 0x4B)) {
            if (hasZipDirectory(file, "word/")) return new ActualFormat("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            if (hasZipDirectory(file, "xl/")) return new ActualFormat("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            return new ActualFormat("zip", null);
        }
        return null;
    }
    private static boolean hasZipDirectory(Path file, String directory) {
        try (var zip = new ZipFile(file.toFile())) {
            return zip.stream().anyMatch(entry -> entry.getName().startsWith(directory));
        } catch (IOException exception) {
            return false;
        }
    }
    private static boolean startsWith(byte[] value, int... signature) { if (value.length < signature.length) return false; for (int index = 0; index < signature.length; index++) if ((value[index] & 0xFF) != signature[index]) return false; return true; }
    private static boolean startsWith(byte[] value, String signature) { return startsWith(value, 0, signature); }
    private static boolean startsWith(byte[] value, int offset, String signature) { var bytes = signature.getBytes(java.nio.charset.StandardCharsets.US_ASCII); if (value.length < offset + bytes.length) return false; for (int index = 0; index < bytes.length; index++) if (value[offset + index] != bytes[index]) return false; return true; }
    private static String copyAndChecksum(InputStream input, Path temporary) throws IOException { try (input; var output=Files.newOutputStream(temporary)) { var digest=MessageDigest.getInstance("SHA-256"); var buffer=new byte[8192]; for(int read;(read=input.read(buffer))!=-1;) { output.write(buffer,0,read); digest.update(buffer,0,read); } return java.util.HexFormat.of().formatHex(digest.digest()); } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用",exception); } }
    private static void deleteQuietly(Path path) { if(path==null)return; try { Files.deleteIfExists(path); } catch(IOException ignored) { } }
}

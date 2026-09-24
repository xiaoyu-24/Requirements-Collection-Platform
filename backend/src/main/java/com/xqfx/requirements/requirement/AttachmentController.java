package com.xqfx.requirements.requirement;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import com.xqfx.requirements.user.CurrentUser;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/attachments")
class AttachmentController {
    private final AttachmentService attachments;
    AttachmentController(AttachmentService attachments) { this.attachments=attachments; }
    @GetMapping("/{id}") ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) { var file=attachments.download(id, CurrentUser.require()); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header("Content-Disposition", ContentDisposition.attachment().filename(file.originalName(), StandardCharsets.UTF_8).build().toString()).body(file.resource()); }
    @GetMapping("/{id}/preview") ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable Long id) { var file=attachments.preview(id, CurrentUser.require()); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header("Content-Disposition", ContentDisposition.inline().filename(file.originalName(), StandardCharsets.UTF_8).build().toString()).header("X-Content-Type-Options","nosniff").body(file.resource()); }
    @PostMapping("/{id}/preview/retry") @ResponseStatus(HttpStatus.ACCEPTED) AttachmentResponse retryPreview(@PathVariable Long id) { return attachments.retryPreview(id, CurrentUser.require()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id) { attachments.delete(id, CurrentUser.require()); }
}

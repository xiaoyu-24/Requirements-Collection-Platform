package com.xqfx.requirements.requirement;

record AttachmentResponse(Long id, String originalName, String contentType, long sizeBytes, String checksum, AttachmentPreviewStatus previewStatus, boolean previewAvailable, String previewContentType, String previewErrorMessage) {
    static AttachmentResponse from(AttachmentEntity attachment) { var status=attachment.previewStatus(); return new AttachmentResponse(attachment.id(), attachment.originalName(), attachment.contentType(), attachment.sizeBytes(), attachment.checksum(), status, status==AttachmentPreviewStatus.DIRECT||status==AttachmentPreviewStatus.READY, attachment.previewContentType(), attachment.previewErrorMessage()); }
}

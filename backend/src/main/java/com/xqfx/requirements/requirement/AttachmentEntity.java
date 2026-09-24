package com.xqfx.requirements.requirement;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
class AttachmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private RequirementEntity requirement;
    @Column(nullable = false) private String originalName;
    @Column(nullable = false) private String storedName;
    @Column(nullable = false) private String relativePath;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String checksum;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AttachmentPreviewStatus previewStatus;
    private String previewRelativePath;
    private String previewContentType;
    private LocalDateTime previewGeneratedAt;
    @Column(length = 500) private String previewErrorMessage;
    @Column(nullable = false) private int previewRetryCount = 0;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private boolean deleted = false;
    private LocalDateTime deletedAt;
    protected AttachmentEntity() { }
    AttachmentEntity(RequirementEntity requirement, String originalName, String storedName, String relativePath, String contentType, long sizeBytes, String checksum) { this.requirement=requirement;this.originalName=originalName;this.storedName=storedName;this.relativePath=relativePath;this.contentType=contentType;this.sizeBytes=sizeBytes;this.checksum=checksum;this.previewStatus=isDirectlyPreviewable(contentType)?AttachmentPreviewStatus.DIRECT:AttachmentPreviewStatus.PENDING;this.previewContentType=isDirectlyPreviewable(contentType)?contentType:null;this.createdAt=LocalDateTime.now(); }
    Long id(){return id;} RequirementEntity requirement(){return requirement;} String originalName(){return originalName;} String storedName(){return storedName;} String contentType(){return contentType;} long sizeBytes(){return sizeBytes;} String checksum(){return checksum;} AttachmentPreviewStatus previewStatus(){return previewStatus;} String previewContentType(){return previewContentType;} String previewRelativePath(){return previewRelativePath;} String previewErrorMessage(){return previewErrorMessage;}
    void beginPreviewConversion(){if(previewStatus!=AttachmentPreviewStatus.PENDING&&previewStatus!=AttachmentPreviewStatus.FAILED)return;previewStatus=AttachmentPreviewStatus.CONVERTING;previewErrorMessage=null;}
    void completePreviewConversion(String relativePath){previewStatus=AttachmentPreviewStatus.READY;previewRelativePath=relativePath;previewContentType="application/pdf";previewGeneratedAt=LocalDateTime.now();previewErrorMessage=null;}
    void failPreviewConversion(String message){previewStatus=AttachmentPreviewStatus.FAILED;previewErrorMessage=message==null?"预览生成失败":message.substring(0,Math.min(message.length(),500));previewRetryCount++;}
    void requestPreviewRetry(){if(previewStatus!=AttachmentPreviewStatus.DIRECT){previewStatus=AttachmentPreviewStatus.PENDING;previewErrorMessage=null;}}
    void delete(){deleted=true;deletedAt=LocalDateTime.now();}
    private static boolean isDirectlyPreviewable(String contentType){return contentType.equals("application/pdf")||contentType.startsWith("image/");}
}

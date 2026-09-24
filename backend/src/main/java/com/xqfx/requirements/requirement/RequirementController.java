package com.xqfx.requirements.requirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.xqfx.requirements.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/requirements")
class RequirementController {

    private final RequirementService service;
    private final AttachmentService attachments;

    RequirementController(RequirementService service, AttachmentService attachments) {
        this.service = service;
        this.attachments = attachments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RequirementResponse create(@Valid @RequestBody CreateRequirementRequest request) {
        return service.create(CurrentUser.require(), request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.periodStartDate(),
                request.periodEndDate(), request.urgency(), request.newSystem() == null ? null : request.newSystem().name(),
                request.newSystem() == null ? null : request.newSystem().ownerUserId(),
                request.newSystem() == null ? null : request.newSystem().collaboratorUserIds());
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    RequirementResponse createDraft(@RequestBody DraftRequirementRequest request) {
        return service.createDraft(CurrentUser.require(), request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.periodStartDate(),
                request.periodEndDate(), request.urgency());
    }

    @GetMapping
    List<RequirementResponse> list(@RequestParam(required = false) Long typeId,
                                   @RequestParam(required = false) Long systemId,
                                   @RequestParam(required = false) RequirementSaveType saveType) {
        return service.list(CurrentUser.require(), typeId, systemId, saveType);
    }

    @GetMapping("/page")
    RequirementPageResponse page(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) Long systemId,
                                 @RequestParam(defaultValue = "false") boolean unassignedSystem,
                                 @RequestParam(required = false) Long targetVersionId,
                                 @RequestParam(required = false) Long departmentId,
                                 @RequestParam(required = false) String requesterName,
                                 @RequestParam(required = false) Long typeId,
                                 @RequestParam(required = false) RequirementStatus status,
                                 @RequestParam(required = false) RequirementUrgency urgency,
                                 @RequestParam(required = false) RequirementSaveType saveType,
                                 @RequestParam(defaultValue = "false") boolean unfinishedOnly,
                                 @RequestParam(defaultValue = "false") boolean inProgressOnly,
                                 @RequestParam(required = false) String responsible,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String sortBy,
                                 @RequestParam(required = false) String sortDirection) {
        return service.page(CurrentUser.require(), page, size, systemId, unassignedSystem, targetVersionId, departmentId, requesterName,
                typeId, status, urgency, saveType, unfinishedOnly, inProgressOnly, responsible, keyword, sortBy, sortDirection);
    }

    @GetMapping("/responsibles")
    List<RequirementResponsibleCount> responsibles() {
        return service.responsibles(CurrentUser.require());
    }

    @GetMapping("/{id:\\d+}")
    RequirementResponse get(@PathVariable Long id) {
        return service.get(id, CurrentUser.require());
    }

    @GetMapping("/{id:\\d+}/attachments")
    List<AttachmentResponse> attachments(@PathVariable Long id) {
        return attachments.list(id, CurrentUser.require());
    }

    @GetMapping("/{id:\\d+}/progresses")
    List<RequirementProgressResponse> progresses(@PathVariable Long id) {
        return service.progresses(id, CurrentUser.require());
    }

    @PostMapping("/{id:\\d+}/progresses")
    @ResponseStatus(HttpStatus.CREATED)
    RequirementProgressResponse addProgress(@PathVariable Long id,
                                            @Valid @RequestBody CreateProgressRequest request) {
        return service.addProgress(id, CurrentUser.require(), request.content(), request.status(), request.recordVersion());
    }

    @PatchMapping("/{id:\\d+}/assignee")
    RequirementResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequirementRequest request) {
        return service.assign(id, request.assigneeUserId(), request.recordVersion(), CurrentUser.require());
    }

    @PutMapping("/{id:\\d+}/draft")
    RequirementResponse updateDraft(@PathVariable Long id, @RequestBody DraftRequirementRequest request) {
        return service.updateDraft(id, CurrentUser.require(), request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.periodStartDate(),
                request.periodEndDate(), request.urgency(), request.recordVersion());
    }

    @PutMapping("/{id:\\d+}")
    RequirementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequirementRequest request) {
        return service.update(id, CurrentUser.require(), request.requesterName(), request.departmentId(), request.title(), request.typeId(),
                request.content(), request.systemId(), request.periodStartDate(),
                request.periodEndDate(), request.urgency(), request.recordVersion());
    }

    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.require());
    }

    @PostMapping("/{id:\\d+}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentResponse upload(@PathVariable Long id,
                              @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return attachments.upload(id, CurrentUser.require(), file);
    }

    record CreateProgressRequest(@NotBlank(message = "请输入进展内容") String content, RequirementStatus status,
                                 @NotNull Long recordVersion) {
    }

    record AssignRequirementRequest(Long assigneeUserId, @NotNull Long recordVersion) {
    }

    record UpdateRequirementRequest(@NotBlank(message = "请输入姓名") String requesterName, @NotNull(message = "请选择部门") Long departmentId,
                                    @NotBlank(message = "请输入需求标题") String title, @NotNull(message = "请选择需求类型") Long typeId, @NotBlank(message = "请输入需求内容") String content,
                                    Long systemId, LocalDate periodStartDate,
                                    LocalDate periodEndDate, @NotNull(message = "请选择紧急程度") RequirementUrgency urgency,
                                    @NotNull Long recordVersion) {
    }

    record CreateRequirementRequest(@NotBlank(message = "请输入姓名") String requesterName, @NotNull(message = "请选择部门") Long departmentId,
                                    @NotBlank(message = "请输入需求标题") String title, @NotNull(message = "请选择需求类型") Long typeId, @NotBlank(message = "请输入需求内容") String content,
                                    Long systemId, LocalDate periodStartDate,
                                    LocalDate periodEndDate, @NotNull(message = "请选择紧急程度") RequirementUrgency urgency,
                                    NewSystemRequest newSystem) {
    }

    record DraftRequirementRequest(String requesterName, Long departmentId, String title, Long typeId,
                                   String content, Long systemId, LocalDate periodStartDate,
                                   LocalDate periodEndDate, RequirementUrgency urgency, Long recordVersion) {
    }

    record NewSystemRequest(String name, Long ownerUserId, List<Long> collaboratorUserIds) {
        NewSystemRequest {
            collaboratorUserIds = collaboratorUserIds == null ? List.of() : List.copyOf(collaboratorUserIds);
        }
    }
}

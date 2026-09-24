package com.xqfx.requirements.requirement;

import com.xqfx.requirements.dictionary.DictionaryCategory;
import com.xqfx.requirements.dictionary.DictionaryItemEntity;
import com.xqfx.requirements.dictionary.DictionaryService;
import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemRepository;
import com.xqfx.requirements.system.SystemService;
import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.system.SystemVersionRepository;
import com.xqfx.requirements.user.UserEntity;
import com.xqfx.requirements.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
class RequirementService {

    private final RequirementRepository requirements;
    private final SystemRepository systems;
    private final SystemService systemService;
    private final SystemVersionRepository versions;
    private final DictionaryService dictionaries;
    private final RequirementProgressRepository progresses;
    private final UserRepository users;
    private final RequirementNotificationService notifications;
    private final RequirementVersionChangeRepository versionChanges;

    RequirementService(RequirementRepository requirements, SystemRepository systems, SystemService systemService,
                       SystemVersionRepository versions, DictionaryService dictionaries,
                       RequirementProgressRepository progresses, UserRepository users,
                       RequirementNotificationService notifications,
                       RequirementVersionChangeRepository versionChanges) {
        this.requirements = requirements;
        this.systems = systems;
        this.systemService = systemService;
        this.versions = versions;
        this.dictionaries = dictionaries;
        this.progresses = progresses;
        this.users = users;
        this.notifications = notifications;
        this.versionChanges = versionChanges;
    }

    @Transactional
    RequirementResponse create(UserEntity requester, String requesterName, Long departmentId, String title, Long typeId, String content,
                               Long systemId, LocalDate start, LocalDate end,
                               RequirementUrgency urgency,
                               String newSystemName, Long newSystemOwnerUserId,
                               List<Long> newSystemCollaboratorUserIds) {
        var department = requester.department() == null
                ? dictionaries.requireActive(departmentId, DictionaryCategory.DEPARTMENT, "部门")
                : requester.department();
        var type = dictionaries.requireActive(typeId, DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        if (systemId != null && newSystemName != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能同时选择已有系统和新系统");
        }
        if (newSystemName != null && requester.role() == com.xqfx.requirements.user.UserRole.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通用户不能创建系统");
        }

        var system = newSystemName == null
                ? (systemId == null ? null : findSystem(systemId))
                : createNewSystem(newSystemName, newSystemOwnerUserId, newSystemCollaboratorUserIds);
        if (system != null && !system.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统已停用，不能新建需求");
        }

        var saved = requirements.save(new RequirementEntity(requester, normalizeRequesterName(requesterName, requester), department, title, type, content,
                system, null, RequirementPeriod.of(start, end), urgency));
        notifications.onRequirementSubmitted(saved, requester);
        return RequirementResponse.from(saved);
    }

    @Transactional
    RequirementResponse createDraft(UserEntity requester, String requesterName, Long departmentId, String title, Long typeId,
                                    String content, Long systemId,
                                    LocalDate start, LocalDate end, RequirementUrgency urgency) {
        var department = requester.department() == null
                ? resolveOptionalActive(departmentId, DictionaryCategory.DEPARTMENT, "部门")
                : requester.department();
        var type = resolveOptionalActive(typeId, DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        var system = systemId == null ? null : findSystem(systemId);
        return RequirementResponse.from(requirements.save(RequirementEntity.draft(requester, normalizeRequesterName(requesterName, requester), department, title,
                type, content, system, null, RequirementPeriod.of(start, end), urgency)));
    }

    @Transactional(readOnly = true)
    List<RequirementResponse> list(UserEntity actor, Long typeId, Long systemId, RequirementSaveType saveType) {
        var items = saveType != null
                ? requirements.findBySaveTypeAndDeletedFalse(saveType)
                : (systemId != null
                        ? requirements.findBySystemIdAndDeletedFalse(systemId)
                        : (typeId != null
                                ? requirements.findByType_IdAndDeletedFalse(typeId)
                                : requirements.findAllByDeletedFalse()));
        return items.stream()
                .filter(item -> actor.isHandler() || owns(item, actor))
                .map(RequirementResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    RequirementPageResponse page(UserEntity actor, int page, int size, Long systemId, boolean unassignedSystem,
                                 Long targetVersionId, Long departmentId, String requesterName, Long typeId,
                                 RequirementStatus status, RequirementUrgency urgency, RequirementSaveType saveType,
                                 boolean unfinishedOnly, boolean inProgressOnly, String responsible, String keyword,
                                 String sortBy, String sortDirection) {
        validatePageFilter(page, size, systemId, unassignedSystem);
        return RequirementPageResponse.from(requirements.findAll(
                RequirementSpecifications.filtered(systemId, unassignedSystem, targetVersionId, departmentId,
                        requesterName, typeId, status, urgency, saveType, keyword, unfinishedOnly, inProgressOnly,
                        responsible, sortBy, sortDirection,
                        actor.isHandler() ? null : actor.id()),
                PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    List<RequirementResponsibleCount> responsibles(UserEntity actor) {
        return requirements.countRequirementsByResponsible(false, RequirementSaveType.SUBMITTED,
                        RequirementSpecifications.unfinishedStatuses(), actor.isHandler() ? null : actor.id()).stream()
                .map(RequirementResponsibleCount::from)
                .toList();
    }

    @Transactional(readOnly = true)
    RequirementResponse get(Long id, UserEntity actor) {
        var requirement = findActive(id);
        assertCanView(requirement, actor);
        return RequirementResponse.from(requirement);
    }

    @Transactional(readOnly = true)
    List<RequirementProgressResponse> progresses(Long id, UserEntity actor) {
        var requirement = findActive(id);
        assertCanView(requirement, actor);
        return progresses.findByRequirement_IdOrderByCreatedAtAscIdAsc(id).stream()
                .map(RequirementProgressResponse::from)
                .toList();
    }

    @Transactional
    RequirementProgressResponse addProgress(Long id, UserEntity author, String content, RequirementStatus status, Long recordVersion) {
        assertCanHandle(author);
        var requirement = findActive(id);
        var statusChanged = status != null && status != requirement.status();
        if (statusChanged) {
            assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
            requirement.updateStatusFromProgress(status);
            requirements.flush();
        }
        var progress = progresses.save(new RequirementProgressEntity(
                requirement, author.id(), author.displayName(), content.trim(), status));
        notifications.onProgressAdded(requirement, progress, statusChanged, author);
        return RequirementProgressResponse.from(progress);
    }

    @Transactional
    RequirementResponse updateDraft(Long id, UserEntity actor, String requesterName, Long departmentId, String title, Long typeId,
                                    String content, Long systemId,
                                    LocalDate start, LocalDate end, RequirementUrgency urgency, Long recordVersion) {
        var requirement = findActive(id);
        assertCanEdit(requirement, actor);
        assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
        if (!requirement.isDraft()) {
            throw new IllegalArgumentException("只有草稿可以暂存更新");
        }
        var department = resolveForUpdate(departmentId, requirement.department(),
                DictionaryCategory.DEPARTMENT, "部门");
        var type = resolveForUpdate(typeId, requirement.type(),
                DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        var system = systemId == null ? null : findSystem(systemId);
        var previousVersion = requirement.targetVersion();
        requirement.updateDraft(requesterName, department, title, type, content, system,
                RequirementPeriod.of(start, end), urgency);
        recordAutomaticUnbind(requirement, previousVersion, actor);
        requirements.flush();
        return RequirementResponse.from(requirement);
    }

    @Transactional
    RequirementResponse update(Long id, UserEntity actor, String requesterName, Long departmentId, String title, Long typeId,
                               String content, Long systemId,
                               LocalDate start, LocalDate end, RequirementUrgency urgency, Long recordVersion) {
        var requirement = findActive(id);
        assertCanEdit(requirement, actor);
        var submittingDraft = requirement.isDraft();
        assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
        var department = resolveForUpdate(departmentId, requirement.department(),
                DictionaryCategory.DEPARTMENT, "部门");
        var type = resolveForUpdate(typeId, requirement.type(),
                DictionaryCategory.REQUIREMENT_TYPE, "需求类型");
        var system = systemId == null ? null : findSystem(systemId);
        if (system != null && !system.isActive()
                && (requirement.system() == null || !system.id().equals(requirement.system().id()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统已停用，不能主动更换");
        }
        var previousVersion = requirement.targetVersion();
        requirement.update(requesterName, department, title, type, content, system,
                RequirementPeriod.of(start, end), urgency);
        recordAutomaticUnbind(requirement, previousVersion, actor);
        if (submittingDraft) {
            requirement.linkRequesterUserIfMissing(actor);
        }
        requirements.flush();
        if (submittingDraft) {
            notifications.onRequirementSubmitted(requirement, actor);
        }
        return RequirementResponse.from(requirement);
    }

    @Transactional
    RequirementResponse assign(Long id, Long assigneeUserId, Long recordVersion, UserEntity actor) {
        assertCanHandle(actor);
        var requirement = findActive(id);
        assertRecordVersion(requirement.recordVersion(), recordVersion, "需求已被其他人修改，请刷新后重试");
        var assignee = assigneeUserId == null ? null : findEnabledUser(assigneeUserId);
        var currentAssignee = requirement.assignee();
        if (Objects.equals(currentAssignee == null ? null : currentAssignee.id(), assignee == null ? null : assignee.id())) {
            return RequirementResponse.from(requirement);
        }
        requirement.assign(assignee);
        requirements.flush();
        if (assignee != null) {
            notifications.onAssigneeChanged(requirement, actor);
        }
        return RequirementResponse.from(requirement);
    }

    @Transactional
    void delete(Long id, UserEntity actor) {
        var requirement = findActive(id);
        assertCanEdit(requirement, actor);
        if (!actor.isHandler() && !requirement.isDraft() && requirement.status() != RequirementStatus.PENDING_EVALUATION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通用户只能删除草稿或待评估需求");
        }
        requirement.delete();
    }

    private DictionaryItemEntity resolveOptionalActive(Long id, DictionaryCategory category, String label) {
        return id == null ? null : dictionaries.requireActive(id, category, label);
    }

    /**
     * A disabled item may remain on an old record. It cannot be selected for a different value.
     */
    private DictionaryItemEntity resolveForUpdate(Long requestedId, DictionaryItemEntity current,
                                                  DictionaryCategory category, String label) {
        if (requestedId == null) {
            return null;
        }
        if (current != null && current.id().equals(requestedId)) {
            return current;
        }
        return dictionaries.requireActive(requestedId, category, label);
    }

    private RequirementEntity findActive(Long id) {
        return requirements.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
    }

    private SystemEntity findSystem(Long id) {
        return systems.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统不存在"));
    }

    private SystemVersionEntity findVersion(Long id) {
        return versions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
    }

    private UserEntity findEnabledUser(Long id) {
        var user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户已停用，不能指派");
        }
        if (!user.isHandler()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需求处理人必须是需求处理员或管理员");
        }
        return user;
    }

    private static boolean owns(RequirementEntity requirement, UserEntity actor) {
        return requirement.requesterUser() != null && requirement.requesterUser().id().equals(actor.id());
    }

    private static void assertCanView(RequirementEntity requirement, UserEntity actor) {
        if (!actor.isHandler() && !owns(requirement, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该需求");
        }
    }

    private static void assertCanEdit(RequirementEntity requirement, UserEntity actor) {
        if (!actor.isHandler() && !owns(requirement, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能编辑自己填写的需求");
        }
        if (!actor.isHandler() && !requirement.isDraft()
                && requirement.status() != RequirementStatus.PENDING_EVALUATION
                && requirement.status() != RequirementStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "普通用户只能编辑待评估或已确认的需求");
        }
    }

    private static void assertCanHandle(UserEntity actor) {
        if (!actor.isHandler()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有需求处理员或管理员可以执行该操作");
        }
    }

    private SystemEntity createNewSystem(String name, Long ownerUserId, List<Long> collaboratorUserIds) {
        return systemService.createBoundEntity(name, ownerUserId, collaboratorUserIds);
    }

    private static String normalizeRequesterName(String requesterName, UserEntity requester) {
        return requesterName == null || requesterName.isBlank() ? requester.displayName() : requesterName.trim();
    }

    private static void assertVersionBelongsToSystem(SystemVersionEntity version, SystemEntity system) {
        if (version != null && (system == null || !version.system().id().equals(system.id()))) {
            throw new IllegalArgumentException("目标版本不属于所属系统");
        }
    }

    private void recordAutomaticUnbind(RequirementEntity requirement, SystemVersionEntity previousVersion, UserEntity actor) {
        if (previousVersion != null && requirement.targetVersion() == null) {
            versionChanges.save(new RequirementVersionChangeEntity(
                    requirement, previousVersion, null, RequirementVersionChangeAction.UNBIND, actor));
        }
    }

    private static void validatePageFilter(int page, int size, Long systemId, boolean unassignedSystem) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        if (systemId != null && unassignedSystem) {
            throw new IllegalArgumentException("不能同时筛选具体系统和暂无系统");
        }
    }

    private static void assertRecordVersion(long currentVersion, Long requestVersion, String message) {
        if (requestVersion == null || requestVersion != currentVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }
}

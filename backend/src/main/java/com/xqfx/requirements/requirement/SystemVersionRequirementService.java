package com.xqfx.requirements.requirement;

import com.xqfx.requirements.system.SystemVersionRepository;
import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.user.UserEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class SystemVersionRequirementService {

    private final RequirementRepository requirements;
    private final SystemVersionRepository versions;
    private final RequirementVersionChangeRepository changes;

    SystemVersionRequirementService(RequirementRepository requirements, SystemVersionRepository versions,
                                    RequirementVersionChangeRepository changes) {
        this.requirements = requirements;
        this.versions = versions;
        this.changes = changes;
    }

    @Transactional(readOnly = true)
    RequirementPageResponse listCandidates(Long versionId, int page, int size, String keyword,
                                           RequirementStatus status, RequirementUrgency urgency, String source) {
        validatePage(page, size);
        var version = versions.findByIdAndDeletedFalse(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
        return RequirementPageResponse.from(requirements.findAll(
                RequirementSpecifications.versionCandidates(
                        version.system().id(), version.id(), keyword, status, urgency, source),
                PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    RequirementPageResponse listCurrent(Long versionId, int page, int size, String keyword,
                                        RequirementStatus status, RequirementUrgency urgency) {
        validatePage(page, size);
        var version = findVersion(versionId);
        return RequirementPageResponse.from(requirements.findAll(
                RequirementSpecifications.versionCurrent(version.id(), keyword, status, urgency),
                PageRequest.of(page, size)));
    }

    @Transactional
    VersionRequirementBatchResponse bind(Long versionId, List<VersionRequirementSelection> selections, UserEntity actor) {
        assertHandler(actor);
        var targetVersion = findVersion(versionId);
        if (!targetVersion.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "版本已停用，不能纳入需求");
        }
        var pendingChanges = validateSelections(targetVersion, selections, false);
        var boundCount = 0;
        var migratedCount = 0;
        for (var pending : pendingChanges) {
            var action = pending.fromVersion() == null
                    ? RequirementVersionChangeAction.BIND
                    : RequirementVersionChangeAction.MIGRATE;
            if (action == RequirementVersionChangeAction.BIND) boundCount++;
            else migratedCount++;
            pending.requirement().changeTargetVersion(targetVersion);
            changes.save(new RequirementVersionChangeEntity(
                    pending.requirement(), pending.fromVersion(), targetVersion, action, actor));
        }
        requirements.flush();
        changes.flush();
        return VersionRequirementBatchResponse.bound(boundCount, migratedCount);
    }

    @Transactional
    VersionRequirementBatchResponse unbind(Long versionId, List<VersionRequirementSelection> selections, UserEntity actor) {
        assertHandler(actor);
        var currentVersion = findVersion(versionId);
        var pendingChanges = validateSelections(currentVersion, selections, true);
        for (var pending : pendingChanges) {
            pending.requirement().changeTargetVersion(null);
            changes.save(new RequirementVersionChangeEntity(
                    pending.requirement(), currentVersion, null, RequirementVersionChangeAction.UNBIND, actor));
        }
        requirements.flush();
        changes.flush();
        return VersionRequirementBatchResponse.unbound(pendingChanges.size());
    }

    @Transactional(readOnly = true)
    List<RequirementVersionChangeResponse> history(Long requirementId, UserEntity actor) {
        var requirement = requirements.findByIdAndDeletedFalse(requirementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
        if (!actor.isHandler() && (requirement.requesterUser() == null
                || !requirement.requesterUser().id().equals(actor.id()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该需求");
        }
        return changes.findByRequirement_IdOrderByCreatedAtAscIdAsc(requirementId).stream()
                .map(RequirementVersionChangeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    VersionRequirementSummaryResponse summary(Long versionId) {
        var version = findVersion(versionId);
        var systemId = version.system().id();
        return new VersionRequirementSummaryResponse(
                requirements.count(RequirementSpecifications.versionCurrent(versionId, null, null, null)),
                requirements.count(RequirementSpecifications.versionCandidates(systemId, versionId, null, null, null, null)),
                requirements.count(RequirementSpecifications.versionCandidates(systemId, versionId, null, null, null, "UNASSIGNED")),
                requirements.count(RequirementSpecifications.versionCandidates(systemId, versionId, null, null, null, "OTHER_VERSION")),
                requirements.count(RequirementSpecifications.versionCandidates(systemId, versionId, null, null, RequirementUrgency.HIGH, null)));
    }

    @Transactional
    public int migrateSystem(SystemEntity sourceSystem, SystemEntity targetSystem, UserEntity actor) {
        assertHandler(actor);
        var affected = requirements.findBySystemIdAndDeletedFalse(sourceSystem.id());
        for (var requirement : affected) {
            var previousVersion = requirement.targetVersion();
            requirement.migrateSystem(targetSystem);
            if (previousVersion != null) {
                changes.save(new RequirementVersionChangeEntity(
                        requirement, previousVersion, null, RequirementVersionChangeAction.UNBIND, actor));
            }
        }
        requirements.flush();
        changes.flush();
        return affected.size();
    }

    private List<PendingVersionChange> validateSelections(SystemVersionEntity targetVersion,
                                                           List<VersionRequirementSelection> selections,
                                                           boolean unbind) {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一条需求");
        }
        var ids = new HashSet<Long>();
        var pending = new ArrayList<PendingVersionChange>();
        for (var selection : selections) {
            if (selection == null || selection.id() == null || !ids.add(selection.id())) {
                throw new IllegalArgumentException("需求选择无效或重复");
            }
            var requirement = requirements.findByIdAndDeletedFalse(selection.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
            if (selection.recordVersion() == null || requirement.recordVersion() != selection.recordVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "需求已发生变化，请刷新后重试");
            }
            if (requirement.isDraft()) {
                throw new IllegalArgumentException("草稿不能纳入版本");
            }
            if (!isManageable(requirement.status())) {
                throw new IllegalArgumentException("终态需求不能迁移或解除");
            }
            if (requirement.system() == null || !requirement.system().id().equals(targetVersion.system().id())) {
                throw new IllegalArgumentException("需求与版本不属于同一系统");
            }
            var fromVersion = requirement.targetVersion();
            if (unbind && (fromVersion == null || !fromVersion.id().equals(targetVersion.id()))) {
                throw new IllegalArgumentException("需求不属于当前版本");
            }
            if (!unbind && fromVersion != null && fromVersion.id().equals(targetVersion.id())) {
                throw new IllegalArgumentException("需求已经属于当前版本");
            }
            pending.add(new PendingVersionChange(requirement, fromVersion));
        }
        return pending;
    }

    private SystemVersionEntity findVersion(Long id) {
        return versions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
    }

    private static boolean isManageable(RequirementStatus status) {
        return status == RequirementStatus.PENDING_EVALUATION
                || status == RequirementStatus.CONFIRMED
                || status == RequirementStatus.IN_DEVELOPMENT
                || status == RequirementStatus.PAUSED;
    }

    private static void assertHandler(UserEntity actor) {
        if (actor == null || !actor.isHandler()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有需求处理员或管理员可以执行该操作");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
    }

    private record PendingVersionChange(RequirementEntity requirement, SystemVersionEntity fromVersion) {
    }
}

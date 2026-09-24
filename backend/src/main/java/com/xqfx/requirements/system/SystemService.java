package com.xqfx.requirements.system;

import com.xqfx.requirements.requirement.RequirementRepository;
import com.xqfx.requirements.user.UserEntity;
import com.xqfx.requirements.user.UserRepository;
import com.xqfx.requirements.requirement.SystemVersionRequirementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SystemService {

    private final SystemRepository repository;
    private final SystemVersionRepository versions;
    private final RequirementRepository requirements;
    private final UserRepository users;
    private final SystemVersionRequirementService versionRequirements;

    SystemService(SystemRepository repository, SystemVersionRepository versions, RequirementRepository requirements,
                  UserRepository users, SystemVersionRequirementService versionRequirements) {
        this.repository = repository;
        this.versions = versions;
        this.requirements = requirements;
        this.users = users;
        this.versionRequirements = versionRequirements;
    }

    @Transactional
    SystemResponse create(String name, Long ownerUserId, List<Long> collaboratorUserIds) {
        var saved = createBoundEntity(name, ownerUserId, collaboratorUserIds);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    List<SystemResponse> list() {
        return repository.findAllByDeletedFalse().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    SystemResponse update(Long id, String name, Long ownerUserId, List<Long> collaboratorUserIds, Long recordVersion) {
        var system = findActive(id);
        assertRecordVersion(system.recordVersion(), recordVersion, "系统已被其他人修改，请刷新后重试");
        var normalizedName = requireSystemName(name);
        if (repository.existsByActiveNameKeyAndIdNot(SystemEntity.normalizedName(normalizedName), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统名称已存在");
        }
        var owner = requireEnabledUser(ownerUserId, "系统负责人");
        var collaborators = requireEnabledCollaborators(collaboratorUserIds, owner);
        system.update(normalizedName, owner, collaborators);
        return toResponse(system);
    }

    /** 供填写需求时同步创建新系统，复用系统管理的账号绑定校验。 */
    @Transactional
    public SystemEntity createBoundEntity(String name, Long ownerUserId, List<Long> collaboratorUserIds) {
        var normalizedName = requireSystemName(name);
        if (repository.existsByActiveNameKey(SystemEntity.normalizedName(normalizedName))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统名称已存在");
        }
        var owner = requireEnabledUser(ownerUserId, "系统负责人");
        var collaborators = requireEnabledCollaborators(collaboratorUserIds, owner);
        return repository.saveAndFlush(new SystemEntity(normalizedName, owner, collaborators));
    }

    @Transactional
    SystemResponse updateStatus(Long id, SystemStatus status, Long recordVersion) {
        var system = findActive(id);
        assertRecordVersion(system.recordVersion(), recordVersion, "系统已被其他人修改，请刷新后重试");
        system.updateStatus(status);
        return toResponse(system);
    }

    @Transactional
    void delete(Long id) {
        var system = findActive(id);
        if (requirements.countBySystemIdAndDeletedFalse(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统存在关联需求，无法删除");
        }
        system.delete();
    }

    @Transactional
    SystemMigrationResponse migrate(Long sourceSystemId, Long targetSystemId, UserEntity actor) {
        var sourceSystem = findActive(sourceSystemId);
        SystemEntity targetSystem = null;
        if (targetSystemId != null) {
            if (sourceSystemId.equals(targetSystemId)) {
                throw new IllegalArgumentException("迁移目标不能是当前系统");
            }
            targetSystem = findActive(targetSystemId);
            if (!targetSystem.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "迁移目标系统已停用");
            }
        }
        var migratedCount = versionRequirements.migrateSystem(sourceSystem, targetSystem, actor);
        return new SystemMigrationResponse(migratedCount);
    }

    private SystemEntity findActive(Long id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统不存在"));
    }

    private void assertRecordVersion(long currentVersion, Long requestVersion, String message) {
        if (requestVersion == null || requestVersion != currentVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private UserEntity requireEnabledUser(Long userId, String label) {
        if (userId == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        var user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, label + "账号已停用，不能选择");
        }
        if (!user.canManageSystems()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, label + "必须是需求处理员或管理员");
        }
        return user;
    }

    private List<UserEntity> requireEnabledCollaborators(List<Long> collaboratorUserIds, UserEntity owner) {
        var ids = collaboratorUserIds == null ? List.<Long>of() : collaboratorUserIds;
        var uniqueIds = new LinkedHashSet<Long>();
        for (var userId : ids) {
            if (userId == null) {
                throw new IllegalArgumentException("系统协助人不能为空");
            }
            if (!uniqueIds.add(userId)) {
                throw new IllegalArgumentException("系统协助人不能重复");
            }
        }

        var collaborators = new ArrayList<UserEntity>();
        for (var userId : uniqueIds) {
            var collaborator = requireEnabledUser(userId, "系统协助人");
            if (collaborator.id().equals(owner.id())) {
                throw new IllegalArgumentException("系统负责人不能同时作为协助人");
            }
            collaborators.add(collaborator);
        }
        return collaborators;
    }

    private static String requireSystemName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("系统名称不能为空");
        }
        return name.trim();
    }

    private SystemResponse toResponse(SystemEntity system) {
        return SystemResponse.from(
                system,
                versions.countBySystemIdAndDeletedFalse(system.id()),
                requirements.countBySystemIdAndDeletedFalse(system.id())
        );
    }
}

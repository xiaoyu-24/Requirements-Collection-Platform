package com.xqfx.requirements.user;

import com.xqfx.requirements.dictionary.DictionaryCategory;
import com.xqfx.requirements.dictionary.DictionaryItemEntity;
import com.xqfx.requirements.dictionary.DictionaryService;
import com.xqfx.requirements.system.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final String INITIAL_PASSWORD = "888888";

    private final UserRepository repository;
    private final AuthService authService;
    private final DictionaryService dictionaries;
    private final SystemRepository systems;

    UserService(UserRepository repository, AuthService authService, DictionaryService dictionaries, SystemRepository systems) {
        this.repository = repository;
        this.authService = authService;
        this.dictionaries = dictionaries;
        this.systems = systems;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return repository.findAllByOrderByDisplayNameAsc().stream().map(UserResponse::from).toList();
    }

    /** 供需求处理人、填写人等下拉选择使用，只返回启用中的账号。 */
    @Transactional(readOnly = true)
    public List<UserResponse> listActiveUsers() {
        return repository.findByDisabledFalseAndRoleInOrderByDisplayNameAsc(List.of(UserRole.HANDLER, UserRole.ADMIN))
                .stream().map(UserResponse::from).toList();
    }

    /** 新建账号，使用统一初始密码，首次登录时必须修改。 */
    @Transactional
    public CreatedUser createUser(UserSaveRequest request) {
        var username = request.username().trim();
        if (repository.existsByUsername(username)) {
            throw new IllegalArgumentException("账号已存在：" + username);
        }
        var initialPassword = INITIAL_PASSWORD;
        var user = new UserEntity(
                username,
                authService.encodePassword(initialPassword),
                request.displayName().trim(),
                resolveOptionalActiveDepartment(request.departmentId()),
                request.role() == null ? UserRole.USER : request.role());
        repository.save(user);
        return new CreatedUser(UserResponse.from(user), initialPassword);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserSaveRequest request) {
        var user = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        var requestedRole = request.role() == null ? UserRole.USER : request.role();
        if (user.isAdmin() && requestedRole != UserRole.ADMIN && isLastActiveAdmin(user)) {
            throw new IllegalArgumentException("至少需要保留一个启用中的管理员");
        }
        if (user.canManageSystems() && requestedRole == UserRole.USER
                && (systems.existsByOwnerUser_IdAndDeletedFalse(id)
                || systems.existsByCollaboratorUserIdAndDeletedFalse(id))) {
            throw new IllegalArgumentException("该账号仍担任系统负责人或协助人，请先完成变更");
        }
        user.updateProfile(request.displayName().trim(),
                resolveDepartmentForUpdate(request.departmentId(), user.department()), requestedRole);
        repository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public String resetPassword(Long id) {
        var user = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        var newPassword = INITIAL_PASSWORD;
        user.resetPassword(authService.encodePassword(newPassword));
        repository.save(user);
        // 密码被重置后，该用户此前的登录一律失效。
        authService.invalidateSessionsFor(id);
        return newPassword;
    }

    @Transactional
    public UserResponse updateDisabled(Long id, boolean disabled) {
        var user = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (disabled && user.isAdmin() && isLastActiveAdmin(user)) {
            throw new IllegalArgumentException("至少需要保留一个启用中的管理员");
        }
        user.updateDisabled(disabled);
        repository.save(user);
        if (disabled) {
            authService.invalidateSessionsFor(id);
        }
        return UserResponse.from(user);
    }

    /**
     * 防止把最后一个管理员降级或停用，否则将没有人能进入人员管理页，
     * 只能改数据库才能恢复。
     */
    private boolean isLastActiveAdmin(UserEntity candidate) {
        return repository.findByDisabledFalseOrderByDisplayNameAsc().stream()
                .filter(UserEntity::isAdmin)
                .noneMatch(other -> !other.id().equals(candidate.id()));
    }

    private DictionaryItemEntity resolveOptionalActiveDepartment(Long departmentId) {
        return departmentId == null
                ? null
                : dictionaries.requireActive(departmentId, DictionaryCategory.DEPARTMENT, "部门");
    }

    /** 已关联的停用部门允许保留，不能主动换到其他停用部门。 */
    private DictionaryItemEntity resolveDepartmentForUpdate(Long requestedId, DictionaryItemEntity current) {
        if (requestedId == null) {
            return null;
        }
        if (current != null && current.id().equals(requestedId)) {
            return current;
        }
        return dictionaries.requireActive(requestedId, DictionaryCategory.DEPARTMENT, "部门");
    }

    public record CreatedUser(UserResponse user, String initialPassword) {
    }
}

package com.xqfx.requirements.requirement;

import com.xqfx.requirements.dictionary.DictionaryService;
import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemProfile;
import com.xqfx.requirements.system.SystemRepository;
import com.xqfx.requirements.system.SystemService;
import com.xqfx.requirements.user.AuthService;
import com.xqfx.requirements.user.UserEntity;
import com.xqfx.requirements.user.UserRepository;
import com.xqfx.requirements.user.UserRole;
import com.xqfx.requirements.user.UserSaveRequest;
import com.xqfx.requirements.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(showSql = false, properties = "spring.datasource.url=jdbc:h2:mem:overviewtest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DashboardController.class, RequirementService.class, AuthService.class, UserService.class, DictionaryService.class})
class OverviewDrilldownTest {

    @Autowired private RequirementRepository requirements;
    @Autowired private SystemRepository systems;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService users;
    @Autowired private RequirementService service;
    @Autowired private DashboardController dashboard;
    @MockBean private SystemService systemService;
    @MockBean private RequirementNotificationService notifications;

    private UserEntity alice;
    private UserEntity bob;
    private UserEntity sameName;
    private UserEntity requester;
    private SystemEntity systemA;

    @BeforeEach
    void seedRequirements() {
        alice = user("alice", "张三", UserRole.HANDLER);
        bob = user("bob", "李四", UserRole.HANDLER);
        sameName = user("another-alice", "张三", UserRole.HANDLER);
        requester = user("requester", "填写人", UserRole.USER);
        systemA = systems.save(new SystemEntity("系统 A", alice, List.of()));
        var systemB = systems.save(new SystemEntity("系统 B", alice, List.of()));
        var systemC = systems.save(new SystemEntity("系统 C", bob, List.of()));
        var sameNameSystem = systems.save(new SystemEntity("同名负责人的系统", sameName, List.of()));
        var legacySystem = systems.save(new SystemEntity(new SystemProfile("历史系统", "历史负责人", List.of())));

        submitted("A-待评估", systemA, null, RequirementStatus.PENDING_EVALUATION, RequirementUrgency.HIGH);
        submitted("B-已确认", systemB, null, RequirementStatus.CONFIRMED, RequirementUrgency.MEDIUM);
        submitted("A-指定李四", systemA, bob, RequirementStatus.IN_DEVELOPMENT, RequirementUrgency.HIGH);
        submitted("C-暂停", systemC, null, RequirementStatus.PAUSED, RequirementUrgency.LOW);
        submitted("同名人员的需求", sameNameSystem, null, RequirementStatus.PENDING_EVALUATION, RequirementUrgency.HIGH);
        submitted("未分配需求", null, null, RequirementStatus.PENDING_EVALUATION, RequirementUrgency.MEDIUM);
        submitted("历史负责人需求", legacySystem, null, RequirementStatus.PENDING_EVALUATION, RequirementUrgency.LOW);
        submitted("A-完成", systemA, null, RequirementStatus.COMPLETED, RequirementUrgency.HIGH);
        submitted("B-拒绝", systemB, bob, RequirementStatus.REJECTED, RequirementUrgency.HIGH);
        submitted("C-关闭", systemC, null, RequirementStatus.CLOSED, RequirementUrgency.LOW);
        draft("系统草稿", systemA);
        draft("未分配草稿", null);
        submitted("无系统但已指派", null, bob, RequirementStatus.CONFIRMED, RequirementUrgency.HIGH);
        var deleted = submitted("已删除", systemA, null, RequirementStatus.CONFIRMED, RequirementUrgency.HIGH);
        deleted.delete();
        requirements.flush();
    }

    @Test
    void overviewEndpointReturnsResponsibleGroupsForBothScopes() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(dashboard).build();
        mvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("unfinished"))
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.responsibleCounts", hasSize(5)));
        mvc.perform(get("/api/dashboard/overview").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("all"))
                .andExpect(jsonPath("$.total").value(13))
                .andExpect(jsonPath("$.responsibleCounts", hasSize(5)));
        mvc.perform(get("/api/dashboard/overview").param("scope", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eachResponsibleSegmentMatchesItsFilteredListIncludingDraftsAndLegacyOwners() {
        for (var scope : List.of("unfinished", "all")) {
            var overview = dashboard.overview(scope);
            assertThat(overview.responsibleCounts().stream().mapToLong(RequirementResponsibleCount::count).sum())
                    .isEqualTo(overview.total());
            for (var segment : overview.responsibleCounts()) {
                assertThat(page(scope.equals("unfinished"), false, segment.key(), null, null, null).totalElements())
                        .as("%s / %s", scope, segment.key()).isEqualTo(segment.count());
            }
        }
        assertThat(page(false, false, "none", null, null, null).content())
                .extracting(RequirementResponse::title).containsExactlyInAnyOrder("未分配需求", "未分配草稿");
        assertThat(page(false, false, "name:历史负责人", null, null, null).content())
                .extracting(RequirementResponse::title).containsExactly("历史负责人需求");
    }

    @Test
    void assigneeOverridesSystemOwnerAndDoesNotRequireASystem() {
        assertThat(page(true, false, key(bob), null, null, null).content())
                .extracting(RequirementResponse::title)
                .containsExactlyInAnyOrder("A-指定李四", "C-暂停", "无系统但已指派");
        assertThat(page(true, false, key(alice), null, null, null).content())
                .extracting(RequirementResponse::title).containsExactlyInAnyOrder("A-待评估", "B-已确认");
    }

    @Test
    void sameNameAccountsRemainSeparateGroups() {
        assertThat(dashboard.overview("unfinished").responsibleCounts())
                .filteredOn(group -> group.name().equals("张三"))
                .extracting(RequirementResponsibleCount::key)
                .containsExactlyInAnyOrder(key(alice), key(sameName));
    }

    @Test
    void renamedOwnersUseCurrentAccountNamesInBothChartsAndRows() {
        users.updateUser(alice.id(), new UserSaveRequest("alice", "张三新名字", null, UserRole.HANDLER));
        assertThat(dashboard.overview("all").responsibleCounts())
                .filteredOn(group -> group.key().equals(key(alice)))
                .singleElement().satisfies(group -> {
                    assertThat(group.name()).isEqualTo("张三新名字");
                    assertThat(group.count()).isEqualTo(4);
                });
        assertThat(page(false, false, key(alice), null, null, null).content())
                .allSatisfy(row -> assertThat(row.systemOwnerName()).isEqualTo("张三新名字"));
    }

    @Test
    void disabledResponsibleAccountsRemainVisible() {
        ReflectionTestUtils.setField(bob, "disabled", true);
        userRepository.flush();
        assertThat(service.responsibles(alice)).filteredOn(group -> group.key().equals(key(bob)))
                .singleElement().satisfies(group -> assertThat(group.count()).isEqualTo(5));
        assertThat(page(false, false, key(bob), null, null, null).totalElements()).isEqualTo(5);
    }

    @Test
    void emptyLegacyNamesBelongToUnassignedResponsibility() {
        var emptyOwner = systems.save(new SystemEntity(new SystemProfile("旧空负责人系统", "  ", List.of())));
        submitted("空负责人", emptyOwner, null, RequirementStatus.PENDING_EVALUATION, RequirementUrgency.LOW);
        assertThat(dashboard.overview("unfinished").responsibleCounts())
                .filteredOn(group -> group.key().equals("none"))
                .singleElement().satisfies(group -> assertThat(group.count()).isEqualTo(2));
        assertThat(page(true, false, "none", null, null, null).totalElements()).isEqualTo(2);
    }

    @Test
    void allKpiValuesMatchTheirDestinationFilters() {
        var unfinished = dashboard.overview("unfinished");
        var all = dashboard.overview("all");
        assertThat(page(true, false, null, null, null, null).totalElements()).isEqualTo(unfinished.total());
        assertThat(page(false, false, null, null, null, null).totalElements()).isEqualTo(all.total());
        assertThat(page(true, false, null, RequirementStatus.PENDING_EVALUATION, null, null).totalElements())
                .isEqualTo(unfinished.pendingEvaluationCount());
        assertThat(page(true, true, null, null, null, null).totalElements()).isEqualTo(unfinished.inProgressCount());
        assertThat(page(true, false, null, null, RequirementUrgency.HIGH, null).totalElements())
                .isEqualTo(all.highUrgencyPendingCount());
        assertThat(page(false, false, null, RequirementStatus.COMPLETED, null, RequirementSaveType.SUBMITTED).totalElements())
                .isEqualTo(all.completedCount());
    }

    @Test
    void inProgressCombinesThreeStatusesAndExcludesDraftsEvenIfTheyHaveAStatus() {
        var unusualDraft = draft("带状态的旧草稿", systemA);
        unusualDraft.updateStatusFromProgress(RequirementStatus.CONFIRMED);
        assertThat(page(false, true, null, null, null, null).content())
                .extracting(RequirementResponse::title)
                .containsExactlyInAnyOrder("B-已确认", "A-指定李四", "C-暂停", "无系统但已指派");
    }

    @Test
    void ordinaryUsersOnlySeeResponsiblesAndRowsFromTheirOwnRequirements() {
        var outsider = user("outsider", "其他人员", UserRole.HANDLER);
        var foreignSystem = systems.save(new SystemEntity("其他人的系统", outsider, List.of()));
        requirements.save(new RequirementEntity(outsider, "其他填写人", null, "其他人的需求", null, "内容",
                foreignSystem, null, RequirementPeriod.of(null, null), RequirementUrgency.MEDIUM));
        assertThat(service.responsibles(requester)).noneMatch(group -> group.key().equals(key(outsider)));
        assertThat(service.responsibles(requester).stream().mapToLong(RequirementResponsibleCount::count).sum()).isEqualTo(13);
        assertThat(service.page(requester, 0, 100, null, false, null, null, null, null,
                null, null, null, false, false, key(outsider), null, null, null).totalElements()).isZero();
        assertThat(service.responsibles(alice)).anyMatch(group -> group.key().equals(key(outsider)));
    }

    @Test
    void invalidResponsibleKeysAreRejected() {
        for (var invalid : List.of("user:abc", "user:0", "user:-1", "name: ", "unknown")) {
            assertThatThrownBy(() -> page(false, false, invalid, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("负责人筛选无效");
        }
    }

    private UserEntity user(String username, String displayName, UserRole role) {
        var created = users.createUser(new UserSaveRequest(username, displayName, null, role));
        return userRepository.findById(created.user().id()).orElseThrow();
    }

    private RequirementEntity submitted(String title, SystemEntity system, UserEntity assignee,
                                        RequirementStatus status, RequirementUrgency urgency) {
        var requirement = new RequirementEntity(requester, "填写人", null, title, null, "内容", system,
                null, RequirementPeriod.of(null, null), urgency);
        requirement.updateStatusFromProgress(status);
        requirement.assign(assignee);
        return requirements.save(requirement);
    }

    private RequirementEntity draft(String title, SystemEntity system) {
        return requirements.save(RequirementEntity.draft(requester, "填写人", null, title, null, "内容", system,
                null, RequirementPeriod.of(null, null), RequirementUrgency.MEDIUM));
    }

    private RequirementPageResponse page(boolean unfinished, boolean inProgress, String responsible,
                                         RequirementStatus status, RequirementUrgency urgency, RequirementSaveType saveType) {
        return service.page(alice, 0, 100, null, false, null, null, null, null, status, urgency, saveType,
                unfinished, inProgress, responsible, null, null, null);
    }

    private static String key(UserEntity user) {
        return "user:" + user.id();
    }
}

package com.xqfx.requirements.requirement;

import com.xqfx.requirements.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    private final RequirementRepository repository;

    DashboardController(RequirementRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/summary")
    Map<String, Object> summary() {
        long total = repository.countByDeletedFalseAndSaveType(RequirementSaveType.SUBMITTED);
        long draftCount = repository.countByDeletedFalseAndSaveType(RequirementSaveType.DRAFT);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (RequirementStatus status : RequirementStatus.values()) {
            statusCounts.put(status.name(), repository.countByDeletedFalseAndSaveTypeAndStatus(RequirementSaveType.SUBMITTED, status));
        }

        Map<String, Long> urgencyCounts = new LinkedHashMap<>();
        for (RequirementUrgency urgency : RequirementUrgency.values()) {
            urgencyCounts.put(urgency.name(), repository.countByDeletedFalseAndSaveTypeAndUrgency(RequirementSaveType.SUBMITTED, urgency));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("draftCount", draftCount);
        result.put("statusCounts", statusCounts);
        result.put("urgencyCounts", urgencyCounts);
        return result;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    OverviewResponse overview(@RequestParam(defaultValue = "unfinished") String scope) {
        var overviewScope = OverviewScope.parse(scope);
        var unfinishedScope = overviewScope == OverviewScope.UNFINISHED;
        var unfinishedStatuses = RequirementSpecifications.unfinishedStatuses();
        var submitted = repository.countByDeletedFalseAndSaveType(RequirementSaveType.SUBMITTED);
        var drafts = repository.countByDeletedFalseAndSaveType(RequirementSaveType.DRAFT);
        var unfinished = repository.countByDeletedFalseAndSaveTypeAndStatusIn(
                RequirementSaveType.SUBMITTED, unfinishedStatuses);
        var total = unfinishedScope ? unfinished : repository.countByDeletedFalse();
        var pendingEvaluation = repository.countByDeletedFalseAndSaveTypeAndStatus(
                RequirementSaveType.SUBMITTED, RequirementStatus.PENDING_EVALUATION);
        var completed = repository.countByDeletedFalseAndSaveTypeAndStatus(
                RequirementSaveType.SUBMITTED, RequirementStatus.COMPLETED);
        var highUrgencyPending = repository.countByDeletedFalseAndSaveTypeAndUrgencyAndStatusIn(
                RequirementSaveType.SUBMITTED, RequirementUrgency.HIGH, unfinishedStatuses);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (RequirementStatus status : RequirementStatus.values()) {
            statusCounts.put(status.name(), unfinishedScope && !unfinishedStatuses.contains(status)
                    ? 0L
                    : repository.countByDeletedFalseAndSaveTypeAndStatus(RequirementSaveType.SUBMITTED, status));
        }

        Map<String, Long> urgencyCounts = new LinkedHashMap<>();
        for (RequirementUrgency urgency : RequirementUrgency.values()) {
            urgencyCounts.put(urgency.name(), unfinishedScope
                    ? repository.countByDeletedFalseAndSaveTypeAndUrgencyAndStatusIn(
                            RequirementSaveType.SUBMITTED, urgency, unfinishedStatuses)
                    : repository.countByDeletedFalseAndUrgency(urgency));
        }

        var systemCountsQuery = unfinishedScope
                ? repository.countRequirementsBySystemAndStatusIn(RequirementSaveType.SUBMITTED, unfinishedStatuses)
                : repository.countRequirementsBySystem();
        var systemCounts = systemCountsQuery.stream()
                .map(item -> new SystemCount(item.getSystemId(), item.getSystemName(), item.getRequirementCount()))
                .toList();
        var responsibleCounts = repository.countRequirementsByResponsible(
                        unfinishedScope, RequirementSaveType.SUBMITTED, unfinishedStatuses, null).stream()
                .map(RequirementResponsibleCount::from)
                .toList();
        var completionRate = submitted == 0 ? 0 : (int) Math.round((double) completed * 100 / submitted);

        return new OverviewResponse(
                overviewScope.value,
                total,
                drafts,
                unfinished,
                pendingEvaluation,
                Math.max(unfinished - pendingEvaluation, 0),
                completed,
                completionRate,
                highUrgencyPending,
                systemCounts,
                responsibleCounts,
                statusCounts,
                urgencyCounts);
    }

    @GetMapping("/workbench")
    @Transactional(readOnly = true)
    WorkbenchResponse workbench() {
        var userId = CurrentUser.require().id();
        var terminalStatuses = Set.of(RequirementStatus.COMPLETED, RequirementStatus.REJECTED, RequirementStatus.CLOSED);
        var owned = repository.findWorkbenchOwned(userId, RequirementSaveType.SUBMITTED, terminalStatuses);
        var ownedIds = owned.stream().map(RequirementEntity::id).collect(java.util.stream.Collectors.toSet());
        var assisting = repository.findWorkbenchAssisting(userId, RequirementSaveType.SUBMITTED, terminalStatuses).stream()
                .filter(requirement -> !ownedIds.contains(requirement.id()))
                .toList();
        return new WorkbenchResponse(
                owned.stream().map(WorkbenchRequirement::from).toList(),
                assisting.stream().map(WorkbenchRequirement::from).toList());
    }

    record WorkbenchResponse(List<WorkbenchRequirement> owned, List<WorkbenchRequirement> assisting) {
    }

    record WorkbenchRequirement(Long id, String title, String requesterName, String systemName,
                                RequirementStatus status, RequirementUrgency urgency,
                                java.time.LocalDateTime updatedAt) {
        static WorkbenchRequirement from(RequirementEntity requirement) {
            return new WorkbenchRequirement(
                    requirement.id(),
                    requirement.title(),
                    requirement.requesterName(),
                    requirement.system().name(),
                    requirement.status(),
                    requirement.urgency(),
                    requirement.updatedAt());
        }
    }

    record OverviewResponse(String scope, long total, long draftCount, long unfinishedCount, long pendingEvaluationCount,
                            long inProgressCount, long completedCount, int completionRate,
                            long highUrgencyPendingCount, List<SystemCount> systemCounts,
                            List<RequirementResponsibleCount> responsibleCounts,
                            Map<String, Long> statusCounts, Map<String, Long> urgencyCounts) {
    }

    record SystemCount(Long systemId, String systemName, Long count) {
    }

    private enum OverviewScope {
        UNFINISHED("unfinished"),
        ALL("all");

        private final String value;

        OverviewScope(String value) {
            this.value = value;
        }

        private static OverviewScope parse(String value) {
            for (var scope : values()) {
                if (scope.value.equalsIgnoreCase(value)) return scope;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需求概览范围无效");
        }
    }
}

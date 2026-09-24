package com.xqfx.requirements.requirement;

import com.xqfx.requirements.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system-versions/{versionId}")
class SystemVersionRequirementController {

    private final SystemVersionRequirementService service;

    SystemVersionRequirementController(SystemVersionRequirementService service) {
        this.service = service;
    }

    @GetMapping("/requirements")
    RequirementPageResponse list(@PathVariable Long versionId,
                                 @RequestParam VersionRequirementScope scope,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) RequirementStatus status,
                                 @RequestParam(required = false) RequirementUrgency urgency,
                                 @RequestParam(required = false) String source) {
        return scope == VersionRequirementScope.CURRENT
                ? service.listCurrent(versionId, page, size, keyword, status, urgency)
                : service.listCandidates(versionId, page, size, keyword, status, urgency, source);
    }

    @GetMapping("/requirements/summary")
    VersionRequirementSummaryResponse summary(@PathVariable Long versionId) {
        return service.summary(versionId);
    }

    @PostMapping("/requirements/batch-bind")
    VersionRequirementBatchResponse bind(@PathVariable Long versionId, @Valid @RequestBody BatchRequest request) {
        return service.bind(versionId, request.requirements(), CurrentUser.require());
    }

    @PostMapping("/requirements/batch-unbind")
    VersionRequirementBatchResponse unbind(@PathVariable Long versionId, @Valid @RequestBody BatchRequest request) {
        return service.unbind(versionId, request.requirements(), CurrentUser.require());
    }

    record BatchRequest(@NotEmpty List<@Valid VersionRequirementSelection> requirements) {
    }
}

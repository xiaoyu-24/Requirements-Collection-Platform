package com.xqfx.requirements.requirement;

import com.xqfx.requirements.user.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/requirements")
class RequirementVersionHistoryController {

    private final SystemVersionRequirementService service;

    RequirementVersionHistoryController(SystemVersionRequirementService service) {
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/version-history")
    List<RequirementVersionChangeResponse> history(@PathVariable Long id) {
        return service.history(id, CurrentUser.require());
    }
}

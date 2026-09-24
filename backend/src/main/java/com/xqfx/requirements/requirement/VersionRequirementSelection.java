package com.xqfx.requirements.requirement;

import jakarta.validation.constraints.NotNull;

record VersionRequirementSelection(@NotNull Long id, @NotNull Long recordVersion) {
}

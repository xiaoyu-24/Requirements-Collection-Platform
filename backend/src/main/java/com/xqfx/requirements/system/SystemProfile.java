package com.xqfx.requirements.system;

import java.util.List;
import java.util.Set;

public record SystemProfile(String name, String ownerName, List<String> collaborators) {

    private static final int MAX_COLLABORATOR_LENGTH = 50;

    public static SystemProfile create(String name, String ownerName, List<String> collaborators) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("系统名称不能为空");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("系统负责人不能为空");
        }

        var normalizedCollaborators = (collaborators == null ? List.<String>of() : collaborators).stream()
                .map(collaborator -> {
                    if (collaborator == null || collaborator.isBlank()) {
                        throw new IllegalArgumentException("系统协助人不能为空");
                    }
                    var normalized = collaborator.trim();
                    if (normalized.length() > MAX_COLLABORATOR_LENGTH) {
                        throw new IllegalArgumentException("系统协助人不能超过50个字符");
                    }
                    return normalized;
                })
                .toList();
        if (Set.copyOf(normalizedCollaborators).size() != normalizedCollaborators.size()) {
            throw new IllegalArgumentException("系统协助人不能重复");
        }

        return new SystemProfile(name.trim(), ownerName.trim(), normalizedCollaborators);
    }
}

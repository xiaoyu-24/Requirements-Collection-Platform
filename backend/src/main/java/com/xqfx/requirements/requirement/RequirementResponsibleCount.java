package com.xqfx.requirements.requirement;

record RequirementResponsibleCount(String key, String name, long count) {

    static final String USER_PREFIX = "user:";
    static final String NAME_PREFIX = "name:";
    static final String UNASSIGNED_KEY = "none";

    static RequirementResponsibleCount from(RequirementRepository.ResponsibleRequirementCount item) {
        var userId = item.getResponsibleUserId();
        var name = item.getResponsibleName();
        var key = userId != null ? USER_PREFIX + userId
                : name != null ? NAME_PREFIX + name : UNASSIGNED_KEY;
        return new RequirementResponsibleCount(
                key, name == null ? "未分配负责人" : name, item.getRequirementCount());
    }
}

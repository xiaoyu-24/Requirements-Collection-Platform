package com.xqfx.requirements.requirement;

import java.time.LocalDate;

public record RequirementPeriod(LocalDate startDate, LocalDate endDate) {

    public static RequirementPeriod of(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        if ((startDate == null) != (endDate == null)) {
            throw new IllegalArgumentException("开始日期和结束日期必须同时填写");
        }

        return new RequirementPeriod(startDate, endDate);
    }
}

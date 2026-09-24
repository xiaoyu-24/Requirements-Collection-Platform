package com.xqfx.requirements.dictionary;

import jakarta.validation.constraints.NotNull;

record DictionaryItemDisabledRequest(
        @NotNull(message = "请选择启停状态") Boolean disabled,
        @NotNull(message = "缺少记录版本") Long recordVersion) {
}

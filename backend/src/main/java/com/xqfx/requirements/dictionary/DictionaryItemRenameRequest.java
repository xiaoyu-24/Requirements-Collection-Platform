package com.xqfx.requirements.dictionary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record DictionaryItemRenameRequest(
        @NotBlank(message = "请输入名称") @Size(max = 50, message = "名称最长 50 位") String name,
        @NotNull(message = "缺少记录版本") Long recordVersion) {
}

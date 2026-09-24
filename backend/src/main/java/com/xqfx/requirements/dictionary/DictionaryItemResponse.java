package com.xqfx.requirements.dictionary;

public record DictionaryItemResponse(
        Long id,
        DictionaryCategory category,
        String name,
        boolean disabled,
        long recordVersion) {

    static DictionaryItemResponse from(DictionaryItemEntity item) {
        return new DictionaryItemResponse(
                item.id(),
                item.category(),
                item.name(),
                item.isDisabled(),
                item.recordVersion());
    }
}

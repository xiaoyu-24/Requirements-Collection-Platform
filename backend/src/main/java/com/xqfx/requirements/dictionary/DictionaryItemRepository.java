package com.xqfx.requirements.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryItemRepository extends JpaRepository<DictionaryItemEntity, Long> {

    List<DictionaryItemEntity> findByCategoryOrderByDisabledAscNameAsc(DictionaryCategory category);

    List<DictionaryItemEntity> findByCategoryAndDisabledFalseOrderByNameAsc(DictionaryCategory category);

    boolean existsByCategoryAndNameIgnoreCaseAndIdNot(DictionaryCategory category, String name, Long id);

    boolean existsByCategoryAndNameIgnoreCase(DictionaryCategory category, String name);
}

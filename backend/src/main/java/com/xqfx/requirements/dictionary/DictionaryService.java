package com.xqfx.requirements.dictionary;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DictionaryService {

    private final DictionaryItemRepository repository;

    public DictionaryService(DictionaryItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DictionaryItemEntity> listActiveEntities(DictionaryCategory category) {
        return repository.findByCategoryAndDisabledFalseOrderByNameAsc(category);
    }

    @Transactional(readOnly = true)
    List<DictionaryItemResponse> listActive(DictionaryCategory category) {
        return listActiveEntities(category).stream().map(DictionaryItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    List<DictionaryItemResponse> list(DictionaryCategory category) {
        return repository.findByCategoryOrderByDisabledAscNameAsc(category).stream()
                .map(DictionaryItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DictionaryItemEntity requireActive(Long id, DictionaryCategory category, String label) {
        if (id == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        var item = findItem(id);
        if (item.category() != category) {
            throw new IllegalArgumentException(label + "不属于指定类别");
        }
        if (item.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, label + "已停用，不能选择");
        }
        return item;
    }

    @Transactional
    DictionaryItemResponse create(DictionaryItemSaveRequest request) {
        var name = normalizedName(request.name());
        if (repository.existsByCategoryAndNameIgnoreCase(request.category(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同类别下已存在同名项");
        }
        var saved = repository.save(new DictionaryItemEntity(request.category(), name));
        return DictionaryItemResponse.from(saved);
    }

    @Transactional
    DictionaryItemResponse rename(Long id, DictionaryItemRenameRequest request) {
        var item = findItem(id);
        assertRecordVersion(item, request.recordVersion());
        var name = normalizedName(request.name());
        if (repository.existsByCategoryAndNameIgnoreCaseAndIdNot(item.category(), name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同类别下已存在同名项");
        }
        item.rename(name);
        repository.flush();
        return DictionaryItemResponse.from(item);
    }

    @Transactional
    DictionaryItemResponse updateDisabled(Long id, DictionaryItemDisabledRequest request) {
        var item = findItem(id);
        assertRecordVersion(item, request.recordVersion());
        item.updateDisabled(request.disabled());
        repository.flush();
        return DictionaryItemResponse.from(item);
    }

    private DictionaryItemEntity findItem(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "字典项不存在"));
    }

    private static String normalizedName(String value) {
        return value == null ? "" : value.trim();
    }

    private static void assertRecordVersion(DictionaryItemEntity item, Long requestVersion) {
        if (requestVersion == null || item.recordVersion() != requestVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "字典项已被其他人修改，请刷新后重试");
        }
    }
}

package com.xqfx.requirements.dictionary;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dictionaries")
class DictionaryController {

    private final DictionaryService service;

    DictionaryController(DictionaryService service) {
        this.service = service;
    }

    @GetMapping("/active")
    List<DictionaryItemResponse> listActive(@RequestParam DictionaryCategory category) {
        return service.listActive(category);
    }

    @GetMapping
    List<DictionaryItemResponse> list(@RequestParam DictionaryCategory category) {
        return service.list(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DictionaryItemResponse create(@Valid @RequestBody DictionaryItemSaveRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    DictionaryItemResponse rename(@PathVariable Long id, @Valid @RequestBody DictionaryItemRenameRequest request) {
        return service.rename(id, request);
    }

    @PatchMapping("/{id}/disabled")
    DictionaryItemResponse updateDisabled(@PathVariable Long id,
                                          @Valid @RequestBody DictionaryItemDisabledRequest request) {
        return service.updateDisabled(id, request);
    }
}

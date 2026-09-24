package com.xqfx.requirements.aiconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
class AiAnalysisController {

    private final AiAnalysisService service;

    AiAnalysisController(AiAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    AiAnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        try {
            return service.analyze(request.text());
        } catch (AiAnalysisService.AiAnalysisException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @PostMapping("/analyze-image")
    AiAnalysisResponse analyzeImage(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            return service.analyzeImage(file);
        } catch (AiAnalysisService.AiAnalysisException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    record AnalyzeRequest(@NotBlank String text) {
    }
}

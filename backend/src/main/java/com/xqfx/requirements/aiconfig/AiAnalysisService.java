package com.xqfx.requirements.aiconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqfx.requirements.dictionary.DictionaryCategory;
import com.xqfx.requirements.dictionary.DictionaryItemEntity;
import com.xqfx.requirements.dictionary.DictionaryService;
import com.xqfx.requirements.system.SystemEntity;
import com.xqfx.requirements.system.SystemRepository;
import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.system.SystemVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLHandshakeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
class AiAnalysisService {

    private final AiConfigService configService;
    private final SystemRepository systemRepository;
    private final SystemVersionRepository versionRepository;
    private final DictionaryService dictionaryService;
    private final ObjectMapper objectMapper;
    private final int requestTimeoutSeconds;
    private final int imageRequestTimeoutSeconds;

    AiAnalysisService(AiConfigService configService,
                      SystemRepository systemRepository,
                      SystemVersionRepository versionRepository,
                      DictionaryService dictionaryService,
                      ObjectMapper objectMapper,
                      @Value("${app.ai.request-timeout-seconds:30}") int requestTimeoutSeconds,
                      @Value("${app.ai.image-request-timeout-seconds:60}") int imageRequestTimeoutSeconds) {
        this.configService = configService;
        this.systemRepository = systemRepository;
        this.versionRepository = versionRepository;
        this.dictionaryService = dictionaryService;
        this.objectMapper = objectMapper;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.imageRequestTimeoutSeconds = imageRequestTimeoutSeconds;
    }

    AiAnalysisResponse analyze(String text) {
        var config = configService.getActiveEntity();
        if (config == null || !config.enabled()) {
            throw new AiAnalysisException("AI 功能未启用，请先在 AI 配置页开启并激活一个配置");
        }
        if (config.serviceUrl() == null || config.serviceUrl().isBlank()) {
            throw new AiAnalysisException("AI 服务地址未配置");
        }
        if (config.modelName() == null || config.modelName().isBlank()) {
            throw new AiAnalysisException("AI 模型名未配置");
        }

        var departments = dictionaryService.listActiveEntities(DictionaryCategory.DEPARTMENT);
        var types = dictionaryService.listActiveEntities(DictionaryCategory.REQUIREMENT_TYPE);
        var apiKey = configService.getApiKey(config);
        var prompt = buildPrompt(text, departments, types);

        try {
            var responseContent = callModel(config.serviceUrl(), config.modelName(), apiKey, prompt);
            return parseAndValidate(responseContent, departments, types);
        } catch (AiAnalysisException e) {
            throw e;
        } catch (SSLHandshakeException e) {
            throw new AiAnalysisException("无法与 AI 服务建立安全连接，请确认 AI Base URL 的 HTTP/HTTPS 协议正确，且服务端支持 TLS 1.2");
        } catch (Exception e) {
            throw new AiAnalysisException("AI 分析失败：" + e.getMessage());
        }
    }

    AiAnalysisResponse analyzeImage(MultipartFile file) {
        var config = configService.getActiveEntity();
        if (config == null || !config.enabled()) {
            throw new AiAnalysisException("AI 功能未启用，请先在 AI 配置页开启并激活一个配置");
        }
        if (config.serviceUrl() == null || config.serviceUrl().isBlank()) {
            throw new AiAnalysisException("AI 服务地址未配置");
        }
        if (config.modelName() == null || config.modelName().isBlank()) {
            throw new AiAnalysisException("AI 模型名未配置");
        }
        if (file == null || file.isEmpty()) {
            throw new AiAnalysisException("请选择要识别的图片");
        }
        var sizeError = checkImageSize(file.getSize());
        if (sizeError != null) {
            throw new AiAnalysisException(sizeError);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AiAnalysisException("图片读取失败，请重试");
        }
        var contentType = detectImageContentType(bytes);
        if (contentType == null) {
            throw new AiAnalysisException("仅支持 PNG / JPG / WebP 格式的图片");
        }
        // 图片仅在内存中转为 data URL 随请求发送，不落地、不入日志（spec "图片不落地"）
        var dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);

        var departments = dictionaryService.listActiveEntities(DictionaryCategory.DEPARTMENT);
        var types = dictionaryService.listActiveEntities(DictionaryCategory.REQUIREMENT_TYPE);
        var apiKey = configService.getApiKey(config);
        var prompt = buildImagePrompt(departments, types);

        try {
            var responseContent = callModel(config.serviceUrl(), config.modelName(), apiKey, prompt,
                    dataUrl, imageRequestTimeoutSeconds);
            return parseAndValidate(responseContent, departments, types);
        } catch (AiAnalysisException e) {
            throw e;
        } catch (SSLHandshakeException e) {
            throw new AiAnalysisException("无法与 AI 服务建立安全连接，请确认 AI Base URL 的 HTTP/HTTPS 协议正确，且服务端支持 TLS 1.2");
        } catch (Exception e) {
            throw new AiAnalysisException("AI 分析失败：" + e.getMessage());
        }
    }

    private String buildPrompt(String userText, List<DictionaryItemEntity> departments,
                               List<DictionaryItemEntity> types) {
        return buildExtractionPrompt(departments, types, "用户文本：\n" + userText);
    }

    private String buildImagePrompt(List<DictionaryItemEntity> departments,
                                    List<DictionaryItemEntity> types) {
        return buildExtractionPrompt(departments, types, IMAGE_INPUT_INSTRUCTION);
    }

    private String buildExtractionPrompt(List<DictionaryItemEntity> departments,
                                         List<DictionaryItemEntity> types,
                                         String userContentSection) {
        var systems = systemRepository.findAllByDeletedFalse().stream()
                .filter(SystemEntity::isActive)
                .collect(Collectors.toList());

        var systemDict = new StringBuilder();
        for (var system : systems) {
            systemDict.append("- 系统ID=").append(system.id()).append("，名称=\"").append(system.name()).append("\"");
            var versions = versionRepository.findBySystemIdAndDeletedFalseOrderByNameAsc(system.id()).stream()
                    .filter(SystemVersionEntity::isActive)
                    .toList();
            if (!versions.isEmpty()) {
                systemDict.append("，版本：");
                systemDict.append(versions.stream()
                        .map(version -> "ID=" + version.id() + "/\"" + version.name() + "\"")
                        .collect(Collectors.joining("、")));
            }
            systemDict.append("\n");
        }

        var departmentNames = dictionaryNames(departments);
        var typeNames = dictionaryNames(types);
        return """
                你是一个需求分析助手。请从用户提供的文本中提取需求信息，严格按以下 JSON 格式返回（不要输出其他内容）：
                {"requesterName":"...","department":"...","title":"...","type":"...","content":"...","system":"...","version":"...","periodStartDate":"...","periodEndDate":"..."}

                规则：
                1. requesterName 为提出需求的人名（2-4个汉字），无法确定则填 null。
                2. department 必须是以下部门字典中的一个名称。用户可能使用简称或近义写法，请匹配最接近的名称；无法确定则填 null。
                3. type 必须是以下需求类型字典中的一个名称；无法确定则填 null。
                4. title 为简短概括，content 为完整需求描述。无法提取则填 null。
                5. system 填写最匹配的系统名称。用户文本中可能使用简称、别名或大小写不同的名称，请尽量匹配下方系统列表中最接近的系统。无法唯一确定则填 null。
                6. version 填写最匹配的版本名称（必须从对应系统的版本列表中匹配），无法唯一确定则填 null。
                7. periodStartDate 和 periodEndDate 格式为 yyyy-MM-dd，无法确定则填 null。

                可选部门：%s
                可选需求类型：%s

                系统与版本字典：
                %s
                %s
                """.formatted(departmentNames, typeNames, systemDict, userContentSection);
    }

    private String callModel(String serviceUrl, String modelName, String apiKey, String prompt) throws Exception {
        return callModel(serviceUrl, modelName, apiKey, prompt, null, requestTimeoutSeconds);
    }

    private String callModel(String serviceUrl, String modelName, String apiKey, String prompt,
                             String imageDataUrl, int timeoutSeconds) throws Exception {
        Object messageContent = imageDataUrl == null
                ? prompt
                : List.of(
                        java.util.Map.of("type", "text", "text", prompt),
                        java.util.Map.of("type", "image_url", "image_url", java.util.Map.of("url", imageDataUrl)));
        var requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                "model", modelName,
                "messages", List.of(java.util.Map.of("role", "user", "content", messageContent)),
                "temperature", 0.1
        ));

        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        var response = AiHttpClientFactory.send(
                requestBuilder.build(), HttpResponse.BodyHandlers.ofString(), timeoutSeconds);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (imageDataUrl != null) {
                var visionError = mapVisionError(response.statusCode(), response.body());
                if (visionError != null) {
                    throw new AiAnalysisException(visionError);
                }
            }
            throw new AiAnalysisException("AI 服务返回错误，状态码：" + response.statusCode());
        }

        var responseJson = objectMapper.readTree(response.body());
        JsonNode choices = responseJson.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiAnalysisException("AI 服务未返回有效结果");
        }
        return choices.get(0).get("message").get("content").asText();
    }

    private AiAnalysisResponse parseAndValidate(String content, List<DictionaryItemEntity> departments,
                                                List<DictionaryItemEntity> types) {
        try {
            var json = content.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").strip();
            }

            var node = objectMapper.readTree(json);
            var department = getValidDepartment(node, departments);
            var type = getValidType(node, types);
            var systemId = resolveSystemId(node);

            return new AiAnalysisResponse(
                    getNullableString(node, "requesterName"),
                    department == null ? null : department.id(),
                    department == null ? null : department.name(),
                    getNullableString(node, "title"),
                    type == null ? null : type.id(),
                    type == null ? null : type.name(),
                    getNullableString(node, "content"),
                    systemId,
                    resolveVersionId(node, systemId),
                    getValidDate(node, "periodStartDate"),
                    getValidDate(node, "periodEndDate"));
        } catch (AiAnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AiAnalysisException("AI 返回结果解析失败，请重试");
        }
    }

    private DictionaryItemEntity getValidDepartment(JsonNode node, List<DictionaryItemEntity> departments) {
        var value = getNullableString(node, "department");
        if (value == null) {
            return null;
        }
        var exactMatches = departments.stream()
                .filter(item -> item.name().equalsIgnoreCase(value))
                .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.get(0);
        }

        var normalizedInput = normalizeDepartmentName(value);
        var matches = departments.stream()
                .filter(item -> {
                    var normalizedDepartment = normalizeDepartmentName(item.name());
                    return normalizedDepartment.equals(normalizedInput)
                            || normalizedDepartment.contains(normalizedInput)
                            || normalizedInput.contains(normalizedDepartment);
                })
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private DictionaryItemEntity getValidType(JsonNode node, List<DictionaryItemEntity> types) {
        var value = getNullableString(node, "type");
        if (value == null) {
            return null;
        }
        var matches = types.stream().filter(item -> item.name().equalsIgnoreCase(value)).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private Long resolveSystemId(JsonNode node) {
        var systemName = getNullableString(node, "system");
        if (systemName == null) {
            return null;
        }

        var normalized = systemName.trim().toLowerCase(Locale.ROOT);
        var activeSystems = systemRepository.findAllByDeletedFalse().stream()
                .filter(SystemEntity::isActive)
                .toList();
        var exactMatches = activeSystems.stream()
                .filter(system -> SystemEntity.normalizedName(system.name()).equals(normalized))
                .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.get(0).id();
        }

        var fuzzyMatches = activeSystems.stream()
                .filter(system -> {
                    var name = SystemEntity.normalizedName(system.name());
                    return name.contains(normalized) || normalized.contains(name);
                })
                .toList();
        return fuzzyMatches.size() == 1 ? fuzzyMatches.get(0).id() : null;
    }

    private Long resolveVersionId(JsonNode node, Long systemId) {
        if (systemId == null) {
            return null;
        }
        var versionName = getNullableString(node, "version");
        if (versionName == null) {
            return null;
        }

        var normalized = versionName.trim().toLowerCase(Locale.ROOT);
        var activeVersions = versionRepository.findBySystemIdAndDeletedFalseOrderByNameAsc(systemId).stream()
                .filter(SystemVersionEntity::isActive)
                .toList();
        var exactMatches = activeVersions.stream()
                .filter(version -> SystemVersionEntity.normalizedName(version.name()).equals(normalized))
                .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.get(0).id();
        }

        var fuzzyMatches = activeVersions.stream()
                .filter(version -> {
                    var name = SystemVersionEntity.normalizedName(version.name());
                    return name.contains(normalized) || normalized.contains(name);
                })
                .toList();
        return fuzzyMatches.size() == 1 ? fuzzyMatches.get(0).id() : null;
    }

    private LocalDate getValidDate(JsonNode node, String field) {
        var value = getNullableString(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String getNullableString(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        var text = value.asText().strip();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static String dictionaryNames(List<DictionaryItemEntity> items) {
        return items.isEmpty()
                ? "无（无法识别）"
                : items.stream().map(DictionaryItemEntity::name).collect(Collectors.joining("、"));
    }

    private static String normalizeDepartmentName(String value) {
        return value.replace("部", "").trim().toLowerCase(Locale.ROOT);
    }

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    static final String VISION_UNSUPPORTED_MESSAGE =
            "当前激活模型可能不支持图片识别，请在 AI 配置中切换为视觉模型（如 qwen-vl、glm-4v、mimo-vl 等）后重试";

    private static final String IMAGE_INPUT_INSTRUCTION = """
            用户截图（可能是微信 / QQ 聊天记录、邮件或文档截图）。请先识别图中文字，再按上述规则提取需求信息。
            噪音过滤要求：
            1. 忽略聊天界面元素：昵称、头像、时间戳、表情、"已读/撤回"、系统通知栏、电量信号等。
            2. requesterName 仅在聊天上下文明确表明某人提出该需求时填写；仅有昵称出现不足以判定，否则填 null。
            3. 与需求无关的文字（广告、闲聊、群公告等）不提取。
            4. 若图中有多段对话，仅提取与需求描述相关的部分并合并到 content。""";

    /** 图片大小校验（纯函数便于单测）：超过 10 MB 返回错误文案，否则 null 表示通过。 */
    static String checkImageSize(long size) {
        return size > MAX_IMAGE_BYTES ? "图片大小不能超过 10MB" : null;
    }

    /** 视觉能力错误映射：仅 4xx 且错误体含视觉相关关键词时返回引导文案，否则 null（design.md D5）。 */
    static String mapVisionError(int status, String body) {
        if (status < 400 || status >= 500 || body == null) {
            return null;
        }
        var lower = body.toLowerCase(Locale.ROOT);
        var hit = lower.contains("image") || lower.contains("vision") || lower.contains("visual")
                || lower.contains("multimodal") || lower.contains("图片") || lower.contains("视觉");
        return hit ? VISION_UNSUPPORTED_MESSAGE : null;
    }

    /** 图片魔数识别（design.md D4）：仅信任文件内容，不信任扩展名与客户端 MIME。 */
    static String detectImageContentType(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50 && (data[2] & 0xFF) == 0x4E && (data[3] & 0xFF) == 0x47) {
            return "image/png";
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((data[0] & 0xFF) == 'R' && (data[1] & 0xFF) == 'I' && (data[2] & 0xFF) == 'F' && (data[3] & 0xFF) == 'F'
                && (data[8] & 0xFF) == 'W' && (data[9] & 0xFF) == 'E' && (data[10] & 0xFF) == 'B' && (data[11] & 0xFF) == 'P') {
            return "image/webp";
        }
        return null;
    }

    static class AiAnalysisException extends RuntimeException {
        AiAnalysisException(String message) {
            super(message);
        }
    }
}

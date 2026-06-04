package org.ruoyi.service.draw.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.ErDiagramProperties;
import org.ruoyi.domain.dto.request.SystemArchitectureGenerateRequest;
import org.ruoyi.domain.dto.response.SystemArchitectureItemVo;
import org.ruoyi.domain.dto.response.SystemArchitectureLayerVo;
import org.ruoyi.domain.dto.response.SystemArchitectureResponse;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.ISystemArchitectureService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemArchitectureServiceImpl implements ISystemArchitectureService {

    private static final String PROMPT_CODE = "system_architecture";

    private static final String JSON_SCHEMA = """
        {
          "title": "系统架构图",
          "layers": [
            {
              "id": "l1",
              "label": "用户层",
              "items": [
                { "id": "i1", "label": "Web 应用" },
                { "id": "i2", "label": "移动 App" }
              ]
            },
            {
              "id": "l2",
              "label": "业务层",
              "items": [
                { "id": "i3", "label": "用户服务" }
              ]
            }
          ]
        }
        """;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;

    @Override
    public SystemArchitectureResponse generate(SystemArchitectureGenerateRequest request) {
        if (StringUtils.isBlank(request.getDescription())) {
            throw new ServiceException("系统架构描述不能为空");
        }
        String archType = normalizeArchType(request.getArchType());
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String systemPrompt = loadSystemPrompt(archType);
        String fullPrompt = systemPrompt + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + JSON_SCHEMA
            + "\n\n用户需求：\n" + request.getDescription();

        log.info("开始生成系统架构图, archType={}, model={}", archType, modelName);
        String raw = model.chat(fullPrompt);
        JsonNode root = parseJson(raw);
        List<SystemArchitectureLayerVo> layers = parseLayers(root.path("layers"));
        String title = root.path("title").asText("系统架构图");

        return SystemArchitectureResponse.builder()
            .title(title)
            .archType(archType)
            .layers(layers)
            .build();
    }

    private String normalizeArchType(String archType) {
        if ("type2".equals(archType) || "type3".equals(archType)) {
            return archType;
        }
        return "type1";
    }

    private String loadSystemPrompt(String archType) {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return switch (archType) {
            case "type2" -> """
                你是系统架构师。根据描述输出分层系统架构图 JSON（5~6 层，精简版）。

                要求：
                1) layers 自上而下排列，每层含 label 与 items 数组
                2) 推荐层级：用户层、接入层、业务层、数据层、基础设施层（可按需调整）
                3) 每层 items 3~6 个，label 简洁中文
                4) id 唯一，仅输出 JSON
                """;
            case "type3" -> """
                你是系统架构师。根据描述输出微服务系统架构图 JSON（6~8 层）。

                要求：
                1) layers 自上而下，偏微服务/云原生（网关、服务、中间件、存储、监控等）
                2) 每层 items 4~8 个，可含具体技术组件名
                3) id 唯一，仅输出 JSON
                """;
            default -> """
                你是系统架构师。根据描述输出经典分层系统架构图 JSON（6~8 层）。

                要求：
                1) layers 数组自上而下，常见层级如：用户层、接入层、业务层、服务层、存储层、基础设施层、安全层（按场景取舍）
                2) 每层 items 3~8 个组件/模块，label 简洁中文或中英文
                3) 所有 id 唯一；不要 color 字段（前端自动配色）
                4) 仅输出 JSON，不要解释
                """;
        };
    }

    private String resolveModelName(String requestModel) {
        if (StringUtils.isNotBlank(requestModel)) {
            return requestModel;
        }
        String defaultModel = erDiagramProperties.getDefaultModel();
        if (StringUtils.isBlank(defaultModel)) {
            throw new ServiceException("未指定模型且未配置 chat.model.default-model");
        }
        return defaultModel;
    }

    private ChatModel buildModel(String modelName) {
        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new ServiceException("模型不存在: " + modelName);
        }
        return OpenAiChatModel.builder()
            .baseUrl(modelVo.getApiHost())
            .apiKey(modelVo.getApiKey())
            .modelName(modelVo.getModelName())
            .build();
    }

    private JsonNode parseJson(String raw) {
        try {
            String candidate = raw == null ? "" : raw.trim();
            if (candidate.contains("{")) {
                int start = candidate.indexOf('{');
                int end = candidate.lastIndexOf('}');
                candidate = candidate.substring(start, end + 1);
            }
            return objectMapper.readTree(candidate);
        }
        catch (Exception e) {
            log.error("解析系统架构 JSON 失败: {}", raw, e);
            throw new ServiceException("AI 返回格式错误，未能解析系统架构图数据");
        }
    }

    private List<SystemArchitectureLayerVo> parseLayers(JsonNode layersNode) {
        if (!layersNode.isArray() || layersNode.isEmpty()) {
            throw new ServiceException("AI 未生成任何架构层");
        }
        List<SystemArchitectureLayerVo> layers = new ArrayList<>();
        for (JsonNode layerNode : layersNode) {
            String id = layerNode.path("id").asText("");
            String label = layerNode.path("label").asText("");
            if (StringUtils.isBlank(id) || StringUtils.isBlank(label)) {
                continue;
            }
            SystemArchitectureLayerVo layer = new SystemArchitectureLayerVo();
            layer.setId(id);
            layer.setLabel(label);
            String color = layerNode.path("color").asText(null);
            if (StringUtils.isNotBlank(color)) {
                layer.setColor(color);
            }
            layer.setItems(parseItems(layerNode.path("items")));
            if (layer.getItems().isEmpty()) {
                continue;
            }
            layers.add(layer);
        }
        if (layers.isEmpty()) {
            throw new ServiceException("AI 未生成有效架构层");
        }
        return layers;
    }

    private List<SystemArchitectureItemVo> parseItems(JsonNode itemsNode) {
        List<SystemArchitectureItemVo> items = new ArrayList<>();
        if (!itemsNode.isArray()) {
            return items;
        }
        for (JsonNode itemNode : itemsNode) {
            String id = itemNode.path("id").asText("");
            String label = itemNode.path("label").asText("");
            if (StringUtils.isBlank(id) || StringUtils.isBlank(label)) {
                continue;
            }
            SystemArchitectureItemVo item = new SystemArchitectureItemVo();
            item.setId(id);
            item.setLabel(label);
            items.add(item);
        }
        return items;
    }
}

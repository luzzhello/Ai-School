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
import org.ruoyi.domain.dto.request.MindMapGenerateRequest;
import org.ruoyi.domain.dto.response.MindMapNodeVo;
import org.ruoyi.domain.dto.response.MindMapResponse;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.IMindMapService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MindMapServiceImpl implements IMindMapService {

    private static final String PROMPT_CODE = "mind_map";

    private static final String JSON_SCHEMA = """
        {
          "nodes": [
            { "id": "n0", "label": "中心主题", "level": 0 },
            { "id": "n1", "label": "分支一", "level": 1, "parentId": "n0" },
            { "id": "n2", "label": "子分支", "level": 2, "parentId": "n1" }
          ]
        }
        """;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;

    @Override
    public MindMapResponse generate(MindMapGenerateRequest request) {
        if (StringUtils.isBlank(request.getDescription())) {
            throw new ServiceException("主题描述不能为空");
        }
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String systemPrompt = loadSystemPrompt();
        String fullPrompt = systemPrompt + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + JSON_SCHEMA
            + "\n\n用户需求：\n" + request.getDescription();

        log.info("开始生成思维导图, model={}", modelName);
        String raw = model.chat(fullPrompt);
        JsonNode root = parseJson(raw);
        List<MindMapNodeVo> nodes = parseNodes(root.path("nodes"));

        String rootId = nodes.stream()
            .filter(n -> n.getLevel() != null && n.getLevel() == 0)
            .map(MindMapNodeVo::getId)
            .findFirst()
            .orElse(nodes.isEmpty() ? "" : nodes.get(0).getId());

        return MindMapResponse.builder()
            .nodes(nodes)
            .rootId(rootId)
            .build();
    }

    private String loadSystemPrompt() {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return """
            你是思维导图设计专家。根据用户描述输出思维导图 JSON（树形结构，不限层级）。

            要求：
            1) level=0 仅 1 个中心主题节点
            2) level=1 为 2~6 个主分支
            3) level>=2 为子主题，可按需继续展开
            4) 所有节点 id 唯一，parentId 正确引用父节点 id
            5) label 使用简洁中文，每条 2~12 字
            6) 仅输出 JSON，不要其他说明
            """;
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
            log.error("解析思维导图 JSON 失败: {}", raw, e);
            throw new ServiceException("AI 返回格式错误，未能解析思维导图数据");
        }
    }

    private List<MindMapNodeVo> parseNodes(JsonNode nodesNode) {
        if (!nodesNode.isArray() || nodesNode.isEmpty()) {
            throw new ServiceException("AI 未生成任何节点");
        }
        List<MindMapNodeVo> nodes = new ArrayList<>();
        for (JsonNode node : nodesNode) {
            String id = node.path("id").asText("");
            String label = node.path("label").asText("");
            if (StringUtils.isBlank(id) || StringUtils.isBlank(label)) {
                continue;
            }
            MindMapNodeVo vo = new MindMapNodeVo();
            vo.setId(id);
            vo.setLabel(label);
            int level = node.path("level").asInt(-1);
            vo.setLevel(Math.max(0, level));
            String parentId = node.path("parentId").asText(null);
            if (StringUtils.isNotBlank(parentId)) {
                vo.setParentId(parentId);
            }
            nodes.add(vo);
        }
        if (nodes.isEmpty()) {
            throw new ServiceException("AI 未生成有效节点");
        }
        return nodes;
    }
}

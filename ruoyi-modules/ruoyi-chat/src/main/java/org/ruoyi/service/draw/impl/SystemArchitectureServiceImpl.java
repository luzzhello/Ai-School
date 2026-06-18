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
import org.ruoyi.domain.dto.response.SystemArchitectureConnectionVo;
import org.ruoyi.domain.dto.response.SystemArchitectureItemVo;
import org.ruoyi.domain.dto.response.SystemArchitectureLayerVo;
import org.ruoyi.domain.dto.response.SystemArchitectureResponse;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.ISystemArchitectureService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
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
          "title": "零食超市系统架构图",
          "layers": [
            {
              "id": "presentation",
              "label": "表示层",
              "items": [
                { "id": "vue_frontend", "label": "零食超市系统前端 (Vue)" },
                { "id": "admin_frontend", "label": "后台管理前端" },
                { "id": "vo", "label": "VO" },
                { "id": "controller", "label": "Controller" }
              ]
            },
            {
              "id": "business",
              "label": "业务逻辑层",
              "items": [
                { "id": "service_iface", "label": "Service 接口" },
                { "id": "service_impl", "label": "Service 实现类" },
                { "id": "domain_model", "label": "业务领域模型" },
                { "id": "dto", "label": "DTO" }
              ]
            },
            {
              "id": "data_access",
              "label": "数据访问层",
              "items": [
                { "id": "mapper", "label": "MyBatis Mapper" },
                { "id": "entity", "label": "Entity (PO)" }
              ]
            },
            {
              "id": "infrastructure",
              "label": "基础设施与数据层",
              "items": [
                { "id": "mysql", "label": "MySQL 数据库" },
                { "id": "redis", "label": "Redis 缓存" }
              ]
            }
          ],
          "connections": [
            { "id": "c1", "from": "vue_frontend", "to": "controller" },
            { "id": "c2", "from": "admin_frontend", "to": "controller" },
            { "id": "c3", "from": "controller", "to": "service_iface" },
            { "id": "c4", "from": "service_iface", "to": "service_impl" },
            { "id": "c5", "from": "service_impl", "to": "mapper" },
            { "id": "c6", "from": "mapper", "to": "mysql" },
            { "id": "c7", "from": "service_impl", "to": "redis" }
          ]
        }
        """;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;
    private final IFeatureCoinService featureCoinService;

    @Override
    public SystemArchitectureResponse generate(SystemArchitectureGenerateRequest request) {
        if (StringUtils.isBlank(request.getDescription())) {
            throw new ServiceException("系统架构描述不能为空");
        }
        featureCoinService.requireAffordableForLoginUser(FeatureCodes.SYSTEM_ARCHITECTURE_AI, null);
        String archType = normalizeArchType(request.getArchType());
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String systemPrompt = loadSystemPrompt(archType);
        String fullPrompt = systemPrompt + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + JSON_SCHEMA
            + "\n\n要求：固定四层逻辑架构（表示层/业务逻辑层/数据访问层/基础设施与数据层），自上而下排列；"
            + "items 为具体组件，禁止把层名写入 items；connections 的 from/to 引用 item.id（可跨层）；"
            + "必须覆盖 前端→Controller→Service→Mapper→MySQL/Redis 完整调用链；"
            + "禁止出现 Docker/Kubernetes/ELK/Prometheus/Grafana 等运维监控组件；不要 color 与坐标字段。"
            + "\n\n用户需求：\n" + request.getDescription();

        log.info("开始生成系统架构图, archType={}, model={}", archType, modelName);
        String raw = model.chat(fullPrompt);
        JsonNode root = parseJson(raw);
        List<SystemArchitectureLayerVo> layers = parseLayers(root.path("layers"));
        List<SystemArchitectureConnectionVo> connections = parseConnections(root);
        String title = root.path("title").asText("系统架构图");

        featureCoinService.chargeForLoginUser(FeatureCodes.SYSTEM_ARCHITECTURE_AI, null);
        SystemArchitectureResponse build = SystemArchitectureResponse.builder()
            .title(title)
            .archType(archType)
            .layers(layers)
            .connections(connections)
            .build();
        log.info("生成系统架构图成功, archType={}, model={}, response={}", archType, modelName, build);
        return build;
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
                你是系统架构师。根据描述输出论文级标准逻辑分层架构图 JSON（固定四层，精简版）。

                四层结构（自上而下，layer.label 必须使用下列名称）：
                1) 表示层：系统前端、后台管理前端、VO、Controller
                2) 业务逻辑层：Service 接口、Service 实现类、DTO（可按模块增减）
                3) 数据访问层：MyBatis Mapper、Entity (PO)
                4) 基础设施与数据层：MySQL、Redis（如有）

                要求：每层 items 2~4 个；connections 覆盖 前端→Controller→Service→Mapper→MySQL/Redis；id 唯一；仅输出 JSON
                """;
            case "type3" -> """
                你是系统架构师。根据描述输出微服务云原生体系结构图 JSON（5~7 层）。

                要求：
                1) layers 含网关、微服务、中间件、存储、监控等
                2) connections 完整描述服务调用与数据访问关系
                3) id 唯一；仅输出 JSON
                """;
            default -> """
                你是系统架构师。根据描述输出论文级标准逻辑分层架构图 JSON（固定四层）。

                四层结构（自上而下，layer.label 必须使用下列名称）：
                1) 表示层 (Presentation Layer)：系统前端 (Vue/Tailwind CSS)、后台管理前端、VO、Controller
                   — 负责与用户交互，接收 HTTP 请求，返回 JSON 或视图
                2) 业务逻辑层 (Business Layer)：Service 接口、Service 实现类、业务领域模型、DTO
                   — 处理核心业务逻辑（订单处理、库存扣减、数据校验等）
                   — 禁止放入 Docker/Kubernetes/ELK/Prometheus/Grafana 等运维监控组件
                3) 数据访问层 (Data Access Layer)：DAO / Repository / MyBatis Mapper、Entity (PO)
                   — 负责与数据库交互，实现数据持久化
                4) 基础设施与数据层 (Infrastructure)：MySQL 数据库、Redis 缓存（如有）
                   — 提供底层数据存储与软件支撑

                要求：
                1) 固定 4 层，将前端/后端/数据库融合为一张统一的分层架构图，禁止拆成多张或多余层
                2) items 为具体组件，label 根据用户系统名称替换（如「零食超市系统前端」），不得把层名写入 items
                3) connections 必须覆盖 前端→Controller→Service→Mapper→MySQL/Redis 完整调用链
                4) 所有 id 唯一；不要 color 字段；仅输出 JSON
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

    private List<SystemArchitectureConnectionVo> parseConnections(JsonNode root) {
        JsonNode connNode = root.has("connections") ? root.path("connections") : root.path("edges");
        List<SystemArchitectureConnectionVo> connections = new ArrayList<>();
        if (!connNode.isArray()) {
            return connections;
        }
        for (JsonNode node : connNode) {
            String from = node.path("from").asText("");
            String to = node.path("to").asText("");
            if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {
                continue;
            }
            SystemArchitectureConnectionVo conn = new SystemArchitectureConnectionVo();
            conn.setId(node.path("id").asText("c_" + from + "_" + to));
            conn.setFrom(from);
            conn.setTo(to);
            String label = node.path("label").asText(null);
            if (StringUtils.isNotBlank(label)) {
                conn.setLabel(label);
            }
            connections.add(conn);
        }
        return connections;
    }
}

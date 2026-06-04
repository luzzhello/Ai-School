package org.ruoyi.service.draw.impl;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.model.chat.ChatModel;

import dev.langchain4j.model.openai.OpenAiChatModel;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;

import org.ruoyi.common.chat.service.chat.IChatModelService;

import org.ruoyi.common.core.exception.ServiceException;

import org.ruoyi.common.core.utils.StringUtils;

import org.ruoyi.config.ErDiagramProperties;

import org.ruoyi.domain.dto.request.SoftwareDiagramGenerateRequest;

import org.ruoyi.domain.dto.response.SoftwareDiagramResponse;

import org.ruoyi.domain.vo.chat.ChatPromptVo;

import org.ruoyi.service.chat.IChatPromptService;

import org.ruoyi.service.draw.ISoftwareDiagramService;

import org.ruoyi.service.draw.SoftwareDiagramTypes;

import org.springframework.stereotype.Service;



import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



@Slf4j

@Service

@RequiredArgsConstructor

public class SoftwareDiagramServiceImpl implements ISoftwareDiagramService {



    private static final String USECASE_JSON_SCHEMA = """

        {

          "actors": [{ "id": "user", "label": "用户" }],

          "useCases": [

            { "id": "uc1", "label": "注册登录", "actorId": "user" },

            { "id": "uc2", "label": "个人中心", "actorId": "user" },

            { "id": "uc21", "label": "订单管理", "parentId": "uc2" }

          ]

        }

        """;



    private static final String GRAPH_JSON_SCHEMA = """

        {

          "nodes": [

            { "id": "n1", "label": "类名", "shape": "rect", "x": 80, "y": 60, "width": 120, "height": 52 },

            { "id": "n2", "label": "判断", "shape": "diamond", "x": 80, "y": 160, "width": 100, "height": 80 }

          ],

          "edges": [

            { "id": "e1", "from": "n1", "to": "n2", "label": "关联" }

          ]

        }

        """;



    private static final String CLASS_JSON_SCHEMA = """

        {

          "classes": [

            {

              "id": "user",

              "name": "User",

              "attributes": ["- userId: String", "- username: String"],

              "methods": ["+ login(): void", "+ register(): void"]

            },

            {

              "id": "order",

              "name": "Order",

              "attributes": ["- orderId: String", "- amount: double"],

              "methods": ["+ create(): void", "+ pay(): void"]

            }

          ],

          "relations": [

            { "id": "r1", "from": "user", "to": "order", "type": "association", "label": "1..*" }

          ]

        }

        """;



    private static final String ACTIVITY_JSON_SCHEMA = """

        {

          "nodes": [

            { "id": "start", "kind": "start" },

            { "id": "a1", "kind": "activity", "label": "浏览商品" },

            { "id": "d1", "kind": "decision", "label": "是否登录?" },

            { "id": "a2", "kind": "activity", "label": "填写地址" },

            { "id": "end", "kind": "end" }

          ],

          "flows": [

            { "id": "f1", "from": "start", "to": "a1" },

            { "id": "f2", "from": "a1", "to": "d1" },

            { "id": "f3", "from": "d1", "to": "a2", "label": "[否]" },

            { "id": "f4", "from": "a2", "to": "end" }

          ]

        }

        """;



    private static final String SEQUENCE_JSON_SCHEMA = """

        {

          "participants": [

            { "id": "user", "label": "用户", "kind": "actor" },

            { "id": "login", "label": "登录页面", "kind": "object" },

            { "id": "auth", "label": "认证服务", "kind": "object" },

            { "id": "db", "label": "用户数据库", "kind": "database" }

          ],

          "messages": [

            { "id": "m1", "from": "user", "to": "login", "label": "输入用户名和密码", "type": "sync" },

            { "id": "m2", "from": "login", "to": "auth", "label": "发送认证请求", "type": "sync" },

            { "id": "m3", "from": "auth", "to": "db", "label": "查询用户信息", "type": "sync" },

            { "id": "m4", "from": "db", "to": "auth", "label": "返回用户信息", "type": "return" }

          ]

        }

        """;



    private static final Map<String, String> SHAPE_HINTS = Map.ofEntries(



        Map.entry("state", "状态 rect 或 circle，转移边 label 标注条件。"),

        Map.entry("object", "对象 rect，label 含 对象名:类名 及属性值。"),

        Map.entry("component", "构件 rect，依赖/接口用 edges 连接。"),

        Map.entry("dfd", "外部实体 rect，处理 rect，数据存储可用 ellipse；数据流 label 标注数据名。"),

        Map.entry("func_flow", "步骤 rect，判断 diamond，自上而下布局。"),

        Map.entry("func_structure", "系统/模块/功能 rect，树形层级用 edges 连接。"),

        Map.entry("architecture", "分层组件 rect，层间依赖用 edges。"),

        Map.entry("deployment", "节点 rect，部署构件 rect，连线 label 标注通信/部署关系。"),

        Map.entry("swimlane", "泳道内步骤 rect，跨泳道流转用 edges；label 可含泳道名。")

    );



    private final IChatModelService chatModelService;

    private final IChatPromptService chatPromptService;

    private final ErDiagramProperties erDiagramProperties;

    private final ObjectMapper objectMapper;



    @Override

    public SoftwareDiagramResponse generate(String diagramType, SoftwareDiagramGenerateRequest request) {

        SoftwareDiagramTypes.validate(diagramType);

        if (StringUtils.isBlank(request.getDescription())) {

            throw new ServiceException("图表描述不能为空");

        }



        String modelName = resolveModelName(request.getModel());

        ChatModel model = buildModel(modelName);

        String systemPrompt = loadSystemPrompt(diagramType);

        String styleHint = buildStyleHint(request.getStyle());

        String fullPrompt;



        if ("usecase".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + USECASE_JSON_SCHEMA

                + "\n\n用户需求：\n" + request.getDescription();

        }

        else if ("sequence".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + SEQUENCE_JSON_SCHEMA

                + "\n\n要求：participants.kind 取值 actor/object/database；messages 按时间顺序排列；"

                + "type=sync 表示实线调用，type=return 表示虚线返回，type=async 表示异步。"

                + "\n\n用户需求：\n" + request.getDescription();

        }

        else if ("class".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + CLASS_JSON_SCHEMA

                + "\n\n要求：classes 含 name/attributes/methods 三区；attributes 与方法以 +/-/# 表示可见性；"

                + "relations.type 取值 inheritance/association/aggregation/composition/dependency；"

                + "inheritance 的 from 为子类 to 为父类；label 可标注多重性。"

                + "\n\n用户需求：\n" + request.getDescription();

        }

        else if ("activity".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + ACTIVITY_JSON_SCHEMA

                + "\n\n要求：nodes.kind 取值 start/end/activity/decision；必须有 start 与 end；"

                + "flows 按流程顺序连接；decision 的分支 label 用 [是]/[否] 等。"

                + "\n\n用户需求：\n" + request.getDescription();

        }

        else {

            String shapeHint = SHAPE_HINTS.getOrDefault(diagramType, "节点 shape 可用 rect/ellipse/diamond/circle。");

            fullPrompt = systemPrompt

                + "\n\n输出可编辑画布 JSON（非 Mermaid）。节点 shape：rect=矩形，ellipse=椭圆，diamond=菱形，circle=圆形。"

                + "\n" + shapeHint

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + GRAPH_JSON_SCHEMA

                + "\n\n要求：id 唯一；label 中文；nodes 含 x/y/width/height 便于布局；edges 的 from/to 引用节点 id。"

                + "\n\n用户需求：\n" + request.getDescription();

        }

        fullPrompt = styleHint + "\n\n" + fullPrompt;

        log.info("开始生成软件工程图, type={}, style={}, promptCode={}, model={}",

            diagramType, blankToDefault(request.getStyle(), "professional"), SoftwareDiagramTypes.promptCode(diagramType), modelName);

        String raw = model.chat(fullPrompt);



        String content;

        if ("usecase".equals(diagramType)) {

            content = extractUseCaseJson(raw);

        }

        else if ("sequence".equals(diagramType)) {

            content = extractSequenceJson(raw);

        }

        else if ("class".equals(diagramType)) {

            content = extractClassJson(raw);

        }

        else if ("activity".equals(diagramType)) {

            content = extractActivityJson(raw);

        }

        else {

            content = extractGraphJson(raw);

        }



        return SoftwareDiagramResponse.builder()

            .mermaid(content)

            .diagramType(diagramType)

            .build();

    }



    private static String buildStyleHint(String style) {
        if ("modern".equalsIgnoreCase(blankToDefault(style, "professional"))) {
            return """
                【现代风格】请按现代 UI 审美组织图表语义与命名：
                - 标签简洁、偏产品化表述，避免冗长教材式描述
                - 类/模块划分清晰，关系适度聚合，减少杂乱交叉
                - 布局留白充足，节点命名统一英文或中文二选一
                - 活动/状态分支 label 简短（如「是」「否」）
                """;
        }
        return """
            【专业风格】请按经典 UML / 教材 / 课程作业规范输出：
            - 术语标准（参与者、用例、类名、可见性 +/-/#）
            - 结构完整、关系标注规范（多重性、角色名）
            - 命名偏学术与业务规范，适合论文与实验报告插图
            """;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String loadSystemPrompt(String diagramType) {

        String promptCode = SoftwareDiagramTypes.promptCode(diagramType);

        ChatPromptVo prompt = chatPromptService.queryByCode(promptCode);

        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {

            return prompt.getPromptContent();

        }

        return SoftwareDiagramTypes.defaultPrompt(diagramType);

    }



    private String extractUseCaseJson(String raw) {

        JsonNode root = parseJson(raw);

        JsonNode actors = root.path("actors");

        JsonNode useCases = root.path("useCases");

        if (!actors.isArray() || actors.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 actors");

        }

        if (!useCases.isArray() || useCases.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 useCases");

        }

        return writeJson(root);

    }



    private String extractSequenceJson(String raw) {

        JsonNode root = parseJson(raw);

        JsonNode participants = root.path("participants");

        JsonNode messages = root.path("messages");

        if (!participants.isArray() || participants.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 participants");

        }

        if (!messages.isArray() || messages.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 messages");

        }

        return writeJson(root);

    }



    private String extractClassJson(String raw) {

        JsonNode root = parseJson(raw);

        JsonNode classes = root.path("classes");

        JsonNode relations = root.path("relations");

        if (!classes.isArray() || classes.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 classes");

        }

        if (!relations.isArray()) {

            throw new ServiceException("AI 返回格式错误：缺少 relations");

        }

        return writeJson(root);

    }



    private String extractActivityJson(String raw) {

        JsonNode root = parseJson(raw);

        ObjectNode rootObj = unwrapActivityRoot(root);

        JsonNode nodes = rootObj.path("nodes");

        if (!nodes.isArray() || nodes.isEmpty()) {

            JsonNode activities = rootObj.path("activities");

            if (activities.isArray() && !activities.isEmpty()) {

                rootObj.set("nodes", activities);

                nodes = activities;

            }

        }

        if ((!nodes.isArray() || nodes.isEmpty()) && rootObj.path("steps").isArray() && !rootObj.path("steps").isEmpty()) {

            rootObj.set("nodes", rootObj.path("steps"));

            nodes = rootObj.path("nodes");

        }

        if ((!nodes.isArray() || nodes.isEmpty()) && nodes.isObject() && !nodes.isEmpty()) {

            rootObj.set("nodes", objectMapToNodesArray(nodes));

            nodes = rootObj.path("nodes");

        }

        if (!nodes.isArray() || nodes.isEmpty()) {

            ArrayNode laneNodes = flattenSwimlaneNodes(rootObj);

            if (laneNodes != null && !laneNodes.isEmpty()) {

                rootObj.set("nodes", laneNodes);

                nodes = laneNodes;

            }

        }

        if (!nodes.isArray() || nodes.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 nodes");

        }

        normalizeActivityNodes(rootObj);

        JsonNode flows = rootObj.path("flows");

        if (!flows.isArray()) {

            JsonNode edges = firstArray(rootObj, "edges", "transitions", "connections", "links");

            if (edges != null && edges.isArray()) {

                rootObj.set("flows", convertEdgesToFlows(edges));

            }

            else {

                rootObj.set("flows", objectMapper.createArrayNode());

            }

        }

        return writeJson(rootObj);

    }



    private ObjectNode unwrapActivityRoot(JsonNode root) {

        ObjectNode current = root.isObject() ? (ObjectNode) root.deepCopy() : objectMapper.createObjectNode();

        for (int i = 0; i < 4; i++) {

            JsonNode nested = firstObject(current, "data", "result", "diagram", "content", "activityDiagram", "activity", "payload");

            if (nested == null || !nested.isObject()) {

                break;

            }

            current = (ObjectNode) nested.deepCopy();

        }

        return current;

    }



    private JsonNode firstObject(ObjectNode obj, String... fields) {

        for (String field : fields) {

            JsonNode node = obj.path(field);

            if (node.isObject()) {

                return node;

            }

        }

        return null;

    }



    private JsonNode firstArray(ObjectNode obj, String... fields) {

        for (String field : fields) {

            JsonNode node = obj.path(field);

            if (node.isArray()) {

                return node;

            }

        }

        return null;

    }



    private ArrayNode objectMapToNodesArray(JsonNode mapNode) {

        ArrayNode nodes = objectMapper.createArrayNode();

        mapNode.fields().forEachRemaining(entry -> {

            JsonNode value = entry.getValue();

            ObjectNode node = value.isObject() ? ((ObjectNode) value).deepCopy() : objectMapper.createObjectNode();

            if (!node.has("id")) {

                node.put("id", entry.getKey());

            }

            if (!value.isObject()) {

                node.put("label", value.asText());

                node.put("kind", "activity");

            }

            nodes.add(node);

        });

        return nodes;

    }



    private ArrayNode flattenSwimlaneNodes(ObjectNode rootObj) {

        JsonNode lanes = firstArray(rootObj, "swimlanes", "lanes", "partitions");

        if (lanes == null) {

            return null;

        }

        ArrayNode merged = objectMapper.createArrayNode();

        for (JsonNode lane : lanes) {

            if (!lane.isObject()) {

                continue;

            }

            JsonNode laneNodes = null;

            if (lane.isObject()) {

                laneNodes = firstArray((ObjectNode) lane, "nodes", "activities", "steps");

            }

            if (laneNodes != null) {

                laneNodes.forEach(merged::add);

            }

        }

        return merged.isEmpty() ? null : merged;

    }



    private void normalizeActivityNodes(ObjectNode rootObj) {

        JsonNode nodes = rootObj.path("nodes");

        if (!nodes.isArray()) {

            return;

        }

        for (JsonNode node : nodes) {

            if (!node.isObject()) {

                continue;

            }

            ObjectNode obj = (ObjectNode) node;

            if (!obj.has("kind") && obj.has("type")) {

                obj.set("kind", obj.get("type"));

            }

            if (!obj.has("kind") && obj.has("shape")) {

                String shape = obj.get("shape").asText("").toLowerCase();

                if ("diamond".equals(shape) || "rhombus".equals(shape)) {

                    obj.put("kind", "decision");

                }

                else if ("circle".equals(shape) || "ellipse".equals(shape)) {

                    String label = obj.path("label").asText("").toLowerCase();

                    if (label.contains("结束") || label.contains("end") || label.contains("final")) {

                        obj.put("kind", "end");

                    }

                    else {

                        obj.put("kind", "start");

                    }

                }

            }

        }

    }



    private ArrayNode convertEdgesToFlows(JsonNode edges) {

        ArrayNode flows = objectMapper.createArrayNode();

        int i = 0;

        for (JsonNode edge : edges) {

            if (!edge.isObject()) {

                continue;

            }

            String from = edgeText(edge, "from", "source", "src", "start", "fromId", "sourceId");

            String to = edgeText(edge, "to", "target", "tgt", "end", "toId", "targetId");

            if (StringUtils.isBlank(from) || StringUtils.isBlank(to)) {

                continue;

            }

            ObjectNode flow = objectMapper.createObjectNode();

            flow.put("id", edge.has("id") ? edge.get("id").asText() : ("f" + (++i)));

            flow.put("from", from);

            flow.put("to", to);

            if (edge.has("label")) {

                flow.put("label", edge.get("label").asText());

            }

            flows.add(flow);

        }

        return flows;

    }



    private String edgeText(JsonNode edge, String... fields) {

        for (String field : fields) {

            if (edge.has(field) && StringUtils.isNotBlank(edge.get(field).asText())) {

                return edge.get(field).asText().trim();

            }

        }

        return "";

    }



    private String extractGraphJson(String raw) {

        JsonNode root = parseJson(raw);

        JsonNode nodes = root.path("nodes");

        JsonNode edges = root.path("edges");

        if (!nodes.isArray() || nodes.isEmpty()) {

            throw new ServiceException("AI 返回格式错误：缺少 nodes");

        }

        if (!edges.isArray()) {

            throw new ServiceException("AI 返回格式错误：缺少 edges");

        }

        return writeJson(root);

    }



    private String writeJson(JsonNode root) {

        try {

            return objectMapper.writeValueAsString(root);

        }

        catch (Exception e) {

            throw new ServiceException("AI 返回格式错误，未能解析 JSON");

        }

    }



    private JsonNode parseJson(String raw) {

        if (StringUtils.isBlank(raw)) {

            throw new ServiceException("AI 未返回图表内容");

        }

        String candidate = raw.trim();

        Matcher m = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE).matcher(candidate);

        if (m.find()) {

            candidate = m.group(1).trim();

        }

        else if (candidate.contains("```")) {

            int start = candidate.indexOf("```");

            int end = candidate.lastIndexOf("```");

            if (end > start) {

                candidate = candidate.substring(start + 3, end).trim();

                if (candidate.startsWith("json")) {

                    candidate = candidate.substring(4).trim();

                }

            }

        }

        int jsonStart = candidate.indexOf("{");

        int jsonEnd = candidate.lastIndexOf("}");

        if (jsonStart >= 0 && jsonEnd > jsonStart) {

            candidate = candidate.substring(jsonStart, jsonEnd + 1);

        }

        try {

            return objectMapper.readTree(candidate);

        }

        catch (Exception e) {

            throw new ServiceException("AI 返回格式错误，未能解析 JSON");

        }

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

}



package org.ruoyi.service.draw.impl;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.model.chat.ChatModel;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

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


import java.util.HashMap;
import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;


@Slf4j

@Service

@RequiredArgsConstructor

public class SoftwareDiagramServiceImpl implements ISoftwareDiagramService {


    private static final String USECASE_JSON_SCHEMA = """

        {

          "actors": [

            { "id": "student", "label": "学生" },

            { "id": "teacher", "label": "教师督导" },

            { "id": "admin", "label": "管理员" }

          ],

          "useCases": [

            { "id": "uc1", "label": "课程学习", "actorId": "student" },

            { "id": "uc2", "label": "作业提交", "actorId": "student" },

            { "id": "uc3", "label": "课程管理", "actorId": "teacher" },

            { "id": "uc4", "label": "用户管理", "actorId": "admin" }

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

            { "id": "user", "name": "User", "attributes": ["- userId: Long", "- username: String"], "methods": ["+ login(username: String, password: String): boolean"] },

            { "id": "order", "name": "Order", "attributes": ["- orderId: Long", "- totalAmount: BigDecimal"], "methods": ["+ create(): void", "+ pay(): boolean"] },

            { "id": "order_item", "name": "OrderItem", "attributes": ["- quantity: int", "- unitPrice: double"], "methods": ["+ calcSubtotal(): double"] },

            { "id": "product", "name": "Product", "attributes": ["- productId: Long", "- name: String", "- price: double"], "methods": ["+ checkStock(qty: int): boolean"] },

            { "id": "payment", "name": "Payment", "attributes": ["- paymentId: Long", "- amount: double"], "methods": ["+ processPayment(): boolean"] }

          ],

          "relations": [

            { "id": "r1", "from": "user", "to": "order", "type": "association", "fromMultiplicity": "1", "toMultiplicity": "0..*" },

            { "id": "r2", "from": "order", "to": "order_item", "type": "composition", "fromMultiplicity": "1", "toMultiplicity": "1..*" },

            { "id": "r3", "from": "order_item", "to": "product", "type": "aggregation", "fromMultiplicity": "1..*", "toMultiplicity": "1" },

            { "id": "r4", "from": "order", "to": "payment", "type": "association", "fromMultiplicity": "1", "toMultiplicity": "0..1" }

          ]

        }

        """;


    private static final String ACTIVITY_JSON_SCHEMA = """

        {

          "nodes": [

            { "id": "start", "kind": "start" },

            { "id": "a_login", "kind": "activity", "label": "用户登录" },

            { "id": "d_verify", "kind": "decision", "label": "验证成功?" },

            { "id": "a_error", "kind": "activity", "label": "显示错误信息" },

            { "id": "a_home", "kind": "activity", "label": "显示主页" },

            { "id": "a_select", "kind": "activity", "label": "用户选择功能" },

            { "id": "d_func", "kind": "decision", "label": "功能类型?" },

            { "id": "a_view", "kind": "activity", "label": "显示信息" },

            { "id": "a_exec", "kind": "activity", "label": "执行操作" },

            { "id": "a_update", "kind": "activity", "label": "更新数据" },

            { "id": "m_branch", "kind": "decision", "label": "合流" },

            { "id": "m_final", "kind": "decision", "label": "合流" },

            { "id": "end", "kind": "end" }

          ],

          "flows": [

            { "id": "f1", "from": "start", "to": "a_login" },

            { "id": "f2", "from": "a_login", "to": "d_verify" },

            { "id": "f3", "from": "d_verify", "to": "a_error", "label": "[否]" },

            { "id": "f4", "from": "d_verify", "to": "a_home", "label": "[是]" },

            { "id": "f5", "from": "a_home", "to": "a_select" },

            { "id": "f6", "from": "a_select", "to": "d_func" },

            { "id": "f7", "from": "d_func", "to": "a_view", "label": "[查看]" },

            { "id": "f8", "from": "d_func", "to": "a_exec", "label": "[操作]" },

            { "id": "f9", "from": "a_exec", "to": "a_update" },

            { "id": "f10", "from": "a_view", "to": "m_branch" },

            { "id": "f11", "from": "a_update", "to": "m_branch" },

            { "id": "f12", "from": "m_branch", "to": "m_final" },

            { "id": "f13", "from": "a_error", "to": "m_final" },

            { "id": "f14", "from": "m_final", "to": "end" }

          ]

        }

        """;


    private static final String ARCHITECTURE_JSON_SCHEMA = """

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

        Map.entry("func_flow", """
            标准流程图符号：开始/结束 ellipse（label=开始/结束各 1 个）；处理 rect；判断 diamond。
            每个判断节点必须至少 2 条出边，且每条出边必须有 label（是/否 或 通过/不通过）。
            禁止出现多个“结束”节点：全图只能有 1 个 label=结束 的 ellipse；所有分支最终汇合到同一结束节点。
            主流程自上而下，分支左右展开，尽量避免交叉线；nodes 填写合理 x/y/width/height。
            """),

        Map.entry("func_structure", "系统/模块/功能 rect，树形层级用 edges 连接。"),

        Map.entry("architecture", """
            分层架构 JSON：layers（表现层/业务层/数据层）+ connections（item.id 跨层连线）。
            必须包含网关汇聚、服务调用、数据库访问完整链路；不要 color 与坐标。
            """),

        Map.entry("deployment", "节点 rect，部署构件 rect，连线 label 标注通信/部署关系。"),

        Map.entry("swimlane", "泳道内步骤 rect，跨泳道流转用 edges；label 可含泳道名。")

    );


    private final IChatModelService chatModelService;

    private final IChatPromptService chatPromptService;

    private final ErDiagramProperties erDiagramProperties;

    private final ObjectMapper objectMapper;

    private final org.ruoyi.service.usercenter.IFeatureCoinService featureCoinService;

    @Override
    public SoftwareDiagramResponse generate(String diagramType, SoftwareDiagramGenerateRequest request) {
        SoftwareDiagramTypes.validate(diagramType);

        if (StringUtils.isBlank(request.getDescription())) {

            throw new ServiceException("图表描述不能为空");

        }
        featureCoinService.requireAffordableForLoginUser(org.ruoyi.service.usercenter.FeatureCodes.SOFTWARE_DIAGRAM_AI, null);


        String modelName = resolveModelName(request.getModel());

        ChatModel model = DrawChatModelSupport.buildModel(chatModelService, modelName);

        String systemPrompt = loadSystemPrompt(diagramType);

        String styleHint = buildStyleHint(request.getStyle());

        String fullPrompt;


        if ("usecase".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + USECASE_JSON_SCHEMA

                + "\n\n要求：所有用例仅通过 actorId 关联参与者，禁止 parentId 子用例。"

                + "\n\n用户需求：\n" + request.getDescription();

        } else if ("sequence".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + SEQUENCE_JSON_SCHEMA

                + "\n\n要求：participants.kind 取值 actor/object/database；messages 按时间顺序排列；"

                + "type=sync 表示实线调用，type=return 表示虚线返回，type=async 表示异步。"

                + "\n\n用户需求：\n" + request.getDescription();

        } else if ("class".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + CLASS_JSON_SCHEMA

                + "\n\n要求：三格类框；英文驼峰；可见性仅用 +/-/#/~；仅 4~8 个核心类；禁止 getter/setter/log；"

                + "relations 必须设 fromMultiplicity 与 toMultiplicity（分别贴近连线两端，禁止只写一个 label）；"

                + "电商语义：User-Order 关联；Order-OrderItem 组合(composition)；OrderItem-Product 聚合(aggregation)；Order-Payment 关联；禁止 Payment-Product 直连；"

                + "type：inheritance/implementation/association/aggregation/composition/dependency；组合/聚合菱形在 from（整体）端，无箭头。"

                + "\n\n用户需求：\n" + request.getDescription();

        } else if ("activity".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + ACTIVITY_JSON_SCHEMA

                + "\n\n要求：仅输出 nodes + flows，禁止 swimlanes/lane；单一用户视角，自上而下主流程；"

                + "全图只能 1 个 start 与 1 个 end（kind=end 牛眼终止符）；"

                + "nodes.kind 取值 start/end/activity/decision；必须有 start 与 end；"

                + "decision 为菱形判断，分支条件写在 flows.label（[是]/[否]/[查看]/[操作]），禁止把条件做成活动节点；"

                + "分支布局：[是] 沿中轴向下，[否] 向右，[查看]/[查询] 向左，[操作]/[办理] 向右，禁止成功路径画到左侧；"

                + "语义硬约束：[否] 只能连错误提示活动，禁止连办理/更新主干；更新/办理后须「是否成功?」再分成功/失败，禁止更新直连提示错误；"

                + "禁止「角色类型?」及 [教师]/[管理员]/[普通用户] 多角色三分支；"

                + "分支合流用 kind=decision 且 label=合流 的 merge 菱形，禁止 fork/join 与多角色泳道；"

                + "参照示例：登录→验证→功能选择→两次合流→end 牛眼终止。"

                + "\n\n用户需求：\n" + request.getDescription();

        } else if ("architecture".equals(diagramType)) {

            fullPrompt = systemPrompt

                + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + ARCHITECTURE_JSON_SCHEMA

                + "\n\n要求：仅 layers+items+connections，禁止根级 nodes/edges；items 不得为层名；"
                + "固定四层：表示层/业务逻辑层/数据访问层/基础设施与数据层；connections 禁止 label；"
                + "覆盖 前端→Controller→Service→Mapper→MySQL/Redis 主链路；"
                + "禁止 Docker/Kubernetes/ELK/Prometheus/Grafana 等运维监控组件；不要 color 与坐标字段。"

                + "\n\n用户需求：\n" + request.getDescription();

        } else {

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

        String raw = DrawChatModelSupport.chat(model, fullPrompt);


        String content;

        if ("usecase".equals(diagramType)) {

            content = extractUseCaseJson(raw);

        } else if ("sequence".equals(diagramType)) {

            content = extractSequenceJson(raw);

        } else if ("class".equals(diagramType)) {

            content = extractClassJson(raw);

        } else if ("activity".equals(diagramType)) {

            content = extractActivityJson(raw);

        } else if ("architecture".equals(diagramType)) {

            content = extractArchitectureJson(raw);

        } else {

            content = extractGraphJson(raw);

        }

        log.info("生成软件工程图完成, type={}, style={}, promptCode={}, model={}, content={}",
            diagramType, blankToDefault(request.getStyle(), "professional"), SoftwareDiagramTypes.promptCode(diagramType), modelName, content);
        featureCoinService.chargeForLoginUser(org.ruoyi.service.usercenter.FeatureCodes.SOFTWARE_DIAGRAM_AI, null);
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
            【专业风格】请按毕业论文 UML 类图规范输出：
            - 类名/属性/方法英文驼峰，可见性仅用 +/-/#/~
            - 局部模块 4~8 类，剔除 getter/setter/toString/log
            - 泛化=实线空心三角，实现=虚线空心三角，依赖=虚线开放箭头
            - 关联/聚合/组合标注多重性（1、*、1..*）
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

        flattenUseCases((ObjectNode) root);

        return writeJson(root);

    }


    /**
     * 去掉 parentId，将子用例提升为参与者下的一级用例
     */
    private void flattenUseCases(ObjectNode root) {

        JsonNode useCases = root.path("useCases");

        if (!useCases.isArray()) {

            return;

        }

        Map<String, JsonNode> byId = new HashMap<>();

        for (JsonNode uc : useCases) {

            String id = uc.path("id").asText(null);

            if (StringUtils.isNotBlank(id)) {

                byId.put(id, uc);

            }

        }

        ArrayNode flat = objectMapper.createArrayNode();

        for (JsonNode uc : useCases) {

            ObjectNode copy = uc.deepCopy();

            if (copy.hasNonNull("parentId")) {

                String actorId = resolveActorIdForUseCase(copy, byId);

                if (StringUtils.isNotBlank(actorId)) {

                    copy.put("actorId", actorId);

                }

                copy.remove("parentId");

            }

            flat.add(copy);

        }

        root.set("useCases", flat);

    }


    private String resolveActorIdForUseCase(ObjectNode uc, Map<String, JsonNode> byId) {

        if (uc.hasNonNull("actorId")) {

            return uc.get("actorId").asText();

        }

        String parentId = uc.path("parentId").asText(null);

        int guard = 0;

        while (StringUtils.isNotBlank(parentId) && guard++ < 20) {

            JsonNode parent = byId.get(parentId);

            if (parent == null) {

                break;

            }

            if (parent.hasNonNull("actorId")) {

                return parent.get("actorId").asText();

            }

            parentId = parent.path("parentId").asText(null);

        }

        return null;

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


    private String extractArchitectureJson(String raw) {

        JsonNode root = parseJson(raw);

        JsonNode layers = root.path("layers");

        if (layers.isArray() && !layers.isEmpty()) {

            return writeJson(root);

        }

        JsonNode nodes = root.path("nodes");

        if (nodes.isArray() && !nodes.isEmpty()) {

            return writeJson(root);

        }

        throw new ServiceException("AI 返回格式错误：缺少 layers 或 nodes");

    }


    private String extractActivityJson(String raw) {

        JsonNode root = parseJson(raw);

        if (root.isArray()) {

            if (root.size() == 1 && root.get(0).isObject()) {

                root = root.get(0);

            } else {

                ObjectNode wrapper = objectMapper.createObjectNode();

                wrapper.set("nodes", root);

                root = wrapper;

            }

        }

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

        if (!nodes.isArray() || nodes.isEmpty()) {

            for (String field : new String[]{"elements", "states", "actions", "nodeList"}) {

                JsonNode alt = rootObj.path(field);

                if (alt.isArray() && !alt.isEmpty()) {

                    rootObj.set("nodes", alt);

                    nodes = alt;

                    break;

                }

            }

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
        stripActivitySwimlanes(rootObj);

        JsonNode flows = rootObj.path("flows");

        if (!flows.isArray()) {

            JsonNode edges = firstArray(rootObj, "edges", "transitions", "connections", "links");

            if (edges != null && edges.isArray()) {

                rootObj.set("flows", convertEdgesToFlows(edges));

            } else {

                rootObj.set("flows", objectMapper.createArrayNode());

            }

        } else if (flows.isEmpty()) {

            ArrayNode seqFlows = buildSequentialActivityFlows(rootObj.path("nodes"));

            if (seqFlows != null && !seqFlows.isEmpty()) {

                rootObj.set("flows", seqFlows);

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


    private void stripActivitySwimlanes(ObjectNode rootObj) {

        rootObj.remove("swimlanes");

        rootObj.remove("lanes");

        rootObj.remove("partitions");

        JsonNode nodes = rootObj.path("nodes");

        if (!nodes.isArray()) {

            return;

        }

        for (JsonNode node : nodes) {

            if (!node.isObject()) {

                continue;

            }

            ObjectNode obj = (ObjectNode) node;

            obj.remove("lane");

            obj.remove("swimlane");

            obj.remove("partition");

            obj.remove("laneId");

        }

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

            if (!obj.has("kind") && obj.has("nodeType")) {

                obj.set("kind", obj.get("nodeType"));

            }

            if (!obj.has("kind") && obj.has("shape")) {

                String shape = obj.get("shape").asText("").toLowerCase();

                if ("diamond".equals(shape) || "rhombus".equals(shape)) {

                    obj.put("kind", "decision");

                } else if ("circle".equals(shape) || "ellipse".equals(shape)) {

                    String label = obj.path("label").asText("").toLowerCase();

                    if (label.contains("结束") || label.contains("end") || label.contains("final")) {

                        obj.put("kind", "end");

                    } else {

                        obj.put("kind", "start");

                    }

                }

            }

            if (obj.has("kind")) {

                normalizeActivityKindField(obj);

            }

        }

    }


    private void normalizeActivityKindField(ObjectNode obj) {

        String kind = obj.get("kind").asText("").trim().toLowerCase();

        if ("action".equals(kind) || "task".equals(kind) || "step".equals(kind) || "process".equals(kind)) {

            obj.put("kind", "activity");

        } else if ("condition".equals(kind) || "branch".equals(kind) || "gateway".equals(kind)) {

            obj.put("kind", "decision");

        } else if ("fork".equals(kind) || "parallel".equals(kind)) {

            obj.put("kind", "fork");

        } else if ("join".equals(kind) || "synchronizer".equals(kind) || "同步".equals(kind)) {

            obj.put("kind", "join");

        } else if ("merge".equals(kind) || "汇合".equals(kind) || "合流".equals(kind)) {

            obj.put("kind", "decision");

            if (!obj.has("label") || StringUtils.isBlank(obj.path("label").asText())) {

                obj.put("label", "合流");

            }

        } else if ("initial".equals(kind) || "initialnode".equals(kind)) {

            obj.put("kind", "start");

        } else if ("final".equals(kind) || "finalnode".equals(kind) || "terminate".equals(kind) || "termination".equals(kind)) {

            obj.put("kind", "end");

        }

    }


    private ArrayNode buildSequentialActivityFlows(JsonNode nodes) {

        if (!nodes.isArray() || nodes.size() < 2) {

            return null;

        }

        ArrayNode flows = objectMapper.createArrayNode();

        for (int i = 0; i < nodes.size() - 1; i++) {

            JsonNode fromNode = nodes.get(i);

            JsonNode toNode = nodes.get(i + 1);

            if (!fromNode.isObject() || !toNode.isObject()) {

                continue;

            }

            String fromId = activityNodeIdText(fromNode, i);

            String toId = activityNodeIdText(toNode, i + 1);

            if (StringUtils.isBlank(fromId) || StringUtils.isBlank(toId)) {

                continue;

            }

            ObjectNode flow = objectMapper.createObjectNode();

            flow.put("id", "f" + (i + 1));

            flow.put("from", fromId);

            flow.put("to", toId);

            flows.add(flow);

        }

        return flows.isEmpty() ? null : flows;

    }


    private String activityNodeIdText(JsonNode node, int index) {

        if (node.has("id")) {

            return node.get("id").asText();

        }

        if (node.has("key")) {

            return node.get("key").asText();

        }

        if (node.has("code")) {

            return node.get("code").asText();

        }

        return "n" + (index + 1);

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

        } catch (Exception e) {

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

        } else if (candidate.contains("```")) {

            int start = candidate.indexOf("```");

            int end = candidate.lastIndexOf("```");

            if (end > start) {

                candidate = candidate.substring(start + 3, end).trim();

                if (candidate.startsWith("json")) {

                    candidate = candidate.substring(4).trim();

                }

            }

        }

        int arrStart = candidate.indexOf("[");

        int arrEnd = candidate.lastIndexOf("]");

        int jsonStart = candidate.indexOf("{");

        int jsonEnd = candidate.lastIndexOf("}");

        if (arrStart >= 0 && (jsonStart < 0 || arrStart < jsonStart) && arrEnd > arrStart) {

            candidate = candidate.substring(arrStart, arrEnd + 1);

        } else if (jsonStart >= 0 && jsonEnd > jsonStart) {

            candidate = candidate.substring(jsonStart, jsonEnd + 1);

        }

        try {

            return objectMapper.readTree(candidate);

        } catch (Exception e) {

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


}



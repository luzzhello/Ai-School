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
import org.ruoyi.domain.dto.request.ErDiagramGenerateRequest;
import org.ruoyi.domain.dto.request.ErSqlOptimizeRequest;
import org.ruoyi.domain.dto.response.ErDiagramResponse;
import org.ruoyi.domain.dto.response.ErEntityAttributeDiagramVo;
import org.ruoyi.domain.dto.response.ErEntityMetaVo;
import org.ruoyi.domain.dto.response.ErNodeVo;
import org.ruoyi.domain.dto.response.ErSqlOptimizeResponse;
import org.ruoyi.domain.dto.response.ErSqlTestResponse;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.IErDiagramService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErDiagramServiceImpl implements IErDiagramService {

    private static final String PROMPT_CODE = "er_diagram";

    private static final String SQL_OPTIMIZE_SYSTEM_PROMPT = """
        你是数据库 SQL 优化专家。用户会给出待优化的建表 SQL 与规则。
        你必须严格按规则输出完整优化后的 SQL，不要输出 markdown 代码块标记，不要任何解释说明。
        """;

    private static final String SQL_OPTIMIZE_USER_TEMPLATE = """
        请帮我优化以下SQL，要求：
        1. 所有表和字段添加COMMENT中文简洁注释(严格≤4个汉字)
        2. 表和表之间也要加上外键关系
        3. 为每个外键关系添加COMMENT注释，注释必须使用单个动词短语准确描述两表业务关系(如"隶属于"、"管理"等)
        4. 外键关系必须严格按照以下格式定义(不要添加或删除任何空格或符号，不要使用单引号)：
           `FOREIGN KEY (列名) REFERENCES 表名(列名) COMMENT '关系描述'`
        5. 所有外键定义保持在CREATE TABLE语句内
        6. 表本身也要添加COMMENT注释
        7. 严格保持原SQL功能不变，仅添加注释和完善关系
        8. 直接返回完整优化后的SQL代码，无需任何额外解释

        我的SQL:
        %s
        """;

    private static final String JSON_SCHEMA = """
        {
          "entities": [
            {
              "name": "实体中文名",
              "attributes": ["属性1", "属性2", "属性3"]
            }
          ],
          "relationships": [
            {
              "name": "关系名",
              "entityA": "实体A中文名",
              "entityB": "实体B中文名",
              "cardinalityA": "1",
              "cardinalityB": "n"
            }
          ]
        }
        """;

    private final IChatModelService chatModelService;
    private final IChatPromptService chatPromptService;
    private final ErDiagramProperties erDiagramProperties;
    private final ObjectMapper objectMapper;
    private final IFeatureCoinService featureCoinService;

    @Override
    public ErDiagramResponse generate(ErDiagramGenerateRequest request) {
        validateRequest(request);
        if (isSqlMode(request)) {
            return generateFromSql(request.getSql());
        }
        return generateFromAi(request);
    }

    @Override
    public ErSqlOptimizeResponse optimizeSql(ErSqlOptimizeRequest request) {
        if (StringUtils.isBlank(request.getSql())) {
            throw new ServiceException("SQL 语句不能为空");
        }
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String userPrompt = SQL_OPTIMIZE_USER_TEMPLATE.formatted(request.getSql().trim());
        log.info("AI 优化 ER 图 SQL, model={}", modelName);
        String raw = model.chat(SQL_OPTIMIZE_SYSTEM_PROMPT + "\n\n" + userPrompt);
        String optimized = extractSqlFromAiResponse(raw);
        if (StringUtils.isBlank(optimized)) {
            throw new ServiceException("AI 未返回有效 SQL");
        }
        return ErSqlOptimizeResponse.builder().sql(optimized).build();
    }

    @Override
    public ErSqlTestResponse testSql(String sql) {
        SqlChenErParser.ParseResult result = SqlChenErParser.parse(sql);
        List<String> labels = result.entities().stream()
            .map(ChenErLayoutBuilder.EntityDef::name)
            .toList();
        return ErSqlTestResponse.builder()
            .tableCount(labels.size())
            .tableLabels(labels)
            .message("解析成功，共识别 " + labels.size() + " 个实体，可点击「生成」绘制 ER 图")
            .build();
    }

    private boolean isSqlMode(ErDiagramGenerateRequest request) {
        return "sql".equalsIgnoreCase(request.getMode());
    }

    private ErDiagramResponse generateFromSql(String sql) {
        featureCoinService.requireAffordableForLoginUser(FeatureCodes.ER_SQL, null);
        log.info("本地解析 SQL 生成陈氏 ER 图");
        SqlChenErParser.ParseResult parsed = SqlChenErParser.parse(sql);
        ChenErLayoutBuilder.ErDiagramBundle bundle = ChenErLayoutBuilder.buildBundle(
            parsed.entities(),
            parsed.relationships()
        );
        featureCoinService.chargeForLoginUser(FeatureCodes.ER_SQL, null);
        return toResponse(bundle, parsed.entities());
    }

    private ErDiagramResponse generateFromAi(ErDiagramGenerateRequest request) {
        featureCoinService.requireAffordableForLoginUser(FeatureCodes.ER_AI, null);
        String modelName = resolveModelName(request.getModel());
        ChatModel model = buildModel(modelName);
        String userInput = buildUserInput(request);
        String systemPrompt = loadSystemPrompt();
        String fullPrompt = systemPrompt + "\n\n严格输出 JSON，不要 markdown 代码块，结构如下：\n" + JSON_SCHEMA
            + "\n\n用户需求：\n" + userInput;

        log.info("AI 生成陈氏 ER 图, mode={}, model={}", request.getMode(), modelName);
        String raw = model.chat(fullPrompt);
        JsonNode root = parseJson(raw);

        List<ChenErLayoutBuilder.EntityDef> entities = parseEntities(root.path("entities"));
        List<ChenErLayoutBuilder.RelationshipDef> relationships = parseRelationships(root.path("relationships"));
        ChenErLayoutBuilder.ErDiagramBundle bundle = ChenErLayoutBuilder.buildBundle(entities, relationships);

        featureCoinService.chargeForLoginUser(FeatureCodes.ER_AI, null);
        return toResponse(bundle, entities);
    }

    private void validateRequest(ErDiagramGenerateRequest request) {
        if (isSqlMode(request)) {
            if (StringUtils.isBlank(request.getSql())) {
                throw new ServiceException("SQL 语句不能为空");
            }
        }
        else if (StringUtils.isBlank(request.getDescription())) {
            throw new ServiceException("系统描述不能为空");
        }
    }

    private String buildUserInput(ErDiagramGenerateRequest request) {
        return request.getDescription();
    }

    private String extractSqlFromAiResponse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String candidate = raw.trim();
        if (candidate.contains("```")) {
            int start = candidate.indexOf("```");
            int lineEnd = candidate.indexOf('\n', start);
            int contentStart = lineEnd >= 0 ? lineEnd + 1 : start + 3;
            int end = candidate.indexOf("```", contentStart);
            if (end > contentStart) {
                candidate = candidate.substring(contentStart, end).trim();
            }
        }
        return candidate.trim();
    }

    private String loadSystemPrompt() {
        ChatPromptVo prompt = chatPromptService.queryByCode(PROMPT_CODE);
        if (prompt != null && StringUtils.isNotBlank(prompt.getPromptContent())) {
            return prompt.getPromptContent();
        }
        return """
            你是数据库概念设计专家。根据业务描述输出陈氏 ER 图 JSON（概念层，不是物理表结构）。

            样式参照教材：
            - 总体 E-R 图（图 4.6）：仅含矩形实体 + 菱形关系 + 基数 1/n，不要在总体图中画属性
            - 每个实体单独一张属性图（图 4.7）：实体在下方，4~8 个属性写在 attributes 里供分图使用

            要求：
            1) entities：实体中文名 + attributes 属性列表（4~8 个，第一个为主键/编号类）
            2) relationships：每条关系是二元关系，菱形 name 为动词短语，entityA/entityB 各引用一个已有实体
            3) 禁止一个菱形同时关联三个及以上实体；管理员与多个业务实体须拆成多个独立菱形（如管理活动、管理路线）
            4) cardinalityA/cardinalityB 取值 1 或 n；m:n 在总体图用菱形表达，属性图单独展开
            5) 业务动词准确：路线用收藏/打卡，活动用报名，论坛用发帖/互动
            6) 仅输出 JSON，不要建表语句
            """;
    }

    private String resolveModelName(String requestModel) {
        if (StringUtils.isNotBlank(requestModel)) {
            return requestModel;
        }
        String defaultModel = erDiagramProperties.getDefaultModel();
        if (StringUtils.isBlank(defaultModel)) {
            throw new ServiceException("未指定模型且未配置 chat.er-diagram.default-model");
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
            log.error("解析 ER JSON 失败: {}", raw, e);
            throw new ServiceException("AI 返回格式错误，未能解析 ER 图数据");
        }
    }

    private List<ChenErLayoutBuilder.EntityDef> parseEntities(JsonNode entitiesNode) {
        if (!entitiesNode.isArray() || entitiesNode.isEmpty()) {
            throw new ServiceException("AI 未生成任何实体");
        }
        List<ChenErLayoutBuilder.EntityDef> entities = new ArrayList<>();
        for (JsonNode node : entitiesNode) {
            String name = node.path("name").asText("");
            if (StringUtils.isBlank(name)) {
                continue;
            }
            List<String> attributes = new ArrayList<>();
            JsonNode attrsNode = node.path("attributes");
            if (attrsNode.isArray()) {
                for (JsonNode attr : attrsNode) {
                    String attrName = attr.asText("");
                    if (StringUtils.isNotBlank(attrName)) {
                        attributes.add(attrName);
                    }
                }
            }
            attributes = ensureAttributes(name, attributes);
            entities.add(new ChenErLayoutBuilder.EntityDef(name, attributes));
        }
        if (entities.isEmpty()) {
            throw new ServiceException("AI 未生成有效实体");
        }
        return entities;
    }

    private List<ChenErLayoutBuilder.RelationshipDef> parseRelationships(JsonNode relationsNode) {
        List<ChenErLayoutBuilder.RelationshipDef> relationships = new ArrayList<>();
        if (!relationsNode.isArray()) {
            return relationships;
        }
        for (JsonNode node : relationsNode) {
            String name = node.path("name").asText("");
            String entityA = node.path("entityA").asText("");
            String entityB = node.path("entityB").asText("");
            if (StringUtils.isBlank(name) || StringUtils.isBlank(entityA) || StringUtils.isBlank(entityB)) {
                continue;
            }
            relationships.add(new ChenErLayoutBuilder.RelationshipDef(
                name,
                entityA,
                entityB,
                node.path("cardinalityA").asText("1"),
                node.path("cardinalityB").asText("n")
            ));
        }
        return relationships;
    }

    /** AI 未返回属性时补全默认属性，确保能生成实体属性图 */
    private List<String> ensureAttributes(String entityName, List<String> attributes) {
        if (attributes != null && !attributes.isEmpty()) {
            return attributes.size() > 10 ? attributes.subList(0, 10) : attributes;
        }
        String prefix = StringUtils.isNotBlank(entityName) ? entityName : "实体";
        return List.of(
            prefix + "编号",
            prefix + "名称",
            "描述",
            "状态",
            "创建时间",
            "更新时间"
        );
    }

    private ErDiagramResponse toResponse(ChenErLayoutBuilder.ErDiagramBundle bundle,
                                         List<ChenErLayoutBuilder.EntityDef> entityDefs) {
        Set<String> overviewEntityLabels = bundle.overview().nodes().stream()
            .filter(n -> "entity".equals(n.getType()))
            .map(ErNodeVo::getLabel)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ErEntityAttributeDiagramVo> attributeDiagrams = new ArrayList<>();
        List<ErEntityMetaVo> entityMetas = new ArrayList<>();
        for (ChenErLayoutBuilder.EntityDef def : entityDefs) {
            if (!overviewEntityLabels.contains(def.name())) {
                continue;
            }
            ErEntityMetaVo meta = new ErEntityMetaVo();
            meta.setName(def.name());
            meta.setAttributes(def.attributes());
            entityMetas.add(meta);
        }
        List<ChenErLayoutBuilder.ChenDiagram> attrList = bundle.attributeDiagrams();
        List<String> names = bundle.entityNames();
        for (int i = 0; i < attrList.size(); i++) {
            ChenErLayoutBuilder.ChenDiagram attr = attrList.get(i);
            ErEntityAttributeDiagramVo vo = new ErEntityAttributeDiagramVo();
            vo.setEntityName(i < names.size() ? names.get(i) : "实体");
            vo.setEntityId(attr.nodes().stream()
                .filter(n -> "entity".equals(n.getType()))
                .map(n -> n.getId())
                .findFirst()
                .orElse(""));
            vo.setNodes(attr.nodes());
            vo.setEdges(attr.edges());
            attributeDiagrams.add(vo);
        }
        return ErDiagramResponse.builder()
            .nodes(bundle.overview().nodes())
            .edges(bundle.overview().edges())
            .attributeDiagrams(attributeDiagrams)
            .entities(entityMetas)
            .build();
    }
}

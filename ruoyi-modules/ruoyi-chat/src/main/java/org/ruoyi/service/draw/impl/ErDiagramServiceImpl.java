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
import org.ruoyi.domain.dto.response.ErSqlOptimizeResponse;
import org.ruoyi.domain.dto.response.ErSqlTestResponse;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.ruoyi.service.draw.IErDiagramService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
        log.info("本地解析 SQL 生成陈氏 ER 图");
        SqlChenErParser.ParseResult parsed = SqlChenErParser.parse(sql);
        ChenErLayoutBuilder.ChenDiagram diagram = ChenErLayoutBuilder.build(
            parsed.entities(),
            parsed.relationships()
        );
        return ErDiagramResponse.builder()
            .nodes(diagram.nodes())
            .edges(diagram.edges())
            .build();
    }

    private ErDiagramResponse generateFromAi(ErDiagramGenerateRequest request) {
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
        ChenErLayoutBuilder.ChenDiagram diagram = ChenErLayoutBuilder.build(entities, relationships);

        return ErDiagramResponse.builder()
            .nodes(diagram.nodes())
            .edges(diagram.edges())
            .build();
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

            样式参照教材总体 E-R 图（一张图同时包含全部元素）：
            - 矩形 = 实体
            - 椭圆 = 属性（每个实体 4~6 个，第一个属性为主键/编号类）
            - 菱形 = 关系（动词：属于、选修、开设、管理、包含等）
            - 实体与关系连线上用 cardinalityA/cardinalityB 标注 1 或 n（小方框显示）

            要求：
            1) entities：实体中文名 + attributes 属性列表（4~6 个关键属性，不要 SQL 类型）
            2) relationships：关系动词，entityA/entityB 引用已有实体
            3) cardinalityA/cardinalityB 取值 1 或 n
            4) 多对多通过菱形关系表达；实体间可有多条不同关系
            5) 仅输出 JSON，不要建表语句
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
}

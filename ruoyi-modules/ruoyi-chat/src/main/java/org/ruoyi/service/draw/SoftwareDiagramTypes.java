package org.ruoyi.service.draw;

import org.ruoyi.common.core.exception.ServiceException;

import java.util.Map;
import java.util.Set;

/**
 * 软件工程图类型注册表（每类独立 prompt_code：sw_diagram_{type}）
 */
public final class SoftwareDiagramTypes {

    private SoftwareDiagramTypes() {
    }

    public static final Set<String> ALLOWED = Set.of(
        "class", "sequence", "usecase", "activity", "state", "object",
        "component", "dfd", "func_flow", "func_structure", "architecture", "deployment", "swimlane"
    );

    private static final String GRAPH_BASE = """
        根据用户描述输出可编辑画布 JSON（nodes + edges），前端用 X6 画布渲染，支持拖拽编辑。
        节点 shape：rect=矩形，ellipse=椭圆，diamond=菱形，circle=圆形。
        要求：id 唯一；label 中文；nodes 含 x/y/width/height；edges 的 from/to 引用节点 id。
        仅输出 JSON，不要 markdown 代码块与解释。""";

    private static final Map<String, String> DEFAULT_PROMPTS = Map.ofEntries(
        Map.entry("class", """
            你是 UML 类图建模专家。根据用户描述输出类图 JSON（classes + relations），前端绘制标准 UML 类图（三格类框、继承空心三角、聚合/组合菱形、依赖虚线）。
            classes：id 英文，name 类名，attributes 属性列表，methods 方法列表；属性/方法前缀 +/-/# 表示可见性。
            relations.type：inheritance/association/aggregation/composition/dependency；from/to 引用 class id；inheritance 的 from 为子类 to 为父类。
            仅输出 JSON，不要代码块。"""),
        Map.entry("sequence", """
            你是 UML 时序图建模专家。根据用户描述输出时序图 JSON（participants + messages），前端绘制标准 UML 时序图（生命线、实线/虚线箭头）。
            participants.kind：actor=用户人形，object=系统对象，database=数据库。
            messages.type：sync=实线调用，return=虚线返回，async=虚线异步。messages 按时间顺序排列。
            仅输出 JSON，不要代码块。"""),
        Map.entry("usecase", """
            你是 UML 用例图建模专家。根据用户描述输出用例图 JSON（actors + useCases）。
            参与者 actorId 关联顶级用例，子用例用 parentId。仅输出 JSON，不要代码块。"""),
        Map.entry("activity", """
            你是 UML 活动图建模专家。根据用户描述输出活动图 JSON（nodes + flows），前端绘制标准 UML 活动图（实心圆开始、双圆结束、圆角活动、菱形判断、带箭头流转）。
            nodes.kind：start/end/activity/decision；activity/decision 需 label；必须有 start 与 end。
            flows：from/to 引用 node id；decision 分支 label 用 [是]/[否] 等。
            仅输出 JSON，不要代码块。"""),
        Map.entry("state", "你是 UML 状态图建模专家。" + GRAPH_BASE + " 状态 rect/circle，转移边 label 标注条件。"),
        Map.entry("object", "你是 UML 对象图建模专家。" + GRAPH_BASE + " 对象 rect，label 含 对象名:类名。"),
        Map.entry("component", "你是 UML 构件图建模专家。" + GRAPH_BASE + " 构件 rect，依赖用 edges。"),
        Map.entry("dfd", "你是数据流图建模专家。" + GRAPH_BASE + " 外部实体/处理 rect，存储 ellipse，数据流 label 标注数据名。"),
        Map.entry("func_flow", "你是功能流程图建模专家。" + GRAPH_BASE + " 步骤 rect，判断 diamond，自上而下。"),
        Map.entry("func_structure", "你是功能结构图建模专家。" + GRAPH_BASE + " 系统/模块/功能 rect，树形 edges。"),
        Map.entry("architecture", "你是软件体系结构图建模专家。" + GRAPH_BASE + " 分层组件 rect，依赖 edges。"),
        Map.entry("deployment", "你是 UML 部署图建模专家。" + GRAPH_BASE + " 节点与构件 rect，部署/通信 edges。"),
        Map.entry("swimlane", "你是泳道图建模专家。" + GRAPH_BASE + " 泳道内步骤 rect，跨泳道 edges。")
    );

    public static void validate(String diagramType) {
        if (!ALLOWED.contains(diagramType)) {
            throw new ServiceException("不支持的图表类型: " + diagramType);
        }
    }

    public static String promptCode(String diagramType) {
        validate(diagramType);
        return "sw_diagram_" + diagramType;
    }

    public static String defaultPrompt(String diagramType) {
        validate(diagramType);
        return DEFAULT_PROMPTS.get(diagramType);
    }
}

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
        根据用户描述输出可编辑画布 JSON（nodes + edges），前端用 Draw.io（mxGraph）画布渲染，支持拖拽编辑。
        节点 shape：rect=矩形，ellipse=椭圆，diamond=菱形，circle=圆形，database=圆柱体数据存储。
        要求：id 唯一；label 中文；nodes 含 x/y/width/height；edges 的 from/to 引用节点 id。
        仅输出 JSON，不要 markdown 代码块与解释。""";

    private static final Map<String, String> DEFAULT_PROMPTS = Map.ofEntries(
        Map.entry("class", """
            你是毕业论文 UML 类图建模专家。根据用户描述输出局部核心类图 JSON（classes + relations）。

            类节点：三格类框（类名/属性/操作）；类名与成员必须英文驼峰，禁止中文；接口 name 写 «interface» IXxx。
            可见性仅用 +/-/#/~ 前缀（禁止 public/private 单词）；属性：可见性 名: 类型；方法：可见性 名(参数: 类型): 返回类型。
            仅 4~8 个核心类；禁止 getter/setter/toString/equals/hashCode/log/Logger。

            relations.type：inheritance（泛化，from 子类 to 父类）/ implementation（实现，from 实现类 to 接口）
            / association / aggregation（from 整体 to 部分）/ composition / dependency。
            关联/聚合/组合用 label 标多重性（1、*、1..*）。仅输出 JSON，不要代码块。"""),
        Map.entry("sequence", """
            你是 UML 时序图建模专家。根据用户描述输出时序图 JSON（participants + messages），前端绘制标准 UML 时序图（生命线、实线/虚线箭头）。
            participants.kind：actor=用户人形，object=系统对象，database=数据库。
            messages.type：sync=实线调用，return=虚线返回，async=虚线异步。messages 按时间顺序排列。
            仅输出 JSON，不要代码块。"""),
        Map.entry("usecase", """
            你是 UML 用例图建模专家。根据用户描述输出用例图 JSON（actors + useCases），前端绘制标准 UML 用例图（左侧人形参与者、椭圆用例、直线连接）。
            actors：参与者列表，id 英文，label 中文。
            useCases：用例列表，id 英文，label 中文；每个用例必须设 actorId 指向参与者 id。
            所有用例均为参与者下的一级用例，禁止输出 parentId，禁止生成二级/子用例。
            仅输出 JSON，不要代码块。"""),
        Map.entry("activity", """
            你是 UML 活动图建模专家。用户通常只输入「是什么系统/什么业务」，你需要抽取该系统的【单一用户视角】主流程，输出规范活动图 JSON（仅 nodes + flows，禁止 swimlanes/lane）。

            图形风格（论文常见、自上而下单列）：
            - start：实心圆；end：牛眼双圆终止符（kind=end，全图只能 1 个 end）
            - activity：圆角矩形；decision：菱形判断（label 写判断语，如「验证成功?」「功能类型?」）
            - merge：分支合流必须用菱形（kind=decision，label 写「合流」或留空），禁止多条线直接汇入某个 activity
            - 分支条件写在 flows.label：[是]/[否]/[查看]/[操作]/[查询]/[办理] 等；禁止把条件做成活动节点
            - 分支布局（论文规范）：[是] 沿图中轴向下；[否] 向右再向下；[查看]/[查询] 向左；[操作]/[办理] 向右；禁止把成功路径画到左侧
            - 禁止 fork/join、禁止泳道、禁止多角色并行分流（禁止「角色类型?」「[教师]/[管理员]/[普通用户]」三分支）

            语义硬约束（论文评审重点）：
            - 身份/认证/验证类 decision 的 [否] 只能连「提示错误/验证失败」类 activity，禁止连进入首页、提交办理、更新记录等主干
            - 办理/更新后若可能失败，必须先接「是否成功?」decision，再分 [是] 合流 与 [否] 提示错误；禁止更新记录直连提示错误
            - 成功两支先 merge，再与 [否] 错误支 merge → end；禁止控制流交叉穿透

            默认主干模板（按系统替换活动名称，但结构保持一致）：
            开始 → 用户登录/进入系统 → 验证成功?（decision）
              [否] → 显示错误信息
              [是] → 显示主页 → 用户选择功能 → 功能类型?（decision）
                [查看] → 显示信息
                [操作] → 执行操作 → 更新数据 → 更新成功?（decision）
                  [是] → 合流
                  [否] → 显示错误信息
              成功分支的两条末端先 merge 合流；再与 [否] 分支 merge 合流 → end

            nodes：id 英文；kind 取值 start/end/activity/decision；activity 与 decision 需 label 中文；必须含 1 个 start 与 1 个 end。
            flows：id 英文，from/to 引用 node id。
            仅输出 JSON，不要 markdown 代码块与解释。"""),
        Map.entry("state", "你是 UML 状态图建模专家。" + GRAPH_BASE + " 状态 rect/circle，转移边 label 标注条件。"),
        Map.entry("object", "你是 UML 对象图建模专家。" + GRAPH_BASE + " 对象 rect，label 含 对象名:类名。"),
        Map.entry("component", "你是 UML 构件图建模专家。" + GRAPH_BASE + " 构件 rect，依赖用 edges。"),
        Map.entry("dfd", """
            你是数据流图（DFD）建模专家。""" + GRAPH_BASE + """

            DFD 专业符号与约束（必须严格遵守）：
            - 外部实体 External Entity：shape=rect，label 形如“外部实体1/外部实体2/教师/学生/教务处”等
            - 处理过程 Process：shape=circle，label 必须带编号，形如“1.0 处理1”“2.0 处理2”“3.0 处理3”
            - 数据存储 Data Store：shape=database，label 必须形如“D1: 数据存储1”“D2: 数据存储2”
            - 数据流 Data Flow：edges 必须有 label，label 为数据名（如“输入数据1/中间数据2/读取数据1/存储数据2”）

            输出规则：
            - 仅输出 JSON（nodes + edges），不要 markdown 代码块与解释
            - nodes/edges 的 id 必须唯一；edges.from/to 必须引用存在的 node.id
            - 尽量按从上到下/从左到右布局，避免交叉线；为 nodes 填充合理的 x/y/width/height
            """),
        Map.entry("func_flow", """
            你是标准流程图（功能流程图）建模专家。""" + GRAPH_BASE + """

            标准流程图符号（必须严格遵守，参考教材/论文常用规范）：
            - 开始/结束 Terminator：shape=ellipse，label 必须为“开始”“结束”（各 1 个）
            - 处理过程 Process：shape=rect，label 用动宾短语（如“录入课程信息”“提交申请”“保存用户信息”）
            - 判断 Decision：shape=diamond，label 必须是判断语句（如“是否验证通过”“参数是否合法”）
            - 流向箭头 Flow：edges 表示控制流，尽量使用自上而下主干，分支左右展开

            分支与回路（强制）：
            - 每个 diamond 必须至少 2 条出边，并且每条出边都必须有 label：优先用“是/否”，或“通过/不通过”
            - 允许回路（如“重新提交/返回修改”），但要避免交叉线；回路边 label 必须明确（如“重新提交”“返回修改”）
            - 禁止生成多个“结束”节点：全图只能有 1 个 label=“结束”的 ellipse；所有分支最终必须汇合到同一个“结束”
            - 若存在“撤销/取消/终止”等提前结束分支，也必须连到同一个“结束”节点，不得单独再画一个结束

            布局与尺寸（强制）：
            - 方向：从上到下为主，必要时左右分支；减少斜线与交叉线
            - 建议尺寸：开始/结束 120×50；处理 160×56；判断 140×90（可按文字略增）
            - nodes 必须填写合理 x/y/width/height

            输出只允许 JSON（nodes + edges），不要 markdown 代码块与解释。
            """),
        Map.entry("func_structure", "你是功能结构图建模专家。" + GRAPH_BASE + " 系统/模块/功能 rect，树形 edges。"),
        Map.entry("architecture", """
            你是软件体系结构图（分层架构图）建模专家。根据用户描述输出论文级体系结构图 JSON，前端用 Draw.io（mxGraph）画布渲染。

            输出结构（强制）：
            - title：图标题
            - layers：仅使用 layers+items+connections，禁止根级 nodes/edges
            - 自上而下四层：表现层、接入与网关层、业务服务层、数据服务层
            - items 必须是真实组件（微服务/网关/前端/数据库），禁止把层名写入 items
            - connections：from/to 引用 item.id，禁止 label

            论文级规范（强制）：
            1) 表现层：仅 Vue/Web/H5/小程序/移动客户端，禁止放 API 网关
            2) 接入与网关层：仅 1 个组件，label 固定为「API 网关」，禁止括号技术栈、禁止认证/Nginx/负载均衡
            3) 业务服务层：仅业务微服务，禁止 Elasticsearch/OSS/对象存储/MySQL/Redis
            4) 数据服务层：以 MySQL、Redis 为主；论文默认不写 Elasticsearch/OSS，除非用户明确要求搜索或对象存储
            5) items.label 不得等于各层 layer.label；connections 覆盖 前端→API网关→微服务→MySQL/Redis；仅输出 JSON
            """),
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

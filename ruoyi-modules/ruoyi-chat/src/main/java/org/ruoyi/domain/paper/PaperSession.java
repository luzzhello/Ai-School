package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 论文生成智能体——会话领域模型（与数据库 paper_session / paper_reference / paper_chapter 对应）。
 */
@Data
public class PaperSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话状态常量 */
    public static final class Status {
        /** 初始：已创建，等待输入/解析 */
        public static final String INIT = "init";
        /** 参考文献已确认锁定 */
        public static final String REF_CONFIRMED = "ref_confirmed";
        /** 大纲已确认 */
        public static final String TOC_CONFIRMED = "toc_confirmed";
        /** 逐章写作中 */
        public static final String WRITING = "writing";
        /** 完成 */
        public static final String DONE = "done";

        private Status() {
        }
    }

    /** 会话唯一标识 */
    private String sessionId;

    /** 论文题目 */
    private String title;

    /** 用户输入项 */
    private UserInputs userInputs = new UserInputs();

    /** SQL 解析结果 */
    private SqlParsed sqlParsed = new SqlParsed();

    /** 已确认的参考文献列表 */
    private List<Reference> references = new ArrayList<>();

    /** 目录大纲树 */
    private List<TocNode> toc = new ArrayList<>();

    /** 系统实现功能界面截图清单 */
    private List<PaperUiScreenshot> uiScreenshots = new ArrayList<>();

    /** 已生成正文内容，key=章节 id，value=正文内容 */
    private Map<String, String> generatedContent = new LinkedHashMap<>();

    /** 流程状态，见 {@link Status} */
    private String status = Status.INIT;

    /** 绑定的格式模板 id */
    private Long formatTemplateId;

    /** 会话级格式覆盖 JSON（与模板 merge 后生效） */
    private String formatOverrideJson;

    /** 会话自定义排版 docx 相对路径；非空 = 自定义模式 */
    private String customFormatDocxPath;

    private String customFormatDocxName;

    private Long customFormatDocxSize;

    /** 自定义版式主配置 JSON */
    private String customFormatJson;

    /** 1=强制 patch 样式；0=不 patch；自定义模式默认 1 */
    private Integer customPatchStyles;

    /** 创建时间戳（毫秒），用于会话过期清理 */
    private long createTime = System.currentTimeMillis();

    /** 最后更新时间戳（毫秒） */
    private long updateTime = System.currentTimeMillis();

    /**
     * 用户输入项。
     */
    @Data
    public static class UserInputs implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 上传的 SQL 文件内容（必填） */
        private String sqlContent;

        /** 上传的 Controller/Service 代码内容（可选） */
        private String codeContent;

        /** 开发环境信息（编程语言、框架、数据库等） */
        private String envInfo;

        /** 字数要求，默认 15000 */
        private Integer wordCount = 15000;

        /** 学历层次：本科 / 专科 */
        private String educationLevel;
    }

    /**
     * SQL 解析结果摘要。
     */
    @Data
    public static class SqlParsed implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 所有表名 */
        private List<String> tables = new ArrayList<>();

        /** 表字段映射，key=表名，value=字段列表 */
        private Map<String, List<SqlColumnInfo>> columns = new LinkedHashMap<>();

        /** 表注释（SQL COMMENT），key=物理表名 */
        private Map<String, String> tableComments = new LinkedHashMap<>();

        /** 表之间的关联关系（通过外键推断） */
        private List<Relation> relations = new ArrayList<>();

        /** 系统功能推断文字描述（供 Prompt 注入） */
        private String summary;
    }
}

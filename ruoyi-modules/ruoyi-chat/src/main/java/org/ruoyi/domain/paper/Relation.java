package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;

/**
 * 表之间的关联关系，由外键（显式 FOREIGN KEY 或 xxx_id 命名约定）推断而来。
 * 对应 PRD「6.4 SQL 文件解析逻辑」中的 relations：{table1, table2, via_column, type(1:N / N:N)}。
 */
@Data
public class Relation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关系「一」端的表（被引用表）；N:N 时为其中一张被关联表 */
    private String table1;

    /** 关系「多」端的表（引用表）；N:N 时为另一张被关联表 */
    private String table2;

    /** 关联字段（外键列名）；N:N 时为中间表名 */
    private String viaColumn;

    /** 关系类型：1:N / N:N */
    private String type;

    public Relation() {
    }

    public Relation(String table1, String table2, String viaColumn, String type) {
        this.table1 = table1;
        this.table2 = table2;
        this.viaColumn = viaColumn;
        this.type = type;
    }
}

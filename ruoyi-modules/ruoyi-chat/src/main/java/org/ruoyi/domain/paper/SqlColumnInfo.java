package org.ruoyi.domain.paper;

import lombok.Data;

import java.io.Serializable;

/**
 * SQL 表字段信息，由 SQL 文件解析得到。
 * 对应 PRD「6.4 SQL 文件解析逻辑」中的 columns：{name, type, comment, is_pk, is_fk}。
 */
@Data
public class SqlColumnInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段名 */
    private String name;

    /** 字段类型 */
    private String type;

    /** 字段注释/说明 */
    private String comment;

    /** 是否主键 */
    private boolean pk;

    /** 是否外键 */
    private boolean fk;
}

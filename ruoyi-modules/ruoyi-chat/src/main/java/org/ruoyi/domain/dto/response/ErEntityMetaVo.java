package org.ruoyi.domain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * ER 实体元数据（含属性列表，供属性图生成）
 */
@Data
public class ErEntityMetaVo {

    private String name;

    private List<String> attributes;
}

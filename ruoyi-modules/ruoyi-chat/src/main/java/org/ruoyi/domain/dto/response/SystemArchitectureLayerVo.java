package org.ruoyi.domain.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class SystemArchitectureLayerVo {

    private String id;

    private String label;

    /** 可选，十六进制背景色如 #1e88e5 */
    private String color;

    private List<SystemArchitectureItemVo> items;
}

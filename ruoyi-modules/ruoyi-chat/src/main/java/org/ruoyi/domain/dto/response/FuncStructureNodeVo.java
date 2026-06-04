package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * 功能结构图节点
 */
@Data
public class FuncStructureNodeVo {

    private String id;

    private String label;

    /**
     * 0=系统 1=模块 2=功能
     */
    private Integer level;

    private String parentId;
}

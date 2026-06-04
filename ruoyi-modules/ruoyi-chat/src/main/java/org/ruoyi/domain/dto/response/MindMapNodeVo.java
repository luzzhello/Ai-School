package org.ruoyi.domain.dto.response;

import lombok.Data;

/**
 * 思维导图节点
 */
@Data
public class MindMapNodeVo {

    private String id;

    private String label;

    private Integer level;

    private String parentId;
}

package org.ruoyi.domain.dto.request;

import lombok.Data;

/**
 * 表格区域边框（0 表示无边框）
 */
@Data
public class WordTableBorderConfig {

    private Integer top = 0;
    private Integer bottom = 0;
    private Integer left = 0;
    private Integer right = 0;
}

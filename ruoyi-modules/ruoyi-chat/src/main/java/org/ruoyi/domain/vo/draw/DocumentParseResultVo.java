package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DocumentParseResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fileName;

    private String content;

    private Integer wordCount;
}

package org.ruoyi.domain.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PaperRewriteSegmentResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mode;
    private String originalText;
    private String rewrittenText;
    private Integer costCoins;
}

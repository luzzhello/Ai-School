package org.ruoyi.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.ruoyi.domain.paper.format.PaperFormatConfig;

/**
 * 更新会话排版配置（模板绑定 / 覆盖项）。
 */
@Data
public class PaperSessionFormatUpdateRequest {

    /** 绑定的格式模板 id；JSON 出现该字段时即视为提供（可为 null 以清除绑定） */
    @Setter(AccessLevel.NONE)
    private Long templateId;

    /** 会话级稀疏覆盖配置 */
    private PaperFormatConfig override;

    /** 为 true 时清空 formatOverrideJson；切换模板且未传时默认 true */
    private Boolean clearOverride;

    @JsonIgnore
    @Getter
    @Setter(AccessLevel.NONE)
    private boolean templateIdSpecified;

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
        this.templateIdSpecified = true;
    }
}

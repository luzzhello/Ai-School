package org.ruoyi.domain.bo.chat;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;
import org.ruoyi.domain.entity.chat.ChatPrompt;

/**
 * AI提示词业务对象 chat_prompt
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ChatPrompt.class, reverseConvertGenerate = false)
public class ChatPromptBo extends BaseEntity {

    /**
     * 主键
     */
    @NotNull(message = "主键不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 提示词名称
     */
    @NotBlank(message = "提示词名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String promptName;

    /**
     * 提示词编码
     */
    @NotBlank(message = "提示词编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String promptCode;

    /**
     * 提示词内容
     */
    @NotBlank(message = "提示词内容不能为空", groups = { AddGroup.class, EditGroup.class })
    private String promptContent;

    /**
     * 分类/场景
     */
    private String category;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 排序
     */
    private Long sortOrder;

    /**
     * 备注
     */
    private String remark;

}

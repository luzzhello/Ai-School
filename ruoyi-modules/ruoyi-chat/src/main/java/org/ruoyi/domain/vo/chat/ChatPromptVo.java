package org.ruoyi.domain.vo.chat;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.ruoyi.common.excel.annotation.ExcelDictFormat;
import org.ruoyi.common.excel.convert.ExcelDictConvert;
import org.ruoyi.domain.entity.chat.ChatPrompt;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI提示词视图对象 chat_prompt
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ChatPrompt.class)
public class ChatPromptVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @ExcelProperty(value = "主键")
    private Long id;

    /**
     * 提示词名称
     */
    @ExcelProperty(value = "提示词名称")
    private String promptName;

    /**
     * 提示词编码
     */
    @ExcelProperty(value = "提示词编码")
    private String promptCode;

    /**
     * 提示词内容
     */
    @ExcelProperty(value = "提示词内容")
    private String promptContent;

    /**
     * 分类/场景
     */
    @ExcelProperty(value = "分类/场景")
    private String category;

    /**
     * 状态（0正常 1停用）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long sortOrder;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

}

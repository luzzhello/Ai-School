package org.ruoyi.controller.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.excel.utils.ExcelUtil;
import org.ruoyi.common.idempotent.annotation.RepeatSubmit;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.domain.bo.chat.ChatPromptBo;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.service.chat.IChatPromptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI提示词管理
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/prompt")
public class ChatPromptController extends BaseController {

    private final IChatPromptService chatPromptService;

    /**
     * 查询AI提示词列表
     */
    @SaCheckPermission("system:prompt:list")
    @GetMapping("/list")
    public TableDataInfo<ChatPromptVo> list(ChatPromptBo bo, PageQuery pageQuery) {
        return chatPromptService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出AI提示词列表
     */
    @SaCheckPermission("system:prompt:export")
    @Log(title = "AI提示词", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ChatPromptBo bo, HttpServletResponse response) {
        List<ChatPromptVo> list = chatPromptService.queryList(bo);
        ExcelUtil.exportExcel(list, "AI提示词", ChatPromptVo.class, response);
    }

    /**
     * 获取AI提示词详细信息
     */
    @SaCheckPermission("system:prompt:query")
    @GetMapping("/{id}")
    public R<ChatPromptVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(chatPromptService.queryById(id));
    }

    /**
     * 新增AI提示词
     */
    @SaCheckPermission("system:prompt:add")
    @Log(title = "AI提示词", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ChatPromptBo bo) {
        return toAjax(chatPromptService.insertByBo(bo));
    }

    /**
     * 修改AI提示词
     */
    @SaCheckPermission("system:prompt:edit")
    @Log(title = "AI提示词", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ChatPromptBo bo) {
        return toAjax(chatPromptService.updateByBo(bo));
    }

    /**
     * 删除AI提示词
     */
    @SaCheckPermission("system:prompt:remove")
    @Log(title = "AI提示词", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        return toAjax(chatPromptService.deleteWithValidByIds(List.of(ids), true));
    }
}

package org.ruoyi.controller.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.PaperFormatTemplateSaveRequest;
import org.ruoyi.domain.dto.request.PaperFormatTemplateStatusRequest;
import org.ruoyi.domain.dto.response.PaperFormatTemplateOptionVo;
import org.ruoyi.domain.entity.paper.PaperFormatTemplateEntity;
import org.ruoyi.service.paper.PaperFormatTemplateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 论文排版模板管理（多模板元数据 / format_json / docx）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper/format-template")
public class PaperFormatTemplateController {

    private final PaperFormatTemplateService paperFormatTemplateService;

    @SaCheckPermission("system:paperFormatTemplate:list")
    @GetMapping("/list")
    public R<List<PaperFormatTemplateEntity>> list() {
        LoginHelper.getUserId();
        return R.ok(paperFormatTemplateService.listAll());
    }

    /**
     * 启用中模板简表（任意登录用户可选）；须放在 {@code /{id}} 之前以免路径冲突。
     */
    @GetMapping("/options")
    public R<List<PaperFormatTemplateOptionVo>> options() {
        LoginHelper.getUserId();
        List<PaperFormatTemplateOptionVo> options = paperFormatTemplateService.listEnabled().stream()
            .map(entity -> {
                PaperFormatTemplateOptionVo vo = new PaperFormatTemplateOptionVo();
                vo.setId(entity.getId());
                vo.setName(entity.getName());
                vo.setIsDefault(entity.getIsDefault());
                vo.setSchoolName(entity.getSchoolName());
                return vo;
            })
            .toList();
        return R.ok(options);
    }

    @SaCheckPermission("system:paperFormatTemplate:query")
    @GetMapping("/{id}")
    public R<PaperFormatTemplateEntity> get(@PathVariable Long id) {
        LoginHelper.getUserId();
        PaperFormatTemplateEntity entity = paperFormatTemplateService.getById(id);
        if (entity == null) {
            throw new ServiceException("排版模板不存在: " + id);
        }
        return R.ok(entity);
    }

    @SaCheckPermission("system:paperFormatTemplate:add")
    @PostMapping
    public R<Long> create(@RequestBody PaperFormatTemplateSaveRequest request) {
        LoginHelper.getUserId();
        if (request == null) {
            throw new ServiceException("模板信息不能为空");
        }
        String username = LoginHelper.getUsername();
        PaperFormatTemplateEntity meta = new PaperFormatTemplateEntity();
        meta.setName(request.getName());
        meta.setSchoolName(request.getSchoolName());
        meta.setRemark(request.getRemark());
        meta.setIsDefault(request.getIsDefault());
        meta.setStatus(request.getStatus());
        meta.setCreateBy(username);
        meta.setUpdateBy(username);
        Long id = paperFormatTemplateService.create(meta, request.getFormat());
        return R.ok(id);
    }

    @SaCheckPermission("system:paperFormatTemplate:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody PaperFormatTemplateSaveRequest request) {
        LoginHelper.getUserId();
        if (request == null) {
            throw new ServiceException("模板信息不能为空");
        }
        PaperFormatTemplateEntity meta = new PaperFormatTemplateEntity();
        meta.setName(request.getName());
        meta.setSchoolName(request.getSchoolName());
        meta.setRemark(request.getRemark());
        meta.setStatus(request.getStatus());
        meta.setUpdateBy(LoginHelper.getUsername());
        paperFormatTemplateService.updateMetaAndFormat(id, meta, request.getFormat());
        return R.ok();
    }

    @SaCheckPermission("system:paperFormatTemplate:upload")
    @PostMapping(value = "/{id}/docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> uploadDocx(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        LoginHelper.getUserId();
        paperFormatTemplateService.uploadDocx(id, file);
        return R.ok();
    }

    @SaCheckPermission("system:paperFormatTemplate:edit")
    @PostMapping("/{id}/set-default")
    public R<Void> setDefault(@PathVariable Long id) {
        LoginHelper.getUserId();
        paperFormatTemplateService.setDefault(id);
        return R.ok();
    }

    @SaCheckPermission("system:paperFormatTemplate:edit")
    @PutMapping("/{id}/status")
    public R<Void> setStatus(@PathVariable Long id, @Valid @RequestBody PaperFormatTemplateStatusRequest request) {
        LoginHelper.getUserId();
        paperFormatTemplateService.setStatus(id, request.getStatus());
        return R.ok();
    }

    @SaCheckPermission("system:paperFormatTemplate:query")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        LoginHelper.getUserId();
        PaperFormatTemplateEntity entity = paperFormatTemplateService.getById(id);
        if (entity == null) {
            throw new ServiceException("排版模板不存在: " + id);
        }
        byte[] data;
        try (InputStream in = paperFormatTemplateService.openDocx(id)) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new ServiceException("读取排版模板失败: " + e.getMessage());
        }
        String filename = StringUtils.isNotBlank(entity.getDocxOriginalName())
            ? entity.getDocxOriginalName()
            : "thesis-template.docx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .header(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .body(data);
    }
}

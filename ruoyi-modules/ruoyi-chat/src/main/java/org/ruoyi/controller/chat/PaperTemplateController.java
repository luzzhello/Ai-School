package org.ruoyi.controller.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.paper.PaperTemplateInfo;
import org.ruoyi.service.paper.PaperTemplateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 论文 Word 模板管理（上传 docx 后自动解压 styles/numbering 等到 unpacked 目录）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper/template")
public class PaperTemplateController {

    private final PaperTemplateService paperTemplateService;

    @SaCheckPermission("system:paperTemplate:list")
    @GetMapping("/info")
    public R<PaperTemplateInfo> info() {
        LoginHelper.getUserId();
        return R.ok(paperTemplateService.getInfo());
    }

    @SaCheckPermission("system:paperTemplate:query")
    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        LoginHelper.getUserId();
        byte[] data = paperTemplateService.readTemplateBytes();
        String encoded = URLEncoder.encode("thesis-template.docx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .header(HttpHeaders.CONTENT_TYPE,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .body(data);
    }

    @SaCheckPermission("system:paperTemplate:upload")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<PaperTemplateInfo> upload(@RequestPart("file") MultipartFile file) {
        LoginHelper.getUserId();
        return R.ok(paperTemplateService.upload(file));
    }

    @SaCheckPermission("system:paperTemplate:reset")
    @PostMapping("/reset")
    public R<PaperTemplateInfo> reset() {
        LoginHelper.getUserId();
        return R.ok(paperTemplateService.resetToDefault());
    }
}

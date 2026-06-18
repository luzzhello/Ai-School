package org.ruoyi.controller.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.draw.DocumentParseResultVo;
import org.ruoyi.service.draw.IDocumentReducedExportService;
import org.ruoyi.service.draw.IDocumentTextExtractService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档文本解析（AIGC 检测 / 降率等）
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/document")
public class DocumentParseController {

    private final IDocumentTextExtractService documentTextExtractService;

    private final IDocumentReducedExportService documentReducedExportService;

    private final ObjectMapper objectMapper;

    @PostMapping("/parse")
    public R<DocumentParseResultVo> parse(@RequestParam("file") MultipartFile file) {
        return R.ok(documentTextExtractService.parse(file));
    }

    /**
     * 将改写结果写回原始 DOCX，仅替换正文文字并保留原排版
     */
    @PostMapping("/export-reduced")
    public void exportReduced(
        @RequestParam("file") MultipartFile file,
        @RequestParam("title") String title,
        @RequestParam("segments") String segmentsJson,
        @RequestParam(value = "splitMode", defaultValue = "paragraph") String splitMode,
        HttpServletResponse response) {
        try {
            List<String> segments = objectMapper.readValue(segmentsJson, new TypeReference<>() {});
            documentReducedExportService.export(file, title, segments, splitMode, response);
        }
        catch (ServiceException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ServiceException("导出参数无效");
        }
    }
}

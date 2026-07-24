package org.ruoyi.service.draw.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.vo.draw.DocumentParseResultVo;
import org.ruoyi.service.draw.IDocumentTextExtractService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTextExtractServiceImpl implements IDocumentTextExtractService {

    private static final Set<String> SUPPORTED = Set.of("txt", "doc", "docx", "pdf");
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Override
    public DocumentParseResultVo parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请上传文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ServiceException("文件过大，请上传 20MB 以内的文档");
        }
        String fileName = StringUtils.trim(file.getOriginalFilename());
        if (StringUtils.isBlank(fileName)) {
            throw new ServiceException("文件名无效");
        }
        String ext = extractExtension(fileName);
        if (!SUPPORTED.contains(ext)) {
            throw new ServiceException("不支持的文件格式，请上传 TXT、DOC、DOCX 或 PDF");
        }

        String content;
        try (InputStream inputStream = file.getInputStream()) {
            content = extractText(inputStream, ext);
        }
        catch (ServiceException e) {
            throw e;
        }
        catch (IOException e) {
            log.warn("读取上传文件失败: {}", e.getMessage());
            throw new ServiceException("文件读取失败");
        }

        content = normalizeText(content);
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("未能从文件中提取到有效文本，请检查文件内容或改用文本输入");
        }

        DocumentParseResultVo vo = new DocumentParseResultVo();
        vo.setFileName(fileName);
        vo.setContent(content);
        vo.setWordCount(countWords(content));
        return vo;
    }

    private String extractText(InputStream inputStream, String ext) throws IOException {
        return switch (ext) {
            case "txt" -> new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            case "docx" -> extractDocx(inputStream);
            case "doc" -> extractDoc(inputStream);
            case "pdf" -> extractPdf(inputStream);
            default -> throw new ServiceException("不支持的文件格式，请上传 TXT、DOC、DOCX 或 PDF");
        };
    }

    private String extractDocx(InputStream inputStream) {
        try {
            return DocumentReducedPatcher.extractEditableText(inputStream);
        }
        catch (IOException e) {
            log.warn("DOCX 正文提取失败: {}", e.getMessage());
            throw new ServiceException("文件解析失败，请确认文件未损坏或改用 TXT 文本输入");
        }
    }

    private String extractDoc(InputStream inputStream) {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
        catch (IOException e) {
            log.warn("DOC 正文提取失败: {}", e.getMessage());
            throw new ServiceException("文件解析失败，请确认文件未损坏，或另存为 DOCX/TXT 后重试");
        }
    }

    private String extractPdf(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
        catch (IOException e) {
            log.warn("PDF 正文提取失败: {}", e.getMessage());
            throw new ServiceException("文件解析失败，请确认 PDF 含可选中文字（非纯扫描件），或改用文本输入");
        }
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace('\u0000', ' ')
            .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private int countWords(String text) {
        return text.replaceAll("\\s+", "").length();
    }
}

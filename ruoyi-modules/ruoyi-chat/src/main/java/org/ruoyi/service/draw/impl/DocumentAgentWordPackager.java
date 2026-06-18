package org.ruoyi.service.draw.impl;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将 Agent 产物打包为 ZIP（Word 文档 + SQL）
 */
@Component
class DocumentAgentWordPackager {

  private static final String FONT_BODY = "宋体";
  private static final String FONT_HEADING = "黑体";

  record PackResult(String downloadFileName, String zipBase64) {
  }

  PackResult pack(String projectTitle, Map<String, String> artifacts) {
    String safeTitle = sanitizeFileName(projectTitle);
    String fileName = safeTitle + "-智能文档.zip";
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("00-项目摘要.docx", toDocx("项目摘要", artifacts.get("input_summary")));
    files.put("01-项目标题.docx", toDocx("项目标题", artifacts.get("project_title")));
    files.put("02-功能要点.docx", toDocx("功能要点", artifacts.get("functional_requirements")));
    files.put("03-摘要与关键词.docx", toDocx("摘要与关键词", joinSections(
      artifacts.get("abstract_write"),
      artifacts.get("keywords"),
      artifacts.get("references"))));
    putDocxIfPresent(files, "04-第一章-绪论.docx", "第一章 绪论", artifacts.get("chapter1"));
    putDocxIfPresent(files, "05-第二章-关键技术介绍.docx", "第二章 关键技术介绍", artifacts.get("chapter2"));
    putDocxIfPresent(files, "06-第三章-需求分析.docx", "第三章 需求分析", artifacts.get("chapter3"));
    putDocxIfPresent(files, "07-功能模块图.docx", "功能模块图", artifacts.get("func_module_diagram"));
    putDocxIfPresent(files, "08-系统架构图.docx", "系统架构图", artifacts.get("architecture_diagram"));
    putDocxIfPresent(files, "09-第四章-系统设计.docx", "第四章 系统设计", artifacts.get("chapter4"));
    putTextIfPresent(files, "sql/schema.sql", artifacts.get("sql_generate"));
    putDocxIfPresent(files, "10-ER图说明.docx", "ER图说明", artifacts.get("er_diagram"));
    putDocxIfPresent(files, "11-第五章-数据库设计.docx", "第五章 数据库设计", artifacts.get("chapter5"));
    putDocxIfPresent(files, "12-第六章-系统实现.docx", "第六章 系统实现", artifacts.get("chapter6"));
    putDocxIfPresent(files, "13-第七章-系统测试.docx", "第七章 系统测试", artifacts.get("chapter7"));
    putDocxIfPresent(files, "14-总结与展望.docx", "总结与展望", artifacts.get("conclusion"));
    putDocxIfPresent(files, "15-致谢.docx", "致谢", artifacts.get("acknowledgement"));
    files.put(safeTitle + "-论文全文.docx", buildFullThesisDocx(projectTitle, artifacts));
    if (files.size() <= 1) {
      throw new ServiceException("文档内容为空，无法打包");
    }
    byte[] zipBytes = toZip(files);
    return new PackResult(fileName, Base64.getEncoder().encodeToString(zipBytes));
  }

  private byte[] buildFullThesisDocx(String projectTitle, Map<String, String> artifacts) {
    StringBuilder sb = new StringBuilder();
    appendSection(sb, projectTitle);
    appendSection(sb, artifacts.get("abstract_write"));
    appendSection(sb, artifacts.get("keywords"));
    appendSection(sb, artifacts.get("references"));
    appendSection(sb, artifacts.get("chapter1"));
    appendSection(sb, artifacts.get("chapter2"));
    appendSection(sb, artifacts.get("chapter3"));
    appendSection(sb, artifacts.get("func_module_diagram"));
    appendSection(sb, artifacts.get("architecture_diagram"));
    appendSection(sb, artifacts.get("chapter4"));
    appendSection(sb, artifacts.get("sql_generate"));
    appendSection(sb, artifacts.get("er_diagram"));
    appendSection(sb, artifacts.get("chapter5"));
    appendSection(sb, artifacts.get("chapter6"));
    appendSection(sb, artifacts.get("chapter7"));
    appendSection(sb, artifacts.get("conclusion"));
    appendSection(sb, artifacts.get("acknowledgement"));
    return toDocx(projectTitle == null ? "论文全文" : projectTitle, sb.toString());
  }

  private void appendSection(StringBuilder sb, String content) {
    if (content == null || content.isBlank()) {
      return;
    }
    if (!sb.isEmpty()) {
      sb.append("\n\n");
    }
    sb.append(content.trim());
  }

  private void putDocxIfPresent(Map<String, byte[]> files, String path, String title, String content) {
    if (content != null && !content.isBlank()) {
      files.put(path, toDocx(title, content));
    }
  }

  private void putTextIfPresent(Map<String, byte[]> files, String path, String content) {
    if (content != null && !content.isBlank()) {
      files.put(path, content.trim().getBytes(StandardCharsets.UTF_8));
    }
  }

  private String joinSections(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      if (!sb.isEmpty()) {
        sb.append("\n\n");
      }
      sb.append(part.trim());
    }
    return sb.toString();
  }

  private byte[] toDocx(String title, String markdownText) {
    try (XWPFDocument doc = new XWPFDocument()) {
      if (title != null && !title.isBlank()) {
        addHeading(doc, title, 1);
        addEmptyLine(doc);
      }
      writeMarkdownContent(doc, markdownText == null ? "" : markdownText);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new ServiceException("生成 Word 文档失败");
    }
  }

  private void writeMarkdownContent(XWPFDocument doc, String text) {
    for (String line : text.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        addEmptyLine(doc);
        continue;
      }
      if (trimmed.startsWith("### ")) {
        addHeading(doc, trimmed.substring(4).trim(), 3);
      } else if (trimmed.startsWith("## ")) {
        addHeading(doc, trimmed.substring(3).trim(), 2);
      } else if (trimmed.startsWith("# ")) {
        addHeading(doc, trimmed.substring(2).trim(), 1);
      } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        addBody(doc, "  " + trimmed.substring(2).trim(), false);
      } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
        addBody(doc, "  " + trimmed, false);
      } else {
        addBody(doc, trimmed, true);
      }
    }
  }

  private void addHeading(XWPFDocument doc, String text, int level) {
    XWPFParagraph p = doc.createParagraph();
    p.setAlignment(level == 1 ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
    if (level == 1) {
      p.setSpacingAfter(200);
    }
    XWPFRun run = p.createRun();
    run.setText(text);
    run.setBold(true);
    run.setFontFamily(FONT_HEADING);
    run.setFontSize(level == 1 ? 16 : (level == 2 ? 14 : 12));
  }

  private void addBody(XWPFDocument doc, String text, boolean indent) {
    XWPFParagraph p = doc.createParagraph();
    p.setAlignment(ParagraphAlignment.BOTH);
    if (indent) {
      p.setIndentationFirstLine(480);
    }
    p.setSpacingAfter(80);
    XWPFRun run = p.createRun();
    run.setText(text);
    run.setFontFamily(FONT_BODY);
    run.setFontSize(12);
  }

  private void addEmptyLine(XWPFDocument doc) {
    doc.createParagraph();
  }

  private byte[] toZip(Map<String, byte[]> files) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ZipOutputStream zos = new ZipOutputStream(bos)) {
      for (Map.Entry<String, byte[]> entry : files.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zos.putNextEntry(zipEntry);
        zos.write(entry.getValue());
        zos.closeEntry();
      }
      zos.finish();
      return bos.toByteArray();
    } catch (IOException e) {
      throw new ServiceException("文档打包失败");
    }
  }

  private String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) {
      return "智能文档";
    }
    String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    if (cleaned.length() > 40) {
      cleaned = cleaned.substring(0, 40);
    }
    return cleaned.isEmpty() ? "智能文档" : cleaned;
  }
}

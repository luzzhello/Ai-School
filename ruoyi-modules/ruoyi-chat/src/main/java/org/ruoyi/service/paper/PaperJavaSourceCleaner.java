package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 清洗上传的项目代码：去除 import/注释/getter/setter 等噪声，保留可识别业务功能的代码。
 */
final class PaperJavaSourceCleaner {

    private static final Pattern FILE_BLOCK = Pattern.compile("// ===== (.+?) =====\\r?\\n([\\s\\S]*?)(?=\\r?\\n// ===== |\\z)");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final Pattern GETTER = Pattern.compile(
        "(?m)^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*(?:public|protected|private)\\s+[\\w<>,\\[\\]?.]+\\s+get\\w+\\(\\)\\s*\\{\\s*return\\s+[^;]+;\\s*\\}\\s*$");
    private static final Pattern SETTER = Pattern.compile(
        "(?m)^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*(?:public|protected|private)\\s+void\\s+set\\w+\\([^)]*\\)\\s*\\{\\s*this\\.[^;]+;\\s*\\}\\s*$");
    private static final Pattern METHOD_START = Pattern.compile(
        "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*(?:public|protected|private)\\s+.+\\([^)]*\\).*$");
    private static final Pattern MAPPING_ANNOTATION = Pattern.compile(
        "^@(RestController|Controller|Service|Mapper|Repository|Component|Transactional|GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping|Autowired|Resource)");

    private static final int MAX_LINES_PER_FILE = 150;
    private static final int MAX_METHOD_BODY_LINES = 18;

    private PaperJavaSourceCleaner() {
    }

    static String cleanProjectCode(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        if (code.contains("// ===== ")) {
            StringBuilder sb = new StringBuilder();
            Matcher matcher = FILE_BLOCK.matcher(code);
            while (matcher.find()) {
                String cleaned = cleanJavaFile(matcher.group(2));
                if (StringUtils.isNotBlank(cleaned)) {
                    sb.append("\n// ===== ").append(matcher.group(1)).append(" =====\n")
                        .append(cleaned).append('\n');
                }
            }
            String result = sb.toString().trim();
            return StringUtils.isNotBlank(result) ? result : cleanJavaFile(code);
        }
        return cleanJavaFile(code);
    }

    private static String cleanJavaFile(String source) {
        String cleaned = removeBlockComments(source);
        cleaned = removeLineComments(cleaned);
        cleaned = removeImportsAndPackage(cleaned);
        cleaned = removeGetterSetterMethods(cleaned);
        cleaned = truncateLongMethods(cleaned);
        cleaned = limitTotalLines(cleaned);
        cleaned = collapseBlankLines(cleaned);
        return cleaned.trim();
    }

    private static String removeBlockComments(String source) {
        return BLOCK_COMMENT.matcher(source).replaceAll("");
    }

    private static String removeLineComments(String source) {
        StringBuilder sb = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            boolean inString = false;
            int cut = line.length();
            for (int i = 0; i < line.length() - 1; i++) {
                char ch = line.charAt(i);
                if (ch == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (!inString && ch == '/' && line.charAt(i + 1) == '/') {
                    cut = i;
                    break;
                }
            }
            sb.append(line, 0, cut).append('\n');
        }
        return sb.toString();
    }

    private static String removeImportsAndPackage(String source) {
        StringBuilder sb = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            String trim = line.trim();
            if (trim.startsWith("import ") || trim.startsWith("package ")) {
                continue;
            }
            if (trim.contains("serialVersionUID")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String removeGetterSetterMethods(String source) {
        String cleaned = GETTER.matcher(source).replaceAll("");
        cleaned = SETTER.matcher(cleaned).replaceAll("");
        return cleaned.replaceAll("(?m)^\\s*@Override\\s*$", "");
    }

    private static String truncateLongMethods(String source) {
        String[] lines = source.split("\n", -1);
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (!isMethodStart(line)) {
                result.add(line);
                i++;
                continue;
            }
            List<String> chunk = new ArrayList<>();
            chunk.add(line);
            int brace = 0;
            boolean started = false;
            i++;
            while (i < lines.length) {
                String current = lines[i];
                chunk.add(current);
                for (char ch : current.toCharArray()) {
                    if (ch == '{') {
                        brace++;
                        started = true;
                    } else if (ch == '}') {
                        brace--;
                    }
                }
                i++;
                if (started && brace <= 0) {
                    break;
                }
            }
            if (chunk.size() - 1 > MAX_METHOD_BODY_LINES) {
                result.addAll(chunk.subList(0, Math.min(chunk.size(), MAX_METHOD_BODY_LINES)));
                result.add("        // ... 方法体已精简 ...");
                result.add("    }");
            } else {
                result.addAll(chunk);
            }
        }
        return String.join("\n", result);
    }

    private static boolean isMethodStart(String line) {
        return METHOD_START.matcher(line).matches() && line.contains("(") && !line.trim().endsWith(";");
    }

    private static String limitTotalLines(String source) {
        String[] lines = source.split("\n", -1);
        if (lines.length <= MAX_LINES_PER_FILE) {
            return source;
        }
        Set<Integer> keep = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(lines.length, 25); i++) {
            keep.add(i);
        }
        for (int i = 0; i < lines.length; i++) {
            if (scoreLine(lines[i]) >= 8) {
                keep.add(i);
            }
        }
        List<Integer> ordered = new ArrayList<>(keep);
        ordered.sort(Comparator.naturalOrder());
        if (ordered.size() > MAX_LINES_PER_FILE) {
            ordered = ordered.subList(0, MAX_LINES_PER_FILE);
        }
        StringBuilder sb = new StringBuilder();
        for (int index : ordered) {
            sb.append(lines[index]).append('\n');
        }
        sb.append("// ... 文件其余部分已精简 ...");
        return sb.toString();
    }

    private static int scoreLine(String line) {
        String trim = line.trim();
        if (trim.isEmpty()) {
            return 0;
        }
        if (MAPPING_ANNOTATION.matcher(trim).find()) {
            return 10;
        }
        if (trim.matches("^(public|protected)\\s+[\\w<>,\\[\\]?.]+\\s+\\w+\\s*\\(.*")) {
            return 9;
        }
        if (trim.contains("return ")) {
            return 7;
        }
        if (trim.matches(".*\\.(save|update|delete|insert|select|list|get|find|query|remove)\\w*\\(.*")) {
            return 8;
        }
        if (trim.matches("^\\w+\\.\\w+\\(.*")) {
            return 6;
        }
        if (trim.matches("^(if|for|while|throw|new)\\s.*")) {
            return 5;
        }
        if (trim.matches("^(private|protected)\\s+\\w+.*")) {
            return 4;
        }
        return 1;
    }

    private static String collapseBlankLines(String source) {
        return source.replaceAll("\n{3,}", "\n\n").trim();
    }
}

package org.ruoyi.service.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.config.ChatVisionProperties;
import org.ruoyi.domain.dto.response.PaperScreenshotsAnalyzeResponse;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.PaperUiScreenshot;
import org.ruoyi.domain.paper.PaperUiScreenshotImage;
import org.ruoyi.service.draw.impl.DrawChatModelSupport;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 论文生成智能体——系统实现功能截图组（一功能多图）的保存与 AI 视觉识别。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperScreenshotService {

    private static final Set<String> VALID_MODULES = Set.of("admin", "user");

    private static final String ANALYZE_PROMPT =
        "你是资深软件产品经理。请结合「系统上下文」与截图，判断："
            + "1) 所属业务功能名（短中文，如「角色管理」「骑行记录」）；"
            + "2) 界面类型 label，只能是：列表、新增、编辑、详情、其他 之一。"
            + "要求：功能名优先看左侧菜单高亮/当前页标题/表格业务对象，再对齐上下文中的表注释；"
            + "不同菜单项必须给出不同功能名（如「活动管理」「骑行路线管理」「论坛管理」不可都写成同一名）；"
            + "登录页功能名用「管理员登录」或「用户登录」；"
            + "功能名不超过 8 个汉字；不要章节编号或标点；无法判断时在上下文候选里选最贴切者。"
            + "只输出严格 JSON，不要其他文字或代码块，格式：{\"title\":\"功能名\",\"label\":\"列表\"}";

    private final PaperSessionStore paperSessionStore;
    private final PaperAssetService paperAssetService;
    private final IChatModelService chatModelService;
    private final ChatVisionProperties chatVisionProperties;
    private final ObjectMapper objectMapper;

    public void save(String sessionId, List<PaperUiScreenshot> screenshots) {
        requireSession(sessionId);
        List<PaperUiScreenshot> normalized = normalize(screenshots);
        paperSessionStore.update(sessionId, s -> s.setUiScreenshots(normalized));
    }

    /**
     * 识别各功能组内每张图的 label，并在功能标题为空时用首张图的 title 回填。
     * 会注入会话题目、SQL 表结构摘要与代码片段，减少纯看图误判。
     * <p>
     * 返回统计：{@code successCount}/{@code failCount} 按「单张图是否拿到有效 AI 结果」计数，
     * 模型初始化失败时 {@code modelReady=false} 且全部计入 fail。
     */
    public PaperScreenshotsAnalyzeResponse analyze(String sessionId, List<PaperUiScreenshot> items) {
        PaperSession session = requireSession(sessionId);
        List<PaperUiScreenshot> targets = normalize(items != null ? items : session.getUiScreenshots());
        int imageTotal = countAnalyzableImages(targets);
        String modelName = chatVisionProperties.getDefaultModel();

        if (targets.isEmpty() || imageTotal == 0) {
            paperSessionStore.update(sessionId, s -> s.setUiScreenshots(targets));
            return PaperScreenshotsAnalyzeResponse.builder()
                .screenshots(targets)
                .imageTotal(0)
                .successCount(0)
                .failCount(0)
                .modelReady(true)
                .modelName(modelName)
                .message("没有可识别的截图")
                .build();
        }

        ChatModel chatModel;
        try {
            chatModel = DrawChatModelSupport.buildVisionModel(chatModelService, modelName);
        } catch (Exception e) {
            log.error("论文截图识别：视觉模型初始化失败 model={}", modelName, e);
            paperSessionStore.update(sessionId, s -> s.setUiScreenshots(targets));
            return PaperScreenshotsAnalyzeResponse.builder()
                .screenshots(targets)
                .imageTotal(imageTotal)
                .successCount(0)
                .failCount(imageTotal)
                .modelReady(false)
                .modelName(modelName)
                .message("视觉模型不可用（" + modelName + "），请检查模型配置与 API Key")
                .build();
        }

        String systemContext = buildSystemContext(session);
        int successCount = 0;
        int failCount = 0;
        // imageId -> 识别到的功能名（用于同卡片多功能自动拆组）
        Map<String, String> recognizedTitleByImageId = new LinkedHashMap<>();

        for (PaperUiScreenshot group : targets) {
            if (group.getImages() == null) {
                continue;
            }
            for (PaperUiScreenshotImage image : group.getImages()) {
                if (StringUtils.isBlank(image.getAssetUrl())) {
                    continue;
                }
                try {
                    String moduleHint = "admin".equals(group.getModule()) ? "管理后台" : "用户端";
                    AnalyzeResult r = recognize(chatModel, image.getAssetUrl(), systemContext, moduleHint);
                    boolean gotTitle = StringUtils.isNotBlank(r.title());
                    boolean gotLabel = StringUtils.isNotBlank(r.label());
                    if (!gotTitle && !gotLabel) {
                        failCount++;
                        continue;
                    }
                    // 识别结果覆盖上传时的默认 label，便于「识别功能」真正生效
                    if (gotLabel) {
                        image.setLabel(r.label());
                    }
                    if (gotTitle) {
                        recognizedTitleByImageId.put(image.getId(), normalizeFeatureTitle(r.title()));
                    }
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("论文截图识别失败 sessionId={}, assetUrl={}", sessionId, image.getAssetUrl(), e);
                }
            }
        }

        int groupsBefore = targets.size();
        List<PaperUiScreenshot> afterSplit = splitGroupsByRecognizedTitles(targets, recognizedTitleByImageId);
        int splitAdded = Math.max(0, afterSplit.size() - groupsBefore);

        String message = buildAnalyzeMessage(imageTotal, successCount, failCount, true, splitAdded);
        log.info("论文截图识别完成 sessionId={}, groups={}(+{}), images={}, success={}, fail={}, model={}, contextChars={}",
            sessionId, afterSplit.size(), splitAdded, imageTotal, successCount, failCount, modelName, systemContext.length());

        paperSessionStore.update(sessionId, s -> s.setUiScreenshots(afterSplit));
        PaperSession updated = paperSessionStore.get(sessionId);
        List<PaperUiScreenshot> result = updated == null ? afterSplit : copyOf(updated.getUiScreenshots());
        return PaperScreenshotsAnalyzeResponse.builder()
            .screenshots(result)
            .imageTotal(imageTotal)
            .successCount(successCount)
            .failCount(failCount)
            .modelReady(true)
            .modelName(modelName)
            .message(message)
            .build();
    }

    static int countAnalyzableImages(List<PaperUiScreenshot> targets) {
        int n = 0;
        if (targets == null) {
            return 0;
        }
        for (PaperUiScreenshot group : targets) {
            if (group.getImages() == null) {
                continue;
            }
            for (PaperUiScreenshotImage image : group.getImages()) {
                if (StringUtils.isNotBlank(image.getAssetUrl())) {
                    n++;
                }
            }
        }
        return n;
    }

    static String buildAnalyzeMessage(int imageTotal, int successCount, int failCount, boolean modelReady) {
        return buildAnalyzeMessage(imageTotal, successCount, failCount, modelReady, 0);
    }

    static String buildAnalyzeMessage(
        int imageTotal, int successCount, int failCount, boolean modelReady, int splitAdded
    ) {
        if (!modelReady) {
            return "视觉模型不可用，识别未执行";
        }
        if (imageTotal <= 0) {
            return "没有可识别的截图";
        }
        String splitHint = splitAdded > 0
            ? "；同卡片内检出不同功能，已自动拆成多组（+" + splitAdded + "）"
            : "";
        if (failCount == 0) {
            return "已识别 " + successCount + " 张截图的功能名与界面类型，可再手动调整" + splitHint;
        }
        if (successCount == 0) {
            return "识别失败：共 " + imageTotal + " 张均未得到有效结果，请检查视觉模型或稍后重试";
        }
        return "部分识别成功：成功 " + successCount + " 张，失败 " + failCount + " 张，可手动调整后重试失败项" + splitHint;
    }

    /**
     * 同一功能卡片内若多张图识别出不同功能名，按功能名拆成多个功能组。
     * <p>
     * 未识别到功能名的图保留在原组（标题优先用原标题，否则「未命名功能」）。
     * 仅一种功能名时不拆，仅回填空标题。
     */
    static List<PaperUiScreenshot> splitGroupsByRecognizedTitles(
        List<PaperUiScreenshot> groups,
        Map<String, String> recognizedTitleByImageId
    ) {
        if (groups == null || groups.isEmpty()) {
            return groups == null ? List.of() : groups;
        }
        Map<String, String> titleMap = recognizedTitleByImageId == null
            ? Map.of() : recognizedTitleByImageId;
        List<PaperUiScreenshot> out = new ArrayList<>();
        for (PaperUiScreenshot group : groups) {
            out.addAll(splitOneGroup(group, titleMap));
        }
        renumberSort(out);
        return out;
    }

    private static List<PaperUiScreenshot> splitOneGroup(
        PaperUiScreenshot group,
        Map<String, String> titleMap
    ) {
        List<PaperUiScreenshotImage> images = group.getImages() == null
            ? List.of() : group.getImages();
        if (images.isEmpty()) {
            return List.of(group);
        }

        LinkedHashMap<String, List<PaperUiScreenshotImage>> buckets = new LinkedHashMap<>();
        List<PaperUiScreenshotImage> untitled = new ArrayList<>();
        for (PaperUiScreenshotImage image : images) {
            String rawTitle = titleMap.get(image.getId());
            String title = normalizeFeatureTitle(rawTitle);
            if (StringUtils.isBlank(title)) {
                untitled.add(image);
                continue;
            }
            buckets.computeIfAbsent(title, k -> new ArrayList<>()).add(image);
        }

        if (buckets.isEmpty()) {
            return List.of(group);
        }

        // 仅一种识别功能名：不拆组，空标题时回填
        if (buckets.size() == 1 && untitled.isEmpty()) {
            String only = buckets.keySet().iterator().next();
            if (StringUtils.isBlank(group.getTitle())) {
                group.setTitle(only);
            }
            return List.of(group);
        }
        if (buckets.size() == 1) {
            String only = buckets.keySet().iterator().next();
            List<PaperUiScreenshotImage> merged = new ArrayList<>(buckets.get(only));
            merged.addAll(untitled);
            renumberImageSort(merged);
            group.setImages(merged);
            if (StringUtils.isBlank(group.getTitle())) {
                group.setTitle(only);
            }
            return List.of(group);
        }

        // 多种功能名：按识别名拆组；无标题图挂到「未命名」组
        List<PaperUiScreenshot> split = new ArrayList<>();
        boolean keepOriginalId = true;
        for (Map.Entry<String, List<PaperUiScreenshotImage>> entry : buckets.entrySet()) {
            List<PaperUiScreenshotImage> imgs = entry.getValue();
            renumberImageSort(imgs);
            PaperUiScreenshot next = newGroupFrom(group, entry.getKey(), imgs, keepOriginalId);
            keepOriginalId = false;
            split.add(next);
        }
        if (!untitled.isEmpty()) {
            renumberImageSort(untitled);
            String fallbackTitle = StringUtils.isNotBlank(group.getTitle())
                ? group.getTitle().trim() : "未命名功能";
            // 若 fallback 与已有拆组重名，追加后缀避免合并错觉
            if (buckets.containsKey(normalizeFeatureTitle(fallbackTitle))) {
                fallbackTitle = "未命名功能";
            }
            split.add(newGroupFrom(group, fallbackTitle, untitled, false));
        }
        return split;
    }

    private static PaperUiScreenshot newGroupFrom(
        PaperUiScreenshot template,
        String title,
        List<PaperUiScreenshotImage> images,
        boolean reuseId
    ) {
        PaperUiScreenshot item = new PaperUiScreenshot();
        item.setId(reuseId && StringUtils.isNotBlank(template.getId())
            ? template.getId() : "uss_" + UUID.randomUUID());
        item.setModule(template.getModule());
        item.setTitle(title);
        item.setSort(template.getSort());
        item.setConfirmed(Boolean.FALSE);
        item.setImages(images);
        item.setAssetUrl(null);
        return item;
    }

    private static void renumberSort(List<PaperUiScreenshot> groups) {
        Map<String, Integer> cursor = new HashMap<>();
        for (PaperUiScreenshot g : groups) {
            String module = g.getModule() == null ? "" : g.getModule();
            int next = cursor.getOrDefault(module, 0) + 1;
            cursor.put(module, next);
            g.setSort(next);
        }
    }

    private static void renumberImageSort(List<PaperUiScreenshotImage> images) {
        for (int i = 0; i < images.size(); i++) {
            images.get(i).setSort(i + 1);
        }
    }

    /** 去掉首尾空白与末尾「功能」，便于「活动管理」「活动管理功能」归为一组。 */
    static String normalizeFeatureTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return "";
        }
        String t = title.trim().replaceAll("[\\s　]+", "");
        if (t.endsWith("功能") && t.length() > 2) {
            t = t.substring(0, t.length() - 2);
        }
        if (t.length() > 8) {
            t = t.substring(0, 8);
        }
        return t;
    }

    private AnalyzeResult recognize(ChatModel chatModel, String assetUrl, String systemContext, String moduleHint) {
        byte[] bytes = paperAssetService.readAssetBytes(assetUrl);
        String relative = paperAssetService.extractRelativePath(assetUrl);
        String mimeType = resolveMimeType(paperAssetService.resolveMediaType(relative));
        String base64 = Base64.getEncoder().encodeToString(bytes);

        StringBuilder prompt = new StringBuilder(ANALYZE_PROMPT);
        prompt.append("\n\n【端类型】").append(moduleHint);
        if (StringUtils.isNotBlank(systemContext)) {
            prompt.append("\n\n【系统上下文】\n").append(systemContext);
        } else {
            prompt.append("\n\n【系统上下文】暂无 SQL/代码，仅依据截图判断。");
        }

        UserMessage message = UserMessage.from(
            TextContent.from(prompt.toString()),
            ImageContent.from(base64, mimeType));

        ChatResponse response = chatModel.chat(List.of(message));
        String raw = response.aiMessage() == null ? null : response.aiMessage().text();
        return parseResult(raw);
    }

    /**
     * 拼装识别用上下文：题目 + SQL 表/注释/摘要 + 代码片段（截断）。
     */
    static String buildSystemContext(PaperSession session) {
        if (session == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(session.getTitle())) {
            sb.append("论文题目：").append(session.getTitle().trim()).append('\n');
        }
        PaperSession.UserInputs inputs = session.getUserInputs();
        if (inputs != null && StringUtils.isNotBlank(inputs.getEnvInfo())) {
            sb.append("技术环境：").append(truncate(inputs.getEnvInfo().trim(), 200)).append('\n');
        }
        PaperSession.SqlParsed parsed = session.getSqlParsed();
        if (parsed != null) {
            if (StringUtils.isNotBlank(parsed.getSummary())) {
                sb.append("功能推断：").append(truncate(parsed.getSummary().trim(), 500)).append('\n');
            }
            List<String> tables = parsed.getTables();
            if (tables != null && !tables.isEmpty()) {
                sb.append("数据表：");
                int n = 0;
                for (String table : tables) {
                    if (StringUtils.isBlank(table)) {
                        continue;
                    }
                    if (n > 0) {
                        sb.append("；");
                    }
                    sb.append(table.trim());
                    String comment = parsed.getTableComments() == null
                        ? null : parsed.getTableComments().get(table);
                    if (StringUtils.isNotBlank(comment)) {
                        sb.append('(').append(comment.trim()).append(')');
                    }
                    n++;
                    if (n >= 40) {
                        sb.append("…");
                        break;
                    }
                }
                sb.append('\n');
            }
        } else if (inputs != null && StringUtils.isNotBlank(inputs.getSqlContent())) {
            sb.append("SQL 片段：\n").append(truncate(inputs.getSqlContent().trim(), 1500)).append('\n');
        }
        if (inputs != null && StringUtils.isNotBlank(inputs.getCodeContent())) {
            sb.append("代码片段：\n").append(truncate(inputs.getCodeContent().trim(), 1200)).append('\n');
        }
        return sb.toString().trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private AnalyzeResult parseResult(String raw) {
        if (StringUtils.isBlank(raw)) {
            return new AnalyzeResult("", "");
        }
        String candidate = raw.trim();
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidate = candidate.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(candidate);
            String title = node.path("title").asText("").trim();
            String label = normalizeLabel(node.path("label").asText("").trim());
            return new AnalyzeResult(title, label);
        } catch (Exception e) {
            log.warn("论文截图识别结果解析失败 raw={}", raw);
            return new AnalyzeResult("", "");
        }
    }

    static String normalizeLabel(String label) {
        if (StringUtils.isBlank(label)) {
            return "其他";
        }
        if (label.contains("列表") || label.contains("查询") || label.contains("一览")) {
            return "列表";
        }
        if (label.contains("新增") || label.contains("添加") || label.contains("创建")) {
            return "新增";
        }
        if (label.contains("编辑") || label.contains("修改")) {
            return "编辑";
        }
        if (label.contains("详情") || label.contains("查看") || label.contains("明细")) {
            return "详情";
        }
        return "其他";
    }

    private String resolveMimeType(MediaType mediaType) {
        return mediaType == null ? "image/png" : mediaType.toString().split(";")[0].trim();
    }

    private List<PaperUiScreenshot> normalize(List<PaperUiScreenshot> screenshots) {
        List<PaperUiScreenshot> source = screenshots == null ? List.of() : screenshots;
        List<PaperUiScreenshot> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            PaperUiScreenshot raw = source.get(i);
            if (raw == null) {
                continue;
            }
            String module = StringUtils.defaultIfBlank(raw.getModule(), "").trim().toLowerCase();
            if (!VALID_MODULES.contains(module)) {
                throw new ServiceException("截图所属模块非法（仅支持 admin/user）: " + raw.getModule());
            }
            List<PaperUiScreenshotImage> images = normalizeImages(raw);
            if (images.isEmpty()) {
                continue;
            }
            PaperUiScreenshot item = new PaperUiScreenshot();
            item.setId(StringUtils.isNotBlank(raw.getId()) ? raw.getId().trim() : "uss_" + UUID.randomUUID());
            item.setModule(module);
            item.setTitle(raw.getTitle() == null ? "" : raw.getTitle().trim());
            item.setSort(raw.getSort() != null ? raw.getSort() : i);
            item.setConfirmed(raw.getConfirmed() != null && raw.getConfirmed());
            item.setImages(images);
            item.setAssetUrl(null);
            result.add(item);
        }
        return result;
    }

    private List<PaperUiScreenshotImage> normalizeImages(PaperUiScreenshot raw) {
        List<PaperUiScreenshotImage> result = new ArrayList<>();
        List<PaperUiScreenshotImage> source = raw.getImages();
        if (source != null && !source.isEmpty()) {
            int idx = 0;
            for (PaperUiScreenshotImage img : source) {
                if (img == null || StringUtils.isBlank(img.getAssetUrl())) {
                    continue;
                }
                PaperUiScreenshotImage copy = new PaperUiScreenshotImage();
                copy.setId(StringUtils.isNotBlank(img.getId()) ? img.getId().trim() : "usi_" + UUID.randomUUID());
                copy.setAssetUrl(img.getAssetUrl().trim());
                copy.setLabel(StringUtils.isNotBlank(img.getLabel()) ? normalizeLabel(img.getLabel()) : "");
                copy.setSort(img.getSort() != null ? img.getSort() : idx);
                result.add(copy);
                idx++;
            }
        } else if (StringUtils.isNotBlank(raw.getAssetUrl())) {
            // 旧版单图 → 迁移为 images[0]
            PaperUiScreenshotImage copy = new PaperUiScreenshotImage();
            copy.setId("usi_" + UUID.randomUUID());
            copy.setAssetUrl(raw.getAssetUrl().trim());
            copy.setLabel("其他");
            copy.setSort(0);
            result.add(copy);
        }
        result.sort(Comparator
            .comparing((PaperUiScreenshotImage i) -> i.getSort() == null ? Integer.MAX_VALUE : i.getSort())
            .thenComparing(i -> i.getId() == null ? "" : i.getId()));
        return result;
    }

    private List<PaperUiScreenshot> copyOf(List<PaperUiScreenshot> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    private PaperSession requireSession(String sessionId) {
        PaperSession session = paperSessionStore.get(sessionId);
        if (session == null) {
            throw new ServiceException("会话不存在: " + sessionId);
        }
        return session;
    }

    private record AnalyzeResult(String title, String label) {
    }
}

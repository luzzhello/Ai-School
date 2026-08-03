package org.ruoyi.service.paper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.PaperRewriteSegmentRequest;
import org.ruoyi.domain.dto.response.PaperRewriteSegmentResultVo;
import org.ruoyi.service.draw.impl.DrawChatModelSupport;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.config.ErDiagramProperties;
import org.springframework.stereotype.Service;

/**
 * 论文写作选区改写：扩写 / 缩写 / 自定义润色。
 * 计费复用 thesis_reduce（按字数）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperRewriteService {

    private static final int MIN_CHARS = 10;
    private static final int MAX_CHARS = 8000;

    private final IChatModelService chatModelService;
    private final ErDiagramProperties erDiagramProperties;
    private final IFeatureCoinService featureCoinService;

    public PaperRewriteSegmentResultVo rewrite(PaperRewriteSegmentRequest request) {
        String mode = StringUtils.trim(request.getMode()).toLowerCase();
        if (!"expand".equals(mode) && !"shrink".equals(mode) && !"polish".equals(mode)) {
            throw new ServiceException("不支持的改写类型，请使用 expand / shrink / polish");
        }
        String text = StringUtils.trim(request.getText());
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("待改写文本不能为空");
        }
        int words = countWords(text);
        if (words < MIN_CHARS) {
            throw new ServiceException("选区过短，请至少选择约 " + MIN_CHARS + " 字");
        }
        if (words > MAX_CHARS) {
            throw new ServiceException("选区过长（超过 " + MAX_CHARS + " 字），请缩短后再试");
        }

        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.THESIS_REDUCE, words);

        String rewritten = invokeRewrite(mode, text, request.getPrompt(), request.getModel());
        int cost = (int) featureCoinService.charge(userId, FeatureCodes.THESIS_REDUCE, words);

        PaperRewriteSegmentResultVo vo = new PaperRewriteSegmentResultVo();
        vo.setMode(mode);
        vo.setOriginalText(text);
        vo.setRewrittenText(rewritten);
        vo.setCostCoins(cost);
        return vo;
    }

    private String invokeRewrite(String mode, String text, String userPrompt, String modelName) {
        String system = switch (mode) {
            case "expand" -> """
                你是学术论文写作助手。请将用户给出的中文学术段落扩写为约原来的两倍篇幅。
                要求：保留原意与事实；补充合理论述与过渡，不编造具体数据/文献编号；不要改变人称与时态风格；
                直接输出扩写后正文，不要标题、不要解释、不要 markdown 代码块。
                """;
            case "shrink" -> """
                你是学术论文写作助手。请将用户给出的中文学术段落缩写为约原来的一半篇幅。
                要求：保留核心论点与关键信息；删除冗余套话；不要改变结论方向；
                直接输出缩写后正文，不要标题、不要解释、不要 markdown 代码块。
                """;
            default -> """
                你是学术论文写作助手。请按用户提示词对给定中文学术段落进行润色改写。
                要求：忠实原意；提升表达专业度与流畅度；不要编造具体数据或文献；
                直接输出润色后正文，不要标题、不要解释、不要 markdown 代码块。
                """;
        };
        StringBuilder user = new StringBuilder();
        if ("polish".equals(mode) && StringUtils.isNotBlank(userPrompt)) {
            user.append("润色要求：").append(userPrompt.trim()).append("\n\n");
        }
        user.append("原文：\n").append(text);

        String model = StringUtils.isNotBlank(modelName) ? modelName.trim() : erDiagramProperties.getDefaultModel();
        String raw = DrawChatModelSupport.chat(
            DrawChatModelSupport.buildModel(chatModelService, model),
            system,
            user.toString()
        );
        return cleanup(raw);
    }

    private static String cleanup(String response) {
        if (StringUtils.isBlank(response)) {
            throw new ServiceException("AI 改写未返回有效内容");
        }
        String text = response.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("AI 改写未返回有效内容");
        }
        return text;
    }

    private static int countWords(String text) {
        return text == null ? 0 : text.replaceAll("\\s+", "").length();
    }
}

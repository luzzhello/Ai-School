package org.ruoyi.service.draw.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.AigcSplitRequest;
import org.ruoyi.domain.dto.request.ThesisReduceRequest;
import org.ruoyi.domain.dto.request.ThesisReduceSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcSegmentVo;
import org.ruoyi.domain.vo.draw.AigcSplitResultVo;
import org.ruoyi.domain.vo.draw.ThesisReduceResultVo;
import org.ruoyi.domain.vo.draw.ThesisReduceSegmentResultVo;
import org.ruoyi.service.draw.IThesisReduceService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 论文降重（按片段 AI 改写）
 */
@Service
@RequiredArgsConstructor
public class ThesisReduceServiceImpl implements IThesisReduceService {

    private final IFeatureCoinService featureCoinService;
    private final ThesisLlmSupport thesisLlmSupport;

    @Override
    public ThesisReduceResultVo parse(ThesisReduceRequest request) {
        String title = StringUtils.trim(request.getTitle());
        String content = StringUtils.trim(request.getContent());
        String splitMode = StringUtils.defaultIfBlank(request.getSplitMode(), "sentence");

        if (StringUtils.isBlank(title)) {
            throw new ServiceException("论文标题不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("论文内容不能为空");
        }
        if (content.length() < 50) {
            throw new ServiceException("论文内容过短，请至少输入 50 字");
        }
        if (!"paragraph".equals(splitMode) && !"sentence".equals(splitMode)) {
            throw new ServiceException("分割方式不正确");
        }

        int wordCount = AigcTextSegmenter.countWords(content);
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.THESIS_REDUCE, wordCount);

        List<String> segments = AigcTextSegmenter.split(content, splitMode);
        List<String> reducedSegments = new ArrayList<>();
        int totalCost = 0;
        for (String segment : segments) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            int segmentWords = AigcTextSegmenter.countWords(segment);
            featureCoinService.requireAffordable(userId, FeatureCodes.THESIS_REDUCE, segmentWords);
            String reduced = thesisLlmSupport.reduceText(segment.trim(), null);
            totalCost += (int) featureCoinService.charge(userId, FeatureCodes.THESIS_REDUCE, segmentWords);
            reducedSegments.add(reduced);
        }
        String reducedContent = String.join("\n\n", reducedSegments);

        ThesisReduceResultVo vo = new ThesisReduceResultVo();
        vo.setTitle(title);
        vo.setWordCount(wordCount);
        vo.setBeforeRate(0.0);
        vo.setAfterRate(0.0);
        vo.setCostCoins(totalCost);
        vo.setSplitMode(splitMode);
        vo.setSegmentCount(reducedSegments.size());
        vo.setReducedContent(reducedContent);
        vo.setReducedSegments(reducedSegments);
        vo.setSummary(String.format("共处理 %d 个片段，请人工复核后使用。", reducedSegments.size()));
        return vo;
    }

    @Override
    public AigcSplitResultVo split(AigcSplitRequest request) {
        String content = StringUtils.trim(request.getContent());
        String splitMode = StringUtils.defaultIfBlank(request.getSplitMode(), "outline");
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("论文内容不能为空");
        }
        if (!"paragraph".equals(splitMode) && !"sentence".equals(splitMode) && !"outline".equals(splitMode)) {
            throw new ServiceException("分割方式不正确");
        }

        List<String> segments = AigcTextSegmenter.split(content, splitMode);
        List<AigcOutlineSegmenter.OutlinePart> outlineParts = null;
        if ("outline".equals(splitMode)) {
            outlineParts = AigcOutlineSegmenter.splitFromText(content);
        }
        return buildSplitResult(splitMode, segments, outlineParts);
    }

    @Override
    public AigcSplitResultVo splitFromFile(MultipartFile file, String splitMode) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请上传文件");
        }
        String mode = StringUtils.defaultIfBlank(splitMode, "outline");
        if (!"outline".equals(mode)) {
            throw new ServiceException("文件分段仅支持按目录/标题模式");
        }
        String fileName = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        try {
            List<AigcOutlineSegmenter.OutlinePart> parts;
            if (fileName.endsWith(".docx")) {
                parts = AigcOutlineSegmenter.splitFromDocx(file.getInputStream());
            }
            else {
                String content = new String(file.getBytes());
                parts = AigcOutlineSegmenter.splitFromText(content);
            }
            if (parts.isEmpty()) {
                throw new ServiceException("未能识别论文章节结构，请改用分段/分句或检查标题样式");
            }
            List<String> texts = parts.stream().map(AigcOutlineSegmenter.OutlinePart::segmentText).toList();
            return buildSplitResult(mode, texts, parts);
        }
        catch (IOException e) {
            throw new ServiceException("文件读取失败");
        }
    }

    @Override
    public ThesisReduceSegmentResultVo reduceSegment(ThesisReduceSegmentRequest request) {
        String text = StringUtils.trim(request.getText());
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("片段内容不能为空");
        }
        if (text.length() < 2) {
            throw new ServiceException("片段过短，无需降重");
        }

        int wordCount = AigcTextSegmenter.countWords(text);
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.THESIS_REDUCE, wordCount);

        String reduced = thesisLlmSupport.reduceText(text, request.getModel());
        int cost = (int) featureCoinService.charge(userId, FeatureCodes.THESIS_REDUCE, wordCount);

        ThesisReduceSegmentResultVo vo = new ThesisReduceSegmentResultVo();
        vo.setOriginalText(text);
        vo.setReducedText(reduced);
        vo.setCostCoins(cost);
        return vo;
    }

    private AigcSplitResultVo buildSplitResult(String splitMode, List<String> segments, List<AigcOutlineSegmenter.OutlinePart> outlineParts) {
        List<AigcSegmentVo> items = new ArrayList<>();
        int totalWords = 0;
        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i);
            int wordCount = AigcTextSegmenter.countWords(text);
            totalWords += wordCount;
            AigcSegmentVo item = new AigcSegmentVo();
            item.setIndex(i);
            item.setText(text);
            item.setWordCount(wordCount);
            if (outlineParts != null && i < outlineParts.size()) {
                AigcOutlineSegmenter.OutlinePart part = outlineParts.get(i);
                item.setTitle(part.title());
                item.setLevel(part.level());
            }
            items.add(item);
        }

        AigcSplitResultVo vo = new AigcSplitResultVo();
        vo.setSplitMode(splitMode);
        vo.setSegmentCount(items.size());
        vo.setWordCount(totalWords);
        vo.setSegments(items);
        return vo;
    }
}

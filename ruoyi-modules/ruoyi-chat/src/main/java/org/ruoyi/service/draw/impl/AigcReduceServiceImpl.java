package org.ruoyi.service.draw.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.AigcReduceSegmentRequest;
import org.ruoyi.domain.dto.request.AigcSplitRequest;
import org.ruoyi.domain.vo.draw.AigcReduceSegmentResultVo;
import org.ruoyi.domain.vo.draw.AigcSegmentVo;
import org.ruoyi.domain.vo.draw.AigcSplitResultVo;
import org.ruoyi.service.draw.IAigcReduceService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AigcReduceServiceImpl implements IAigcReduceService {

    private final IFeatureCoinService featureCoinService;
    private final AigcLlmSupport aigcLlmSupport;

    @Override
    public AigcSplitResultVo split(AigcSplitRequest request) {
        String content = StringUtils.trim(request.getContent());
        String splitMode = StringUtils.defaultIfBlank(request.getSplitMode(), "sentence");
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("论文内容不能为空");
        }
        if (!"paragraph".equals(splitMode) && !"sentence".equals(splitMode) && !"outline".equals(splitMode)) {
            throw new ServiceException("分割方式不正确");
        }

        List<String> segments;
        List<AigcOutlineSegmenter.OutlinePart> outlineParts = null;
        if ("outline".equals(splitMode)) {
            outlineParts = AigcOutlineSegmenter.splitFromText(content).stream()
                .filter(part -> StringUtils.isNotBlank(part.segmentText()))
                .toList();
            segments = outlineParts.stream()
                .map(AigcOutlineSegmenter.OutlinePart::segmentText)
                .toList();
            if (segments.isEmpty()) {
                throw new ServiceException("未能识别论文章节结构，请改用分段/分句或检查标题格式");
            }
        }
        else {
            segments = AigcTextSegmenter.split(content, splitMode);
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

    @Override
    public AigcReduceSegmentResultVo reduceSegment(AigcReduceSegmentRequest request) {
        String text = StringUtils.trim(request.getText());
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("片段内容不能为空");
        }
        if (text.length() < 2) {
            throw new ServiceException("片段过短，无需降 AIGC");
        }

        int wordCount = AigcTextSegmenter.countWords(text);
        Long userId = LoginHelper.getUserId();
        boolean detectBefore = request.getDetectBefore() == null || Boolean.TRUE.equals(request.getDetectBefore());
        boolean detectAfter = request.getDetectAfter() == null || Boolean.TRUE.equals(request.getDetectAfter());

        int detectWords = 0;
        if (detectBefore) {
            detectWords += wordCount;
        }

        featureCoinService.requireAffordable(userId, FeatureCodes.AIGC_REDUCE, wordCount);
        if (detectWords > 0) {
            featureCoinService.requireAffordable(userId, FeatureCodes.AIGC_DETECT, detectWords);
        }

        String model = request.getModel();
        Double beforeRate = detectBefore ? aigcLlmSupport.detectAigcRate(text, model) : null;
        // 传入改写前参考率：按同一检测口径复检，未下降则加严重试
        String reduced = aigcLlmSupport.reduceText(text, model, beforeRate);
        Double afterRate = detectAfter ? aigcLlmSupport.detectAigcRate(reduced, model) : null;

        int totalCost = 0;
        if (detectBefore) {
            totalCost += (int) featureCoinService.charge(userId, FeatureCodes.AIGC_DETECT, wordCount);
        }
        totalCost += (int) featureCoinService.charge(userId, FeatureCodes.AIGC_REDUCE, wordCount);
        if (detectAfter) {
            totalCost += (int) featureCoinService.charge(userId, FeatureCodes.AIGC_DETECT, AigcTextSegmenter.countWords(reduced));
        }

        AigcReduceSegmentResultVo vo = new AigcReduceSegmentResultVo();
        vo.setOriginalText(text);
        vo.setReducedText(reduced);
        vo.setBeforeRate(beforeRate);
        vo.setAfterRate(afterRate);
        vo.setCostCoins(totalCost);
        return vo;
    }
}

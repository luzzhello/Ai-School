package org.ruoyi.service.draw.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.AigcDetectRequest;
import org.ruoyi.domain.dto.request.AigcDetectSegmentRequest;
import org.ruoyi.domain.vo.draw.AigcDetectResultVo;
import org.ruoyi.domain.vo.draw.AigcDetectSegmentResultVo;
import org.ruoyi.service.draw.IAigcDetectService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AigcDetectServiceImpl implements IAigcDetectService {

    private final IFeatureCoinService featureCoinService;
    private final AigcLlmSupport aigcLlmSupport;

    @Override
    public AigcDetectResultVo detect(AigcDetectRequest request) {
        String title = StringUtils.trim(request.getTitle());
        String content = StringUtils.trim(request.getContent());
        if (StringUtils.isBlank(title)) {
            throw new ServiceException("论文标题不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("论文内容不能为空");
        }
        if (content.length() < 50) {
            throw new ServiceException("论文内容过短，请至少输入 50 字");
        }

        int wordCount = AigcTextSegmenter.countWords(content);
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.AIGC_DETECT, wordCount);

        double aigcRate = aigcLlmSupport.detectAigcRate(content, null);
        double humanRate = Math.max(0, Math.min(100, 100 - aigcRate));

        AigcDetectResultVo vo = new AigcDetectResultVo();
        vo.setTitle(title);
        vo.setWordCount(wordCount);
        vo.setAigcRate(aigcRate);
        vo.setHumanRate(humanRate);
        long cost = featureCoinService.charge(userId, FeatureCodes.AIGC_DETECT, wordCount);
        vo.setCostCoins((int) cost);
        vo.setSummary(buildSummary(aigcRate, wordCount));
        return vo;
    }

    @Override
    public AigcDetectSegmentResultVo detectSegment(AigcDetectSegmentRequest request) {
        String text = StringUtils.trim(request.getText());
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("片段内容不能为空");
        }
        int wordCount = AigcTextSegmenter.countWords(text);
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, FeatureCodes.AIGC_DETECT, wordCount);

        double aigcRate = aigcLlmSupport.detectAigcRate(text, request.getModel());
        AigcDetectSegmentResultVo vo = new AigcDetectSegmentResultVo();
        vo.setAigcRate(aigcRate);
        vo.setHumanRate(Math.max(0, Math.min(100, 100 - aigcRate)));
        long cost = featureCoinService.charge(userId, FeatureCodes.AIGC_DETECT, wordCount);
        vo.setCostCoins((int) cost);
        return vo;
    }

    private String buildSummary(double aigcRate, int wordCount) {
        if (aigcRate >= 60) {
            return String.format("文本约 %d 字，AIGC 特征较明显，建议对高 AI 率片段逐句降 AIGC。", wordCount);
        }
        if (aigcRate >= 35) {
            return String.format("文本约 %d 字，存在一定 AI 生成特征，可针对重点片段改写。", wordCount);
        }
        return String.format("文本约 %d 字，整体更接近人工撰写风格。", wordCount);
    }
}

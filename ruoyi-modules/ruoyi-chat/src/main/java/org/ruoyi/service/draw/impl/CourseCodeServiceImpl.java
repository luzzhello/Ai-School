package org.ruoyi.service.draw.impl;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.CourseCodeGenerateRequest;
import org.ruoyi.domain.vo.draw.CourseCodeResultVo;
import org.ruoyi.service.draw.ICourseCodeService;
import org.ruoyi.service.usercenter.FeatureCodes;
import org.ruoyi.service.usercenter.IFeatureCoinService;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课设代码生成：基于 template-backend / template-frontend 模板打包 ZIP
 */
@Service
@RequiredArgsConstructor
public class CourseCodeServiceImpl implements ICourseCodeService {

    private static final Pattern TABLE_NAME = Pattern.compile("(?i)create\\s+table\\s+[`']?(\\w+)[`']?");

    private final IFeatureCoinService featureCoinService;
    private final CourseCodeZipBuilder zipBuilder;

    @Override
    public CourseCodeResultVo generate(CourseCodeGenerateRequest request) {
        String mode = StringUtils.defaultIfBlank(request.getMode(), "ai");
        String content = StringUtils.trim(request.getContent());
        String author = StringUtils.trim(request.getAuthor());

        if (StringUtils.isBlank(content)) {
            throw new ServiceException("生成内容不能为空");
        }
        if (content.length() < 10) {
            throw new ServiceException("内容过短，请补充需求或 SQL");
        }
        if (StringUtils.isBlank(author)) {
            throw new ServiceException("作者不能为空");
        }
        if (!"ai".equals(mode) && !"sql".equals(mode)) {
            throw new ServiceException("生成模式不正确");
        }

        String featureCode = "sql".equals(mode) ? FeatureCodes.COURSE_CODE_SQL : FeatureCodes.COURSE_CODE_AI;
        Long userId = LoginHelper.getUserId();
        featureCoinService.requireAffordable(userId, featureCode, null);

        String projectName = resolveProjectName(mode, content);
        List<SqlTableDocParser.SqlTableDef> tables = "sql".equals(mode)
            ? SqlTableDocParser.parse(content)
            : List.of();

        CourseCodeZipBuilder.ZipBuildResult zipResult = zipBuilder.build(request, projectName, tables);

        long cost = featureCoinService.charge(userId, featureCode, null);

        CourseCodeResultVo vo = new CourseCodeResultVo();
        vo.setProjectName(projectName);
        vo.setAuthor(author);
        vo.setFileCount(zipResult.fileCount());
        vo.setCostCoins((int) cost);
        vo.setTechStack("Spring Boot 3 + MyBatis-Plus + Ant Design Pro 前端");
        vo.setFiles(zipResult.files());
        vo.setDownloadFileName(projectName + "-code.zip");
        vo.setZipBase64(Base64.getEncoder().encodeToString(zipResult.bytes()));
        vo.setSummary(String.format(
            "已根据%s生成「%s」完整课设代码压缩包，共 %d 个文件。解压后含 template-backend、template-frontend 与 README，按说明安装依赖并启动即可。",
            "sql".equals(mode) ? "SQL" : "需求描述",
            projectName,
            zipResult.fileCount()));
        return vo;
    }

    private String resolveProjectName(String mode, String content) {
        if ("sql".equals(mode)) {
            Matcher matcher = TABLE_NAME.matcher(content);
            if (matcher.find()) {
                return toPascalCase(matcher.group(1)) + "System";
            }
        }
        if (content.contains("图书")) {
            return "BookManageSystem";
        }
        if (content.contains("学生")) {
            return "StudentManageSystem";
        }
        if (content.contains("商品") || content.contains("订单")) {
            return "GoodsManageSystem";
        }
        return "CourseProjectSystem";
    }

    private String toPascalCase(String name) {
        if (StringUtils.isBlank(name)) {
            return "Main";
        }
        String[] parts = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? "Main" : sb.toString();
    }
}

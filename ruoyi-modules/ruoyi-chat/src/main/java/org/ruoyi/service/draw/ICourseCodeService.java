package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.CourseCodeGenerateRequest;
import org.ruoyi.domain.vo.draw.CourseCodeResultVo;

/**
 * 课设代码生成
 */
public interface ICourseCodeService {

    CourseCodeResultVo generate(CourseCodeGenerateRequest request);
}

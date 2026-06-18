package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.dto.request.CourseCodeGenerateRequest;
import org.ruoyi.domain.vo.draw.CourseCodeResultVo;
import org.ruoyi.service.draw.ICourseCodeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课设代码生成
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/course-code")
public class CourseCodeController {

    private final ICourseCodeService courseCodeService;

    @PostMapping("/generate")
    public R<CourseCodeResultVo> generate(@RequestBody @Valid CourseCodeGenerateRequest request) {
        return R.ok(courseCodeService.generate(request));
    }
}

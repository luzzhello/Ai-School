package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 课设代码生成结果
 */
@Data
public class CourseCodeResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String projectName;

    private String author;

    private Integer fileCount;

    private Integer costCoins;

    private String techStack;

    private String summary;

    private List<String> files;

    /** 下载文件名 */
    private String downloadFileName;

    /** ZIP 文件 Base64（前端触发下载） */
    private String zipBase64;
}

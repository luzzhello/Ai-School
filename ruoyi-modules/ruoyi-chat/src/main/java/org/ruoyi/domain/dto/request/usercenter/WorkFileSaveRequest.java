package org.ruoyi.domain.dto.request.usercenter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkFileSaveRequest {

    private Long fileId;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String description;

    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    private String thumbnail;

    private String contentJson;
}

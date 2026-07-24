package org.ruoyi.domain.dto.request.usercenter;

import lombok.Data;

@Data
public class WorkFileQueryRequest {

    private String fileName;

    private String fileType;

    /** 子类型精确匹配（如软件工程图当前图类型） */
    private String subType;
}

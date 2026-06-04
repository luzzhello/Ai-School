package org.ruoyi.domain.dto.request;

import lombok.Data;

/**
 * 用例说明表结构数据
 */
@Data
public class UseCaseSpecData {

    private String useCaseName;
    private String role;
    private String description;
    private String preconditions;
    private String postconditions;
    private String basicFlow;
    private String extensionFlow;
    private String exceptionFlow;
    private String others;
}

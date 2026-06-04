package org.ruoyi.domain.dto.request;

import lombok.Data;

/**
 * 功能测试用例行
 */
@Data
public class FuncTestCaseData {

    private String caseId;
    private String caseName;
    private String preconditions;
    private String testSteps;
    private String expectedResult;
    private String testResult;
}

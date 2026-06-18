package org.ruoyi.domain.vo.draw;

import lombok.Data;

@Data
public class DocumentAgentTaskRecordVo {

  private String taskCode;

  private String taskTitle;

  private String toolName;

  private String taskKind;

  private Integer sortOrder;

  private String status;

  private String content;

  private String errorMsg;
}

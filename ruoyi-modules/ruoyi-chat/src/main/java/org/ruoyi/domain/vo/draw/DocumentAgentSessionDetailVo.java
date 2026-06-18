package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.util.List;

@Data
public class DocumentAgentSessionDetailVo {

  private String sessionId;

  private String description;

  private String projectTitle;

  private String status;

  private Long costCoins;

  private String downloadFileName;

  private String downloadBase64;

  private List<DocumentAgentTaskRecordVo> tasks;
}

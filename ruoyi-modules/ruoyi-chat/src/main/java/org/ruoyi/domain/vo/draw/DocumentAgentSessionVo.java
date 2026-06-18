package org.ruoyi.domain.vo.draw;

import lombok.Data;

import java.util.Date;

@Data
public class DocumentAgentSessionVo {

  private String sessionId;

  private String description;

  private String projectTitle;

  private String status;

  private Long costCoins;

  private String downloadFileName;

  private Boolean hasDownload;

  private Date createTime;

  private Date updateTime;
}

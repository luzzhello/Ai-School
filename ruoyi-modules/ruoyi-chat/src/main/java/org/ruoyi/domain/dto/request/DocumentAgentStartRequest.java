package org.ruoyi.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能文档 Agent 启动请求
 */
@Data
public class DocumentAgentStartRequest {

  @NotBlank(message = "请输入文档需求描述")
  @Size(max = 4000, message = "需求描述不能超过4000字")
  private String description;

  /** 可选模型名称 */
  private String model;
}

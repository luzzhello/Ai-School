package org.ruoyi.domain.entity.draw;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("doc_agent_session")
public class DocAgentSession implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @TableId(value = "id")
  private Long id;

  private String sessionId;

  private Long userId;

  private String description;

  private String modelName;

  private String projectTitle;

  /** RUNNING / STOPPED / FAILED / COMPLETED */
  private String status;

  private String charged;

  private Long costCoins;

  private String failedTaskCode;

  private String downloadFileName;

  private String downloadBase64;

  private Date createTime;

  private Date updateTime;
}

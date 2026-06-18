package org.ruoyi.domain.entity.draw;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("doc_agent_task_def")
public class DocAgentTaskDef implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @TableId(value = "id")
  private Long id;

  private String taskCode;

  private String taskTitle;

  private String toolName;

  private String taskKind;

  private Integer sortOrder;

  private String status;

  private String remark;

  private Date createTime;

  private Date updateTime;
}

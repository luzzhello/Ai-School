package org.ruoyi.service.draw.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.domain.entity.draw.DocAgentSession;
import org.ruoyi.domain.entity.draw.DocAgentTaskDef;
import org.ruoyi.domain.entity.draw.DocAgentTaskRecord;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionDetailVo;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionVo;
import org.ruoyi.domain.vo.draw.DocumentAgentTaskRecordVo;
import org.ruoyi.mapper.draw.DocAgentSessionMapper;
import org.ruoyi.mapper.draw.DocAgentTaskDefMapper;
import org.ruoyi.mapper.draw.DocAgentTaskRecordMapper;
import org.ruoyi.service.draw.DocumentAgentSessionStatus;
import org.ruoyi.service.draw.DocumentAgentTaskStatus;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class DocumentAgentPersistence {

  private final DocAgentTaskDefMapper taskDefMapper;
  private final DocAgentSessionMapper sessionMapper;
  private final DocAgentTaskRecordMapper taskRecordMapper;

  List<DocAgentTaskDef> listEnabledTaskDefs() {
    LambdaQueryWrapper<DocAgentTaskDef> lqw = Wrappers.lambdaQuery();
    lqw.eq(DocAgentTaskDef::getStatus, "0");
    lqw.orderByAsc(DocAgentTaskDef::getSortOrder);
    return taskDefMapper.selectList(lqw);
  }

  DocAgentSession requireSession(String sessionId, Long userId) {
    DocAgentSession session = sessionMapper.selectOne(Wrappers.<DocAgentSession>lambdaQuery()
      .eq(DocAgentSession::getSessionId, sessionId));
    if (session == null) {
      throw new ServiceException("会话不存在或已过期");
    }
    if (!session.getUserId().equals(userId)) {
      throw new ServiceException("无权访问该会话");
    }
    return session;
  }

  DocAgentSession createSession(String sessionId, Long userId, String description, String modelName, long costCoins) {
    Date now = new Date();
    DocAgentSession session = new DocAgentSession();
    session.setSessionId(sessionId);
    session.setUserId(userId);
    session.setDescription(description);
    session.setModelName(modelName);
    session.setProjectTitle("智能文档");
    session.setStatus(DocumentAgentSessionStatus.RUNNING);
    session.setCharged("1");
    session.setCostCoins(costCoins);
    session.setCreateTime(now);
    session.setUpdateTime(now);
    sessionMapper.insert(session);

    for (DocAgentTaskDef def : listEnabledTaskDefs()) {
      DocAgentTaskRecord record = new DocAgentTaskRecord();
      record.setSessionId(sessionId);
      record.setTaskCode(def.getTaskCode());
      record.setTaskTitle(def.getTaskTitle());
      record.setToolName(def.getToolName());
      record.setTaskKind(def.getTaskKind());
      record.setSortOrder(def.getSortOrder());
      record.setStatus(DocumentAgentTaskStatus.PENDING);
      record.setCreateTime(now);
      record.setUpdateTime(now);
      taskRecordMapper.insert(record);
    }
    return session;
  }

  List<DocAgentTaskRecord> listTaskRecords(String sessionId) {
    return taskRecordMapper.selectList(Wrappers.<DocAgentTaskRecord>lambdaQuery()
      .eq(DocAgentTaskRecord::getSessionId, sessionId)
      .orderByAsc(DocAgentTaskRecord::getSortOrder));
  }

  Map<String, String> loadArtifactMap(String sessionId) {
    Map<String, String> map = new LinkedHashMap<>();
    for (DocAgentTaskRecord record : listTaskRecords(sessionId)) {
      if (StringUtils.isNotBlank(record.getContent())) {
        map.put(record.getTaskCode(), record.getContent());
      }
    }
    return map;
  }

  void markTaskRunning(String sessionId, String taskCode) {
    DocAgentTaskRecord record = getRecord(sessionId, taskCode);
    record.setStatus(DocumentAgentTaskStatus.RUNNING);
    record.setStartTime(new Date());
    record.setErrorMsg(null);
    record.setUpdateTime(new Date());
    taskRecordMapper.updateById(record);
    touchSession(sessionId, DocumentAgentSessionStatus.RUNNING, null, null);
  }

  void markTaskDone(String sessionId, String taskCode, String content) {
    DocAgentTaskRecord record = getRecord(sessionId, taskCode);
    record.setStatus(DocumentAgentTaskStatus.DONE);
    record.setContent(content);
    record.setEndTime(new Date());
    record.setUpdateTime(new Date());
    taskRecordMapper.updateById(record);
  }

  void markTaskFailed(String sessionId, String taskCode, String errorMsg) {
    DocAgentTaskRecord record = getRecord(sessionId, taskCode);
    record.setStatus(DocumentAgentTaskStatus.FAILED);
    record.setErrorMsg(errorMsg);
    record.setEndTime(new Date());
    record.setUpdateTime(new Date());
    taskRecordMapper.updateById(record);
    touchSession(sessionId, DocumentAgentSessionStatus.FAILED, taskCode, null);
  }

  void markSessionStopped(String sessionId, String currentTaskCode) {
    if (StringUtils.isNotBlank(currentTaskCode)) {
      DocAgentTaskRecord record = getRecord(sessionId, currentTaskCode);
      if (DocumentAgentTaskStatus.RUNNING.equals(record.getStatus())) {
        record.setStatus(DocumentAgentTaskStatus.STOPPED);
        record.setEndTime(new Date());
        record.setUpdateTime(new Date());
        taskRecordMapper.updateById(record);
      }
    }
    touchSession(sessionId, DocumentAgentSessionStatus.STOPPED, currentTaskCode, null);
  }

  void markSessionCompleted(String sessionId, String projectTitle, String downloadFileName, String downloadBase64) {
    DocAgentSession session = getSessionById(sessionId);
    session.setStatus(DocumentAgentSessionStatus.COMPLETED);
    session.setProjectTitle(projectTitle);
    session.setDownloadFileName(downloadFileName);
    session.setDownloadBase64(downloadBase64);
    session.setFailedTaskCode(null);
    session.setUpdateTime(new Date());
    sessionMapper.updateById(session);
  }

  void updateProjectTitle(String sessionId, String projectTitle) {
    DocAgentSession session = getSessionById(sessionId);
    session.setProjectTitle(projectTitle);
    session.setUpdateTime(new Date());
    sessionMapper.updateById(session);
  }

  List<DocumentAgentSessionVo> listUserSessions(Long userId, int limit) {
    List<DocAgentSession> rows = sessionMapper.selectList(Wrappers.<DocAgentSession>lambdaQuery()
      .eq(DocAgentSession::getUserId, userId)
      .orderByDesc(DocAgentSession::getCreateTime)
      .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    return rows.stream().map(this::toSessionVo).collect(Collectors.toList());
  }

  DocumentAgentSessionDetailVo getSessionDetail(String sessionId, Long userId, boolean includeDownload) {
    DocAgentSession session = requireSession(sessionId, userId);
    DocumentAgentSessionDetailVo vo = new DocumentAgentSessionDetailVo();
    vo.setSessionId(session.getSessionId());
    vo.setDescription(session.getDescription());
    vo.setProjectTitle(session.getProjectTitle());
    vo.setStatus(session.getStatus());
    vo.setCostCoins(session.getCostCoins());
    vo.setDownloadFileName(session.getDownloadFileName());
    if (includeDownload) {
      vo.setDownloadBase64(session.getDownloadBase64());
    }
    vo.setTasks(listTaskRecords(sessionId).stream().map(this::toTaskVo).collect(Collectors.toList()));
    return vo;
  }

  private DocAgentSession getSessionById(String sessionId) {
    DocAgentSession session = sessionMapper.selectOne(Wrappers.<DocAgentSession>lambdaQuery()
      .eq(DocAgentSession::getSessionId, sessionId));
    if (session == null) {
      throw new ServiceException("会话不存在");
    }
    return session;
  }

  private DocAgentTaskRecord getRecord(String sessionId, String taskCode) {
    DocAgentTaskRecord record = taskRecordMapper.selectOne(Wrappers.<DocAgentTaskRecord>lambdaQuery()
      .eq(DocAgentTaskRecord::getSessionId, sessionId)
      .eq(DocAgentTaskRecord::getTaskCode, taskCode));
    if (record == null) {
      throw new ServiceException("任务记录不存在: " + taskCode);
    }
    return record;
  }

  private void touchSession(String sessionId, String status, String failedTaskCode, String projectTitle) {
    DocAgentSession session = getSessionById(sessionId);
    session.setStatus(status);
    if (failedTaskCode != null) {
      session.setFailedTaskCode(failedTaskCode);
    }
    if (projectTitle != null) {
      session.setProjectTitle(projectTitle);
    }
    session.setUpdateTime(new Date());
    sessionMapper.updateById(session);
  }

  private DocumentAgentSessionVo toSessionVo(DocAgentSession session) {
    DocumentAgentSessionVo vo = new DocumentAgentSessionVo();
    vo.setSessionId(session.getSessionId());
    vo.setDescription(session.getDescription());
    vo.setProjectTitle(session.getProjectTitle());
    vo.setStatus(session.getStatus());
    vo.setCostCoins(session.getCostCoins());
    vo.setDownloadFileName(session.getDownloadFileName());
    vo.setHasDownload(StringUtils.isNotBlank(session.getDownloadBase64()));
    vo.setCreateTime(session.getCreateTime());
    vo.setUpdateTime(session.getUpdateTime());
    return vo;
  }

  private DocumentAgentTaskRecordVo toTaskVo(DocAgentTaskRecord record) {
    DocumentAgentTaskRecordVo vo = new DocumentAgentTaskRecordVo();
    vo.setTaskCode(record.getTaskCode());
    vo.setTaskTitle(record.getTaskTitle());
    vo.setToolName(record.getToolName());
    vo.setTaskKind(record.getTaskKind());
    vo.setSortOrder(record.getSortOrder());
    vo.setStatus(record.getStatus());
    vo.setContent(record.getContent());
    vo.setErrorMsg(record.getErrorMsg());
    return vo;
  }
}

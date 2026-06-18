package org.ruoyi.service.draw;

import org.ruoyi.domain.dto.request.DocumentAgentStartRequest;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionDetailVo;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionVo;
import org.ruoyi.domain.vo.draw.DocumentAgentStartVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能文档 Agent
 */
public interface IDocumentAgentService {

  DocumentAgentStartVo start(DocumentAgentStartRequest request, Long userId);

  void stream(String sessionId, Long userId, SseEmitter emitter);

  void stop(String sessionId, Long userId);

  List<DocumentAgentSessionVo> listSessions(Long userId, Integer limit);

  DocumentAgentSessionDetailVo getSessionDetail(String sessionId, Long userId);
}

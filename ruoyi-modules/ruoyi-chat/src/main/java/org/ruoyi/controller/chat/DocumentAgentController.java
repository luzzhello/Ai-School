package org.ruoyi.controller.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.dto.request.DocumentAgentStartRequest;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionDetailVo;
import org.ruoyi.domain.vo.draw.DocumentAgentSessionVo;
import org.ruoyi.domain.vo.draw.DocumentAgentStartVo;
import org.ruoyi.service.draw.IDocumentAgentService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能文档 Agent
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/document-agent")
public class DocumentAgentController {

  private final IDocumentAgentService documentAgentService;

  @PostMapping("/start")
  public R<DocumentAgentStartVo> start(@RequestBody @Valid DocumentAgentStartRequest request) {
    Long userId = LoginHelper.getUserId();
    return R.ok(documentAgentService.start(request, userId));
  }

  @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String sessionId) {
    Long userId = LoginHelper.getUserId();
    SseEmitter emitter = new SseEmitter(0L);
    documentAgentService.stream(sessionId, userId, emitter);
    return emitter;
  }

  @PostMapping("/stop/{sessionId}")
  public R<Void> stop(@PathVariable String sessionId) {
    Long userId = LoginHelper.getUserId();
    documentAgentService.stop(sessionId, userId);
    return R.ok();
  }

  @GetMapping("/sessions")
  public R<List<DocumentAgentSessionVo>> listSessions(@RequestParam(required = false) Integer limit) {
    Long userId = LoginHelper.getUserId();
    return R.ok(documentAgentService.listSessions(userId, limit));
  }

  @GetMapping("/sessions/{sessionId}")
  public R<DocumentAgentSessionDetailVo> detail(@PathVariable String sessionId) {
    Long userId = LoginHelper.getUserId();
    return R.ok(documentAgentService.getSessionDetail(sessionId, userId));
  }
}

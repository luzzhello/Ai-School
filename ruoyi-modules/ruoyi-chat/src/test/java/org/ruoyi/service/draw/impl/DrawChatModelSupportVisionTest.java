package org.ruoyi.service.draw.impl;

import org.junit.jupiter.api.Test;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawChatModelSupportVisionTest {

    @Test
    void resolveVisionApiModelName_prefersRemarkApiModel() {
        ChatModelVo vo = new ChatModelVo();
        vo.setModelName("Doubao-Seed-2.0-mini");
        vo.setRemark("api_model:doubao-seed-2-0-mini-260428");
        assertEquals("doubao-seed-2-0-mini-260428", DrawChatModelSupport.resolveVisionApiModelName(vo));
    }

    @Test
    void resolveVisionApiModelName_fallsBackToModelName() {
        ChatModelVo vo = new ChatModelVo();
        vo.setModelName("kimi-2.6");
        vo.setRemark("普通备注");
        assertEquals("kimi-2.6", DrawChatModelSupport.resolveVisionApiModelName(vo));
    }
}

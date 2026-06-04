package org.ruoyi.mapper.chat;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.vo.chat.ChatSessionVo;

/**
 * 会话管理Mapper接口
 *
 * @author ageerle
 * @date 2025-12-30
 */

@Mapper
public interface ChatSessionMapper extends BaseMapperPlus<ChatSession, ChatSessionVo> {

}

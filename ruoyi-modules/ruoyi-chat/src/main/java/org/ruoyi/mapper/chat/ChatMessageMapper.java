package org.ruoyi.mapper.chat;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.common.chat.domain.vo.chat.ChatMessageVo;
import org.ruoyi.common.chat.entity.chat.ChatMessage;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 聊天消息Mapper接口
 *
 * @author ageerle
 * @date 2025-12-14
 */

@Mapper
public interface ChatMessageMapper extends BaseMapperPlus<ChatMessage, ChatMessageVo> {

}

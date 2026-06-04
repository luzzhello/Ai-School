package org.ruoyi.mapper.chat;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.chat.ChatPrompt;
import org.ruoyi.domain.vo.chat.ChatPromptVo;

/**
 * AI提示词Mapper接口
 *
 * @author ruoyi
 */
@Mapper
public interface ChatPromptMapper extends BaseMapperPlus<ChatPrompt, ChatPromptVo> {

}

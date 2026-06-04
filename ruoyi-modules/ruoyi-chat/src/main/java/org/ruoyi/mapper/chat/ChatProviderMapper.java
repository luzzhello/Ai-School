package org.ruoyi.mapper.chat;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.domain.entity.chat.ChatProvider;
import org.ruoyi.domain.vo.chat.ChatProviderVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 厂商管理Mapper接口
 *
 * @author ageerle
 * @date 2025-12-14
 */

@Mapper
public interface ChatProviderMapper extends BaseMapperPlus<ChatProvider, ChatProviderVo> {

}

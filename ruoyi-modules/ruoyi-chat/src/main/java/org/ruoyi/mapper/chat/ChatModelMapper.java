package org.ruoyi.mapper.chat;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.common.chat.entity.chat.ChatModel;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 模型管理Mapper接口
 *
 * @author ageerle
 * @date 2025-12-14
 */

@Mapper
public interface ChatModelMapper extends BaseMapperPlus<ChatModel, ChatModelVo> {

}

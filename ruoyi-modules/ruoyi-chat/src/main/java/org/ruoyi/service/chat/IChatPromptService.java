package org.ruoyi.service.chat;

import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.chat.ChatPromptBo;
import org.ruoyi.domain.vo.chat.ChatPromptVo;

import java.util.Collection;
import java.util.List;

/**
 * AI提示词Service接口
 *
 * @author ruoyi
 */
public interface IChatPromptService {

    ChatPromptVo queryById(Long id);

    TableDataInfo<ChatPromptVo> queryPageList(ChatPromptBo bo, PageQuery pageQuery);

    List<ChatPromptVo> queryList(ChatPromptBo bo);

    Boolean insertByBo(ChatPromptBo bo);

    Boolean updateByBo(ChatPromptBo bo);

    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 根据提示词编码查询启用的提示词
     *
     * @param promptCode 提示词编码（业务类型）
     * @return 提示词信息
     */
    ChatPromptVo queryByCode(String promptCode);
}

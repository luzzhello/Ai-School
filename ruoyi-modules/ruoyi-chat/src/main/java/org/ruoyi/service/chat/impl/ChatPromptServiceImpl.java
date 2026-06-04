package org.ruoyi.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.chat.ChatPromptBo;
import org.ruoyi.domain.entity.chat.ChatPrompt;
import org.ruoyi.domain.vo.chat.ChatPromptVo;
import org.ruoyi.mapper.chat.ChatPromptMapper;
import org.ruoyi.service.chat.IChatPromptService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * AI提示词Service业务层处理
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ChatPromptServiceImpl implements IChatPromptService {

    private final ChatPromptMapper baseMapper;

    @Override
    public ChatPromptVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<ChatPromptVo> queryPageList(ChatPromptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ChatPrompt> lqw = buildQueryWrapper(bo);
        Page<ChatPromptVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    @Override
    public List<ChatPromptVo> queryList(ChatPromptBo bo) {
        LambdaQueryWrapper<ChatPrompt> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<ChatPrompt> buildQueryWrapper(ChatPromptBo bo) {
        LambdaQueryWrapper<ChatPrompt> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(ChatPrompt::getSortOrder);
        lqw.orderByDesc(ChatPrompt::getId);
        lqw.like(StringUtils.isNotBlank(bo.getPromptName()), ChatPrompt::getPromptName, bo.getPromptName());
        lqw.eq(StringUtils.isNotBlank(bo.getPromptCode()), ChatPrompt::getPromptCode, bo.getPromptCode());
        lqw.like(StringUtils.isNotBlank(bo.getPromptContent()), ChatPrompt::getPromptContent, bo.getPromptContent());
        lqw.eq(StringUtils.isNotBlank(bo.getCategory()), ChatPrompt::getCategory, bo.getCategory());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), ChatPrompt::getStatus, bo.getStatus());
        return lqw;
    }

    @Override
    public Boolean insertByBo(ChatPromptBo bo) {
        ChatPrompt add = MapstructUtils.convert(bo, ChatPrompt.class);
        validEntityBeforeSave(add);
        if (StringUtils.isBlank(add.getStatus())) {
            add.setStatus("0");
        }
        if (add.getSortOrder() == null) {
            add.setSortOrder(0L);
        }
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    public Boolean updateByBo(ChatPromptBo bo) {
        ChatPrompt update = MapstructUtils.convert(bo, ChatPrompt.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    private void validEntityBeforeSave(ChatPrompt entity) {
        // 编码唯一性校验
        LambdaQueryWrapper<ChatPrompt> lqw = Wrappers.lambdaQuery();
        lqw.eq(ChatPrompt::getPromptCode, entity.getPromptCode());
        if (entity.getId() != null) {
            lqw.ne(ChatPrompt::getId, entity.getId());
        }
        if (baseMapper.exists(lqw)) {
            throw new ServiceException("提示词编码已存在");
        }
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    public ChatPromptVo queryByCode(String promptCode) {
        if (StringUtils.isBlank(promptCode)) {
            return null;
        }
        LambdaQueryWrapper<ChatPrompt> lqw = Wrappers.lambdaQuery();
        lqw.eq(ChatPrompt::getPromptCode, promptCode);
        lqw.eq(ChatPrompt::getStatus, "0");
        lqw.orderByAsc(ChatPrompt::getSortOrder);
        lqw.last("LIMIT 1");
        return baseMapper.selectVoOne(lqw);
    }
}

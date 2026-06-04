package org.ruoyi.mapper.knowledge;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.domain.entity.knowledge.KnowledgeGraphInstance;
import org.ruoyi.domain.vo.knowledge.KnowledgeGraphInstanceVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识图谱实例Mapper接口
 *
 * @author ageerle
 * @date 2025-12-17
 */

@Mapper
public interface KnowledgeGraphInstanceMapper extends BaseMapperPlus<KnowledgeGraphInstance, KnowledgeGraphInstanceVo> {

}

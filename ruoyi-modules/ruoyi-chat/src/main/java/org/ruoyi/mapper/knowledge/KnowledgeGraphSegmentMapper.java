package org.ruoyi.mapper.knowledge;

import org.apache.ibatis.annotations.Mapper;
import org.ruoyi.domain.entity.knowledge.KnowledgeGraphSegment;
import org.ruoyi.domain.vo.knowledge.KnowledgeGraphSegmentVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 知识图谱片段Mapper接口
 *
 * @author ageerle
 * @date 2025-12-17
 */

@Mapper
public interface KnowledgeGraphSegmentMapper extends BaseMapperPlus<KnowledgeGraphSegment, KnowledgeGraphSegmentVo> {

}

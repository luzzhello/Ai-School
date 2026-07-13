package org.ruoyi.mapper.lit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.ruoyi.domain.entity.lit.LitPaperEntity;

import java.util.List;

@Mapper
public interface LitPaperMapper extends BaseMapperPlus<LitPaperEntity, LitPaperEntity> {

    /**
     * FULLTEXT 检索；若命中不足，调用方可用 {@link #searchLike} 兜底。
     */
    @Select("""
        SELECT * FROM lit_paper
        WHERE status = 'active'
          AND MATCH(title, keywords, abstract_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
          AND (#{fromYear} IS NULL OR year IS NULL OR year >= #{fromYear})
        ORDER BY cite_count DESC, year DESC
        LIMIT #{limit}
        """)
    List<LitPaperEntity> searchFulltext(@Param("keyword") String keyword,
                                        @Param("fromYear") Integer fromYear,
                                        @Param("limit") int limit);

    @Select("""
        SELECT * FROM lit_paper
        WHERE status = 'active'
          AND (title LIKE CONCAT('%', #{keyword}, '%')
            OR keywords LIKE CONCAT('%', #{keyword}, '%')
            OR abstract_text LIKE CONCAT('%', #{keyword}, '%'))
          AND (#{fromYear} IS NULL OR year IS NULL OR year >= #{fromYear})
        ORDER BY cite_count DESC, year DESC
        LIMIT #{limit}
        """)
    List<LitPaperEntity> searchLike(@Param("keyword") String keyword,
                                    @Param("fromYear") Integer fromYear,
                                    @Param("limit") int limit);
}

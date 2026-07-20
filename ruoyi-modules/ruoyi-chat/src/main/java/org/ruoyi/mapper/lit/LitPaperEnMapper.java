package org.ruoyi.mapper.lit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.domain.entity.lit.LitPaperEntity;

import java.util.List;

/**
 * 外文文献表 {@code lit_paper_en}；结果列与 {@link LitPaperEntity} 对齐。
 * <p>
 * 英文检索走原文 FULLTEXT；中文检索走知网中译字段 {@code title_zh/keywords_zh/abstract_zh}（ngram）。
 */
@Mapper
public interface LitPaperEnMapper {

    @Select("""
        SELECT * FROM lit_paper_en
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
        SELECT * FROM lit_paper_en
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

    @Select("""
        SELECT * FROM lit_paper_en
        WHERE status = 'active'
          AND MATCH(title_zh, keywords_zh, abstract_zh) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
          AND (#{fromYear} IS NULL OR year IS NULL OR year >= #{fromYear})
        ORDER BY cite_count DESC, year DESC
        LIMIT #{limit}
        """)
    List<LitPaperEntity> searchFulltextZh(@Param("keyword") String keyword,
                                          @Param("fromYear") Integer fromYear,
                                          @Param("limit") int limit);

    @Select("""
        SELECT * FROM lit_paper_en
        WHERE status = 'active'
          AND (title_zh LIKE CONCAT('%', #{keyword}, '%')
            OR keywords_zh LIKE CONCAT('%', #{keyword}, '%')
            OR abstract_zh LIKE CONCAT('%', #{keyword}, '%'))
          AND (#{fromYear} IS NULL OR year IS NULL OR year >= #{fromYear})
        ORDER BY cite_count DESC, year DESC
        LIMIT #{limit}
        """)
    List<LitPaperEntity> searchLikeZh(@Param("keyword") String keyword,
                                      @Param("fromYear") Integer fromYear,
                                      @Param("limit") int limit);
}

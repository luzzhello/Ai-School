package org.ruoyi.service.paper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ruoyi.config.LitPaperProperties;
import org.ruoyi.domain.entity.lit.LitPaperEntity;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.mapper.lit.LitPaperEnMapper;
import org.ruoyi.mapper.lit.LitPaperMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class LitPaperSearchServiceTest {

    @Mock
    private LitPaperMapper litPaperMapper;

    @Mock
    private LitPaperEnMapper litPaperEnMapper;

    private LitPaperProperties props;
    private LitPaperSearchService service;

    @BeforeEach
    void setUp() {
        props = new LitPaperProperties();
        props.setSearchPerKeyword(10);
        props.setSearchMaxTotal(50);
        props.getOndemand().setMinKeywords(3);
        props.getOndemand().setMaxKeywords(5);
        service = new LitPaperSearchService(litPaperMapper, litPaperEnMapper, props);
    }

    @Test
    void searchesEachTokenTenAndMergesCapFifty() {
        String title = "基于人工智能的知识图谱研究";
        List<String> tokens = TitleKeywordSplitter.split(title, 3, 5);
        assertTrue(tokens.size() >= 2, "expected title split into multiple tokens: " + tokens);

        AtomicInteger seq = new AtomicInteger(0);
        when(litPaperMapper.searchFulltext(anyString(), anyInt(), eq(10)))
            .thenAnswer(inv -> rows(seq.getAndIncrement() * 10, 10));

        List<Reference> result = service.search(title, "zh", 50);

        int expected = Math.min(50, tokens.size() * 10);
        assertEquals(expected, result.size());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(litPaperMapper, atLeast(tokens.size()))
            .searchFulltext(queryCaptor.capture(), anyInt(), eq(10));
        // 每个分词至少触发一次 limit=10 的库查询
        assertTrue(queryCaptor.getAllValues().size() >= tokens.size());
    }

    @Test
    void respectsCallerLimitBelowMaxTotal() {
        when(litPaperMapper.searchFulltext(anyString(), anyInt(), eq(10)))
            .thenReturn(rows(0, 10));

        List<Reference> result = service.search("深度学习", "zh", 5);
        assertEquals(5, result.size());
    }

    @Test
    void neverExceedsSearchMaxTotal() {
        props.setSearchMaxTotal(15);
        props.setSearchPerKeyword(10);
        service = new LitPaperSearchService(litPaperMapper, litPaperEnMapper, props);

        AtomicInteger seq = new AtomicInteger(0);
        when(litPaperMapper.searchFulltext(anyString(), anyInt(), eq(10)))
            .thenAnswer(inv -> rows(seq.getAndIncrement() * 10, 10));

        List<Reference> result = service.search("基于人工智能的知识图谱研究", "zh", 50);
        assertEquals(15, result.size());
    }

    private static List<LitPaperEntity> rows(int startId, int count) {
        List<LitPaperEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LitPaperEntity e = new LitPaperEntity();
            e.setId((long) (startId + i + 1));
            e.setTitle("t-" + (startId + i));
            e.setAuthors("a");
            e.setYear(2024);
            list.add(e);
        }
        return list;
    }
}

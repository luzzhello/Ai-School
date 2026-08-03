package org.ruoyi.service.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ruoyi.config.LitPaperProperties;
import org.ruoyi.domain.paper.PaperSession;
import org.ruoyi.domain.paper.Reference;
import org.ruoyi.mapper.lit.LitPaperEnMapper;
import org.ruoyi.mapper.lit.LitPaperMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class LitOnDemandServiceDbReadyTest {

    private static final String SESSION_ID = "sess-001";
    private static final Long USER_ID = 42L;
    private static final String TITLE = "基于人工智能的知识图谱研究";

    @Mock
    private PaperSessionStore paperSessionStore;

    @Mock
    private CnkiCrawlerProcessClient crawlerProcessClient;

    @Mock
    private LitPaperMapper litPaperMapper;

    @Mock
    private LitPaperEnMapper litPaperEnMapper;

    @Mock
    private LitPaperSearchService litPaperSearchService;

    private LitPaperProperties litPaperProperties;
    private LitOnDemandService service;

    @BeforeEach
    void setUp() {
        litPaperProperties = new LitPaperProperties();
        litPaperProperties.getOndemand().setDbReadyMinCount(50);
        litPaperProperties.getOndemand().setAutoSelectZh(18);
        litPaperProperties.getOndemand().setAutoSelectEn(2);

        service = new LitOnDemandService(
            litPaperProperties,
            paperSessionStore,
            crawlerProcessClient,
            litPaperMapper,
            litPaperEnMapper,
            litPaperSearchService,
            new ObjectMapper()
        );
    }

    @Test
    void autoSelectsWhenZhAndEnEachHaveMinCount() {
        List<Reference> zhRefs = buildRefs(50, "zh");
        List<Reference> enRefs = buildRefs(50, "en");
        when(litPaperSearchService.search(TITLE, "zh", 50)).thenReturn(zhRefs);
        when(litPaperSearchService.search(TITLE, "en", 50)).thenReturn(enRefs);

        PaperSession session = new PaperSession();
        session.setReferences(new ArrayList<>());
        when(paperSessionStore.require(SESSION_ID, USER_ID)).thenReturn(session);

        LitOnDemandTask task = task(TITLE);

        boolean handled = service.tryAutoSelectFromDb(task);

        assertTrue(handled);
        verifyNoInteractions(crawlerProcessClient);

        ArgumentCaptor<Consumer<PaperSession>> updateCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(paperSessionStore).update(eq(SESSION_ID), updateCaptor.capture());
        PaperSession updated = new PaperSession();
        updated.setReferences(new ArrayList<>());
        updateCaptor.getValue().accept(updated);
        assertEquals(20, updated.getReferences().size());
        long zhCount = updated.getReferences().stream().filter(r -> "zh".equals(r.getLanguage())).count();
        long enCount = updated.getReferences().stream().filter(r -> "en".equals(r.getLanguage())).count();
        assertEquals(18, zhCount);
        assertEquals(2, enCount);

        assertEquals("db", task.getSource());
        assertEquals(18, task.getFetchedCountZh());
        assertEquals(2, task.getFetchedCountEn());
        assertEquals(20, task.getFetchedCount());
        assertEquals(LitOnDemandTask.Status.DONE, task.getLitStatus());
    }

    @Test
    void crawlsWhenEitherLanguageBelowMinCount() {
        lenient().when(litPaperSearchService.search(TITLE, "zh", 50)).thenReturn(buildRefs(50, "zh"));
        lenient().when(litPaperSearchService.search(TITLE, "en", 50)).thenReturn(buildRefs(10, "en"));

        PaperSession session = new PaperSession();
        session.setReferences(new ArrayList<>());
        lenient().when(paperSessionStore.require(SESSION_ID, USER_ID)).thenReturn(session);

        LitOnDemandTask task = task(TITLE);

        boolean handled = service.tryAutoSelectFromDb(task);

        assertEquals(false, handled);
        verify(paperSessionStore, never()).update(anyString(), any());
        verifyNoInteractions(crawlerProcessClient);
    }

    @Test
    void doesNotOverwriteExistingReferences() {
        List<Reference> existing = buildRefs(3, "zh");
        when(litPaperSearchService.search(TITLE, "zh", 50)).thenReturn(buildRefs(50, "zh"));
        when(litPaperSearchService.search(TITLE, "en", 50)).thenReturn(buildRefs(50, "en"));

        PaperSession session = new PaperSession();
        session.setReferences(new ArrayList<>(existing));
        when(paperSessionStore.require(SESSION_ID, USER_ID)).thenReturn(session);

        LitOnDemandTask task = task(TITLE);

        boolean handled = service.tryAutoSelectFromDb(task);

        assertTrue(handled);
        assertEquals(3, session.getReferences().size());
        assertEquals(0, task.getSelectedCountZh());
        assertEquals(0, task.getSelectedCountEn());
        verify(paperSessionStore, never()).update(anyString(), any());
        verifyNoInteractions(crawlerProcessClient);
    }

    private LitOnDemandTask task(String title) {
        LitOnDemandTask task = new LitOnDemandTask();
        task.setTaskId("task-001");
        task.setSessionId(SESSION_ID);
        task.setTitle(title);
        task.setUserId(USER_ID);
        return task;
    }

    private static List<Reference> buildRefs(int count, String language) {
        List<Reference> refs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Reference ref = new Reference();
            ref.setTitle(language + "-title-" + i);
            ref.setLanguage(language);
            refs.add(ref);
        }
        return refs;
    }
}

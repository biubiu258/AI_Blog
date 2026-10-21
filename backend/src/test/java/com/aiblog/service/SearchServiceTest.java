package com.aiblog.service;

import com.aiblog.dto.ai.SearchItem;
import com.aiblog.entity.Article;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SearchService 纯逻辑单测：可见性裁决 + 双通道合并去重截断。
 * 不启动 Spring，不依赖 WebClient / MySQL。
 */
class SearchServiceTest {

    private static Article article(long id, long authorId, String status) {
        Article a = new Article();
        a.setId(id);
        a.setAuthorId(authorId);
        a.setStatus(status);
        a.setTitle("t" + id);
        return a;
    }

    private static SearchItem semantic(long id, double score, String status) {
        return SearchItem.semantic(id, "t" + id, "s" + id, score, status);
    }

    @Test
    void publishedVisibleToEveryone() {
        assertTrue(SearchService.isVisible(article(1, 9, "published"), 2L));
    }

    @Test
    void draftOnlyVisibleToAuthor() {
        assertTrue(SearchService.isVisible(article(1, 9, "draft"), 9L));
        assertFalse(SearchService.isVisible(article(1, 9, "draft"), 2L));
    }

    @Test
    void privateOnlyVisibleToAuthor() {
        assertTrue(SearchService.isVisible(article(1, 9, "private"), 9L));
        assertFalse(SearchService.isVisible(article(1, 9, "private"), 2L));
    }

    @Test
    void assembleSortsSemanticByScoreThenKeywordAndDedupes() {
        List<SearchItem> semantic = List.of(
                semantic(1, 0.5, "published"),
                semantic(2, 0.9, "published"));
        List<SearchItem> keyword = List.of(
                SearchItem.keyword(2L, "t2", "s2", "draft"),
                SearchItem.keyword(3L, "t3", "s3", "draft"));

        List<SearchItem> result = SearchService.assemble(semantic, keyword, 10);

        assertEquals(List.of(2L, 1L, 3L), result.stream().map(SearchItem::getId).toList());
        assertEquals("semantic", result.get(0).getMatchType());
        assertEquals("keyword", result.get(2).getMatchType());
    }

    @Test
    void assembleTruncatesToLimit() {
        List<SearchItem> semantic = List.of(
                semantic(1, 0.9, "published"),
                semantic(2, 0.8, "published"));
        List<SearchItem> keyword = List.of(SearchItem.keyword(3L, "t3", "s3", "draft"));

        List<SearchItem> result = SearchService.assemble(semantic, keyword, 2);

        assertEquals(2, result.size());
        assertEquals(List.of(1L, 2L), result.stream().map(SearchItem::getId).toList());
    }
}

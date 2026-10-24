package com.aiblog.controller;

import com.aiblog.entity.Article;
import com.aiblog.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索引擎收录：公开生成 sitemap.xml，仅包含已发布文章。
 */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private static final int MAX_URLS = 5000;

    private final ArticleMapper articleMapper;

    @Value("${app.site.base-url:http://localhost}")
    private String baseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        List<Article> articles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, "published")
                .select(Article::getId, Article::getUpdatedAt)
                .orderByDesc(Article::getUpdatedAt)
                .last("LIMIT " + MAX_URLS));

        StringBuilder xml = new StringBuilder(1024)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")
                .append("  <url><loc>").append(baseUrl).append("/</loc>")
                .append("<changefreq>daily</changefreq></url>\n");
        for (Article article : articles) {
            xml.append("  <url><loc>").append(baseUrl).append("/article/")
                    .append(article.getId()).append("</loc>");
            if (article.getUpdatedAt() != null) {
                xml.append("<lastmod>").append(article.getUpdatedAt().toLocalDate()).append("</lastmod>");
            }
            xml.append("</url>\n");
        }
        return xml.append("</urlset>").toString();
    }
}

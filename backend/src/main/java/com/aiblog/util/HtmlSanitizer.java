package com.aiblog.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 富文本 HTML 白名单清洗（jsoup Safelist），入库与渲染前的强制防线。
 * 基于 relaxed 白名单，并放行站内相对路径的图片与链接（/uploads/**）。
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Safelist safelist = Safelist.relaxed()
                // 允许相对路径图片（本地上传目录）与锚点
                .addProtocols("img", "src", "#")
                .addProtocols("a", "href", "#");
        String cleaned = Jsoup.clean(html, "", safelist, new Document.OutputSettings().prettyPrint(false));
        return cleaned == null ? "" : cleaned;
    }

    /**
     * 供列表摘要使用：去除所有标签取纯文本。
     */
    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }
}

package com.aiblog.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 生成请求（Java 转发给 Python 前做任务级校验，见契约 5.4.2）。
 */
@Data
public class AiGenerateRequest {

    /** outline / draft / continue / expand / condense / rewrite / polish / prose / suggest */
    @NotBlank(message = "task 不能为空")
    private String task;

    private Params params;

    private Context context;

    @Data
    public static class Params {
        /** <= 200 */
        private String topic;
        private String articleType;
        private String style;
        private String targetReader;
        /** 散文任务（prose）：用户描述的情绪，<= 200 */
        private String emotion;
        /** 散文任务（prose）：用户近况描述，<= 1000 */
        private String recentState;
        /** 划词建议（suggest）：advice / polish / rewrite / expand / condense */
        private String suggestionMode;
    }

    @Data
    public static class Context {
        /** <= 50000，生成正文时携带 */
        private String outline;
        /** <= 100000，续写/扩写/缩写/改写/润色时携带 */
        private String originalText;
        /** <= 2000，用户附加自由指令 */
        private String instruction;
        /** 划词建议（suggest）：选中片段，<= 2000 */
        private String selectedText;
        /** 划词建议（suggest）：选区前文，<= 300 */
        private String textBefore;
        /** 划词建议（suggest）：选区后文，<= 300 */
        private String textAfter;
        /** 散文任务（prose）：1-5 张图片，站内 /uploads/** 相对路径或同源绝对地址 */
        private List<String> imageUrls;
    }

    public Map<String, Object> paramsAsMap() {
        if (params == null) {
            return Map.of();
        }
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        if (params.getTopic() != null) m.put("topic", params.getTopic());
        if (params.getArticleType() != null) m.put("article_type", params.getArticleType());
        if (params.getStyle() != null) m.put("style", params.getStyle());
        if (params.getTargetReader() != null) m.put("target_reader", params.getTargetReader());
        if (params.getEmotion() != null) m.put("emotion", params.getEmotion());
        if (params.getRecentState() != null) m.put("recent_state", params.getRecentState());
        if (params.getSuggestionMode() != null) m.put("suggestion_mode", params.getSuggestionMode());
        return m;
    }

    public Map<String, Object> contextAsMap() {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("outline", context == null ? "" : context.getOutline());
        m.put("original_text", context == null ? "" : context.getOriginalText());
        m.put("instruction", context == null ? "" : context.getInstruction());
        m.put("selected_text", context == null ? "" : context.getSelectedText());
        m.put("text_before", context == null ? "" : context.getTextBefore());
        m.put("text_after", context == null ? "" : context.getTextAfter());
        m.put("image_urls", context == null || context.getImageUrls() == null
                ? List.of() : context.getImageUrls());
        return m;
    }
}

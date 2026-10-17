package com.aiblog.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 增量 SSE 解析器：网络分块可能把事件切成任意片段，
 * 这里按空行（\n\n 或 \r\n\r\n）切分事件块，解析 event:/data: 行。
 * 忽略以 ':' 开头的保活注释行；多行 data 以 \n 合并。
 */
public class SseParser {

    /**
     * 一条解析完成的 SSE 事件。
     */
    public record SseEvent(String event, String data) {
    }

    private final StringBuilder pending = new StringBuilder();

    public List<SseEvent> feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        pending.append(chunk);
        List<SseEvent> events = new ArrayList<>();
        while (true) {
            int end = indexOfEventEnd(pending);
            if (end < 0) {
                break;
            }
            boolean crlf = pending.indexOf("\r\n\r\n", end) == end;
            String block = pending.substring(0, end);
            pending.delete(0, end + (crlf ? 4 : 2));
            SseEvent event = parseBlock(block);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * 流结束时调用：把最后一段没有以空行结尾的内容作为最终事件解析。
     */
    public List<SseEvent> flush() {
        List<SseEvent> events = new ArrayList<>();
        if (!pending.isEmpty()) {
            SseEvent event = parseBlock(pending.toString());
            if (event != null) {
                events.add(event);
            }
            pending.setLength(0);
        }
        return events;
    }

    private int indexOfEventEnd(CharSequence s) {
        int a = indexOf(s, "\n\n");
        int b = indexOf(s, "\r\n\r\n");
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private int indexOf(CharSequence s, String target) {
        // StringBuilder.indexOf 即可，无需额外实现
        return s.toString().indexOf(target);
    }

    private SseEvent parseBlock(String block) {
        String event = null;
        StringBuilder data = null;
        for (String line : block.split("\r?\n|\n")) {
            if (line.isEmpty() || line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("event:")) {
                event = stripColonValue(line.substring("event:".length()));
            } else if (line.startsWith("data:")) {
                String value = stripColonValue(line.substring("data:".length()));
                data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
            }
        }
        if (event == null && data == null) {
            return null;
        }
        return new SseEvent(event == null ? "message" : event,
                data == null ? "" : data.toString());
    }

    private String stripColonValue(String value) {
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }
        return value.stripTrailing();
    }
}

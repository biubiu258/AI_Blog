package com.aiblog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务网关配置（契约见 docs/05-接口契约.md 5.3 / 5.7.3）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String baseUrl = "http://localhost:8000";

    /** 共享密钥，出站请求头 X-Internal-Token */
    private String internalToken = "dev-internal-token";

    /**
     * Java 对外可访问地址。散文任务转发前用它把 /uploads/** 相对路径
     * 拼成 Python 能直接下载的绝对 URL（不依赖 Python 侧知道 Java 的地址）。
     */
    private String publicBaseUrl = "http://localhost:8080";

    /** P1 向量同步开关 */
    private boolean vectorSyncEnabled = false;

    /** Java -> Python 连接超时（契约 5.7.3：3s） */
    private int connectTimeoutMs = 3000;

    /** Java -> Python 读超时（契约 5.7.3：120s） */
    private int readTimeoutMs = 120000;

    /**
     * 建立 SSE 流前等待连接的最长时间（> 连接超时）。
     * Python 现在先回响应头再做图片转换与模型调用，正常应秒级返回；
     * 15s 用于兜底慢磁盘/慢网络场景。
     */
    private int preStreamWaitSeconds = 15;

    /** P1 语义搜索读超时（独立于生成的 120s，避免用户久等） */
    private int searchReadTimeoutMs = 10000;

    /** 全文审阅读超时：非流式返回结构化建议，独立于生成的 120s */
    private int reviewReadTimeoutMs = 60000;

    /** P1 语义搜索结果条数上限 */
    private int searchMaxTopK = 20;

    /** 演示开关：填入 JSON 数组时跳过 Python 直接返回 mock hits，默认空=关闭 */
    private String searchMockHits = "";

    /** SseEmitter 超时（契约 5.7.3：120s） */
    private long sseTimeoutMs = 120000;
}

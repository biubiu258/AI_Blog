package com.aiblog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 人文关怀检测配置（契约 5.8）：发布时情绪/危机检测，fail-open 兜底。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.care")
public class CareProperties {

    /** 检测调用超时（ms），超时按无风险放行 */
    private int timeoutMs = 5000;

    /** 演示开关：注入 none/low/mid/high 时跳过 Python 检测直接返回该级别，默认空=关闭 */
    private String mockLevel = "";
}

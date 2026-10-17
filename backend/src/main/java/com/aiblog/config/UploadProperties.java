package com.aiblog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传配置：落盘目录、大小与格式白名单。
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private String dir = "./uploads";
    private long maxSizeBytes = 5 * 1024 * 1024;
    /** 逗号分隔的允许扩展名 */
    private String allowedExtensions = "jpg,jpeg,png,gif,webp";
}

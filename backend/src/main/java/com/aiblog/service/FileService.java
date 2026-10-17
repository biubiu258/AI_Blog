package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.UploadProperties;
import com.aiblog.entity.UploadFile;
import com.aiblog.mapper.UploadFileMapper;
import com.aiblog.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 本地图片上传：落盘 {upload.dir}/yyyy/MM/uuid.ext，返回 /uploads/** 访问地址。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final UploadProperties properties;
    private final UploadFileMapper uploadFileMapper;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要上传的图片");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片格式不支持或超过大小限制");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = extension(original);
        Set<String> allowed = allowedExtensions();
        if (!allowed.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片格式不支持或超过大小限制");
        }
        String relativeDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String relativePath = relativeDir + "/" + filename;

        Path root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relativeDir).normalize();
        // 防目录穿越：落盘路径必须位于上传根目录之内
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法的文件路径");
        }
        try {
            Files.createDirectories(target);
            file.transferTo(target.resolve(filename).toFile());
        } catch (IOException e) {
            log.error("文件落盘失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        String url = "/uploads/" + relativePath;
        UploadFile record = new UploadFile();
        record.setOwnerId(CurrentUser.id());
        record.setFilePath(relativePath);
        record.setUrl(url);
        record.setSize(file.getSize());
        uploadFileMapper.insert(record);
        log.info("图片上传: url={}, size={}", url, file.getSize());
        return url;
    }

    private Set<String> allowedExtensions() {
        return java.util.Arrays.stream(properties.getAllowedExtensions().split(","))
                .map(String::strip).map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

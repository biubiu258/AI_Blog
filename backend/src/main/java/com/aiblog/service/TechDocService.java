package com.aiblog.service;

import com.aiblog.ai.PythonResponseException;
import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.config.UploadProperties;
import com.aiblog.dto.tech.TechChunkItem;
import com.aiblog.dto.tech.TechDocRequest;
import com.aiblog.dto.tech.TechParseResponse;
import com.aiblog.entity.AiOutput;
import com.aiblog.entity.TechDoc;
import com.aiblog.entity.TechDocChunk;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.TechDocChunkMapper;
import com.aiblog.mapper.TechDocMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 技术语料库：上传/粘贴入库、解析切块、向量索引、删除与统计。
 * 解析优先调用 Python；Markdown/纯文本在 Python 不可用时本地切块兜底，PDF 不兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechDocService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PARSING = "parsing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "txt", "pdf");
    private static final int LOCAL_CHUNK_SIZE = 480;
    private static final int LOCAL_OVERLAP = 80;
    private static final int MAX_CHUNKS = 200;
    private static final int MAX_ERROR_MESSAGE = 500;

    private final TechDocMapper docMapper;
    private final TechDocChunkMapper chunkMapper;
    private final AiOutputMapper aiOutputMapper;
    private final WebClient aiWebClient;
    private final AiProperties aiProperties;
    private final UploadProperties uploadProperties;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ 查询

    public Map<String, Object> page(int page, int size, String status, String keyword) {
        Page<TechDoc> result = docMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                Wrappers.<TechDoc>lambdaQuery()
                        .eq(StringUtils.hasText(status), TechDoc::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(TechDoc::getTitle, keyword)
                                .or().like(TechDoc::getSourceName, keyword))
                        .orderByDesc(TechDoc::getId));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", result.getRecords());
        return resp;
    }

    public TechDoc detail(Long id) {
        TechDoc doc = docMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技术语料不存在");
        }
        return doc;
    }

    public List<TechDocChunk> chunks(Long id) {
        detail(id);
        return chunkMapper.selectList(Wrappers.<TechDocChunk>lambdaQuery()
                .eq(TechDocChunk::getDocId, id)
                .orderByAsc(TechDocChunk::getChunkIndex));
    }

    // ------------------------------------------------------------------ 写入

    /** 粘贴入库：正文包成内存文件交给 Python 解析。 */
    public TechDoc createFromText(TechDocRequest request, Long userId) {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "正文不能为空");
        }
        String fileType = normalizeTextFileType(request.getFileType());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(bytes);
        assertChecksumAvailable(checksum, null);

        TechDoc doc = new TechDoc();
        doc.setTitle(request.getTitle().trim());
        doc.setDocType(defaultDocType(request.getDocType()));
        doc.setSourceName(trimOrNull(request.getSourceName()));
        doc.setSourceUrl(trimOrNull(request.getSourceUrl()));
        doc.setVersion(trimOrNull(request.getVersion()));
        doc.setLicense(trimOrNull(request.getLicense()));
        doc.setFileType(fileType);
        doc.setPageCount(0);
        doc.setChunkCount(0);
        doc.setStatus(STATUS_PARSING);
        doc.setChecksum(checksum);
        doc.setCreatedBy(userId);
        try {
            docMapper.insert(doc);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "相同内容的语料已存在");
        }

        parseAndPersist(doc, bytes, fileType, content, "pasted." + extensionOf(fileType));
        return doc;
    }

    /** 上传入库：仅 .md/.txt/.pdf，30MB 上限，SHA-256 判重。 */
    public TechDoc upload(MultipartFile file, String title, String docType, String sourceName,
                          String version, String license, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件超过 30MB");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 .md / .txt / .pdf");
        }
        String fileType = switch (ext) {
            case "md" -> "markdown";
            case "pdf" -> "pdf";
            default -> "text";
        };

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件读取失败");
        }
        String checksum = sha256(bytes);
        assertChecksumAvailable(checksum, null);
        String relativePath = storeFile(bytes, ext);
        String resolvedTitle = StringUtils.hasText(title)
                ? title.trim() : stripExtension(original);
        if (!StringUtils.hasText(resolvedTitle)) {
            resolvedTitle = "未命名文档";
        }

        TechDoc doc = new TechDoc();
        doc.setTitle(resolvedTitle.length() > 200 ? resolvedTitle.substring(0, 200) : resolvedTitle);
        doc.setDocType(defaultDocType(docType));
        doc.setSourceName(trimOrNull(sourceName));
        doc.setVersion(trimOrNull(version));
        doc.setLicense(trimOrNull(license));
        doc.setFilePath(relativePath);
        doc.setFileType(fileType);
        doc.setPageCount(0);
        doc.setChunkCount(0);
        doc.setStatus(STATUS_PARSING);
        doc.setChecksum(checksum);
        doc.setCreatedBy(userId);
        try {
            docMapper.insert(doc);
        } catch (DuplicateKeyException e) {
            deleteQuietly(relativePath);
            throw new BusinessException(ErrorCode.CONFLICT, "相同内容的语料已存在");
        }

        parseAndPersist(doc, bytes, fileType,
                "pdf".equals(fileType) ? null : decodeText(bytes), original);
        return doc;
    }

    /** 修改元数据并重新解析正文。 */
    public TechDoc update(Long id, TechDocRequest request) {
        TechDoc doc = detail(id);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "正文不能为空");
        }
        String fileType = normalizeTextFileType(request.getFileType());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(bytes);
        assertChecksumAvailable(checksum, id);

        doc.setTitle(request.getTitle().trim());
        doc.setDocType(defaultDocType(request.getDocType()));
        doc.setSourceName(trimOrNull(request.getSourceName()));
        doc.setSourceUrl(trimOrNull(request.getSourceUrl()));
        doc.setVersion(trimOrNull(request.getVersion()));
        doc.setLicense(trimOrNull(request.getLicense()));
        doc.setFileType(fileType);
        doc.setChecksum(checksum);
        doc.setStatus(STATUS_PARSING);
        docMapper.updateById(doc);

        parseAndPersist(doc, bytes, fileType, content, "pasted." + extensionOf(fileType));
        return doc;
    }

    @Transactional
    public void delete(Long id) {
        TechDoc doc = detail(id);
        chunkMapper.deleteByDocId(id);
        docMapper.deleteById(id);
        if (StringUtils.hasText(doc.getFilePath())) {
            deleteQuietly(doc.getFilePath());
        }
        deleteVectorQuietly(id);
    }

    /** 向量删除尽力而为：失败只记日志，不影响文档删除。 */
    private void deleteVectorQuietly(Long id) {
        if (!aiProperties.isTechVectorEnabled()) {
            return;
        }
        try {
            aiWebClient.delete().uri("/v1/tech/vectors/{docId}", id)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofMillis(aiProperties.getTechParseTimeoutMs()));
        } catch (Exception e) {
            log.warn("技术语料向量删除失败 docId={}: {}", id, e.getMessage());
        }
    }

    /** 重建向量索引：开关关闭时直接返回原因，失败只记录不改变 ready 状态。 */
    public Map<String, Object> index(Long id) {
        TechDoc doc = detail(id);
        if (!aiProperties.isTechVectorEnabled()) {
            return Map.of("indexed", false,
                    "message", "向量索引未开启（AI_TECH_VECTOR_ENABLED=false），检索将使用全文兜底");
        }
        if (!STATUS_READY.equals(doc.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅解析完成的文档可重建索引");
        }
        List<TechDocChunk> chunks = chunks(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("doc_id", doc.getId());
        payload.put("title", doc.getTitle());
        payload.put("source_name", doc.getSourceName() == null ? "" : doc.getSourceName());
        payload.put("version", doc.getVersion() == null ? "" : doc.getVersion());
        payload.put("chunks", chunks.stream().map(chunk -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chunk_index", chunk.getChunkIndex());
            item.put("page_no", chunk.getPageNo());
            item.put("section_path", chunk.getSectionPath());
            item.put("content", chunk.getContent());
            return item;
        }).toList());
        try {
            aiWebClient.post().uri("/v1/tech/vectors/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofMillis(aiProperties.getTechParseTimeoutMs()));
            setErrorMessage(id, null);
            return Map.of("indexed", true, "chunks", chunks.size());
        } catch (Exception e) {
            String message = "向量索引失败：" + errorMessageOf(e);
            log.warn("技术语料向量索引失败 docId={}: {}", id, message);
            setErrorMessage(id, message);
            return Map.of("indexed", false, "message", message);
        }
    }

    /** 后台质量卡片：文档数、状态分布、块数、近 7 天建议调用与采纳。 */
    public Map<String, Object> stats() {
        long total = docMapper.selectCount(null);
        long ready = docMapper.selectCount(Wrappers.<TechDoc>lambdaQuery()
                .eq(TechDoc::getStatus, STATUS_READY));
        long failed = docMapper.selectCount(Wrappers.<TechDoc>lambdaQuery()
                .eq(TechDoc::getStatus, STATUS_FAILED));
        long chunks = chunkMapper.selectCount(null);
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        long assistCalls = aiOutputMapper.selectCount(Wrappers.<AiOutput>lambdaQuery()
                .eq(AiOutput::getAction, "tech_assist")
                .ge(AiOutput::getCreatedAt, since));
        long adopted = aiOutputMapper.selectCount(Wrappers.<AiOutput>lambdaQuery()
                .eq(AiOutput::getAction, "tech_assist")
                .eq(AiOutput::getAdopted, 1)
                .ge(AiOutput::getCreatedAt, since));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("docs", total);
        stats.put("ready", ready);
        stats.put("failed", failed);
        stats.put("chunks", chunks);
        stats.put("assistCalls7d", assistCalls);
        stats.put("adopted7d", adopted);
        stats.put("failureRate", total == 0 ? 0.0
                : Math.round(failed * 10000.0 / total) / 100.0);
        return stats;
    }

    // ------------------------------------------------------------------ 解析

    private void parseAndPersist(TechDoc doc, byte[] bytes, String fileType,
                                 String fallbackText, String filename) {
        try {
            TechParseResponse parsed = callPythonParse(bytes, fileType, doc.getTitle(), filename);
            persistParsed(doc, parsed.getPageCount() == null ? 0 : parsed.getPageCount(),
                    parsed.getChunks());
        } catch (TechParseException e) {
            if (fallbackText != null) {
                List<ChunkDraft> drafts = localChunks(fallbackText);
                if (!drafts.isEmpty()) {
                    log.warn("Python 解析不可用，已本地切块兜底 docId={} code={}: {}",
                            doc.getId(), e.code, e.getMessage());
                    persistDrafts(doc, drafts, 0);
                    return;
                }
            }
            log.warn("技术语料解析失败 docId={} code={}: {}", doc.getId(), e.code, e.getMessage());
            markFailed(doc, e.getMessage());
        }
    }

    private TechParseResponse callPythonParse(byte[] bytes, String fileType,
                                              String title, String filename) {
        org.springframework.http.client.MultipartBodyBuilder builder =
                new org.springframework.http.client.MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        builder.part("file_type", fileType);
        if (StringUtils.hasText(title)) {
            builder.part("title", title);
        }
        try {
            TechParseResponse response = aiWebClient.post()
                    .uri("/v1/tech/parse")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(
                                            new PythonResponseException(resp.statusCode(), body))))
                    .bodyToMono(TechParseResponse.class)
                    .block(Duration.ofMillis(aiProperties.getTechParseTimeoutMs()));
            if (response == null || response.getChunks() == null || response.getChunks().isEmpty()) {
                throw new TechParseException("INVALID_PARAMS", "文档没有可索引的内容");
            }
            return response;
        } catch (PythonResponseException e) {
            throw new TechParseException(errorCodeOf(e), errorMessageOf(e));
        } catch (IllegalStateException e) {
            throw new TechParseException("PARSE_TIMEOUT", "文档解析超时，请稍后重试");
        } catch (TechParseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用 Python 解析失败: {}", e.getMessage());
            throw new TechParseException("AI_SERVICE_ERROR", "解析服务暂时不可用");
        }
    }

    private void persistParsed(TechDoc doc, int pageCount, List<TechChunkItem> items) {
        List<ChunkDraft> drafts = new ArrayList<>(items.size());
        int index = 0;
        for (TechChunkItem item : items) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            drafts.add(new ChunkDraft(
                    item.getChunkIndex() == null ? index : item.getChunkIndex(),
                    item.getPageNo(), item.getSectionPath(), item.getContent().trim(),
                    item.getTokenCount() == null ? estimateTokens(item.getContent()) : item.getTokenCount(),
                    sha256(item.getContent().getBytes(StandardCharsets.UTF_8))));
            index++;
        }
        persistDrafts(doc, drafts, pageCount);
    }

    private void persistDrafts(TechDoc doc, List<ChunkDraft> drafts, int pageCount) {
        if (drafts.isEmpty()) {
            markFailed(doc, "文档没有可索引的内容");
            return;
        }
        chunkMapper.deleteByDocId(doc.getId());
        for (ChunkDraft draft : drafts) {
            TechDocChunk chunk = new TechDocChunk();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(draft.chunkIndex());
            chunk.setPageNo(draft.pageNo());
            chunk.setSectionPath(draft.sectionPath());
            chunk.setContent(draft.content());
            chunk.setTokenCount(draft.tokenCount());
            chunk.setChunkHash(draft.chunkHash());
            chunkMapper.insert(chunk);
        }
        int count = drafts.size();
        updateState(doc.getId(), STATUS_READY, pageCount, count, null);
        doc.setStatus(STATUS_READY);
        doc.setPageCount(pageCount);
        doc.setChunkCount(count);
        doc.setErrorMessage(null);
        if (aiProperties.isTechVectorEnabled()) {
            index(doc.getId());
        }
    }

    private void markFailed(TechDoc doc, String message) {
        String error = truncate(message, MAX_ERROR_MESSAGE);
        chunkMapper.deleteByDocId(doc.getId());
        updateState(doc.getId(), STATUS_FAILED, 0, 0, error);
        doc.setStatus(STATUS_FAILED);
        doc.setPageCount(0);
        doc.setChunkCount(0);
        doc.setErrorMessage(error);
    }

    // ------------------------------------------------------------------ 本地切块

    /** 段落 480 字 / 80 重叠；仅用于 Markdown 与纯文本兜底。 */
    private List<ChunkDraft> localChunks(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<ChunkDraft> drafts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : normalized.split("\n{2,}")) {
            String cleaned = paragraph.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(cleaned);
            while (buffer.length() > LOCAL_CHUNK_SIZE) {
                int cut = cutPoint(buffer);
                addDraft(drafts, buffer.substring(0, cut));
                buffer = new StringBuilder(
                        buffer.substring(Math.max(0, cut - LOCAL_OVERLAP)).stripLeading());
            }
        }
        addDraft(drafts, buffer.toString());
        return drafts.size() > MAX_CHUNKS ? drafts.subList(0, MAX_CHUNKS) : drafts;
    }

    private void addDraft(List<ChunkDraft> drafts, String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty() || drafts.size() >= MAX_CHUNKS) {
            return;
        }
        drafts.add(new ChunkDraft(
                drafts.size(), null, null, cleaned, estimateTokens(cleaned),
                sha256(cleaned.getBytes(StandardCharsets.UTF_8))));
    }

    private int cutPoint(StringBuilder buffer) {
        int limit = LOCAL_CHUNK_SIZE;
        int cut = -1;
        for (String mark : new String[]{"。", "！", "？", "；", "\n"}) {
            cut = Math.max(cut, buffer.substring(0, limit).lastIndexOf(mark));
        }
        return cut < limit / 2 ? limit : cut + 1;
    }

    // ------------------------------------------------------------------ 工具

    private String normalizeTextFileType(String fileType) {
        String value = fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "markdown", "md" -> "markdown";
            case "text", "txt" -> "text";
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "粘贴入库仅支持 Markdown 或纯文本");
        };
    }

    private String defaultDocType(String docType) {
        return StringUtils.hasText(docType) ? docType.trim() : "article";
    }

    private void assertChecksumAvailable(String checksum, Long selfId) {
        TechDoc existing = docMapper.selectOne(Wrappers.<TechDoc>lambdaQuery()
                .eq(TechDoc::getChecksum, checksum)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(selfId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "相同内容的语料已存在：" + existing.getTitle());
        }
    }

    private void updateState(Long id, String status, int pageCount, int chunkCount, String error) {
        docMapper.update(null, Wrappers.<TechDoc>lambdaUpdate()
                .eq(TechDoc::getId, id)
                .set(TechDoc::getStatus, status)
                .set(TechDoc::getPageCount, pageCount)
                .set(TechDoc::getChunkCount, chunkCount)
                .set(TechDoc::getErrorMessage, error));
    }

    private void setErrorMessage(Long id, String message) {
        docMapper.update(null, Wrappers.<TechDoc>lambdaUpdate()
                .eq(TechDoc::getId, id)
                .set(TechDoc::getErrorMessage, truncate(message, MAX_ERROR_MESSAGE)));
    }

    private String storeFile(byte[] bytes, String ext) {
        String relativePath = "tech/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path root = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法的文件路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("技术语料落盘失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        return relativePath;
    }

    private void deleteQuietly(String relativePath) {
        try {
            Path root = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
            Path target = root.resolve(relativePath).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (Exception e) {
            log.warn("技术语料文件删除失败 path={}: {}", relativePath, e.getMessage());
        }
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    /** 上传的 Markdown/纯文本兜底解码：优先 UTF-8，失败回退 GB18030。 */
    private static String decodeText(byte[] bytes) {
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return new String(bytes, java.nio.charset.Charset.forName("GB18030"));
        }
    }

    private static String extensionOf(String fileType) {
        return "pdf".equals(fileType) ? "pdf" : "markdown".equals(fileType) ? "md" : "txt";
    }

    private static String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static int estimateTokens(String text) {
        int cjk = 0;
        int words = 0;
        boolean inWord = false;
        for (char ch : text.toCharArray()) {
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(ch)) {
                if (!inWord) {
                    words++;
                }
                inWord = true;
            } else {
                inWord = false;
            }
        }
        return Math.max(1, cjk + words);
    }

    private String errorMessageOf(Exception e) {
        if (e instanceof PythonResponseException pre) {
            try {
                JsonNode root = objectMapper.readTree(pre.getBody() == null ? "" : pre.getBody());
                JsonNode error = root.path("error");
                String message = error.path("message").asText(null);
                if (StringUtils.hasText(message)) {
                    return message;
                }
            } catch (Exception ignored) {
                // 非 JSON 错误体：走通用映射
            }
            return "解析服务返回错误";
        }
        if (e instanceof IllegalStateException) {
            return "文档解析超时，请稍后重试";
        }
        return "解析服务暂时不可用";
    }

    private String errorCodeOf(PythonResponseException e) {
        try {
            JsonNode root = objectMapper.readTree(e.getBody() == null ? "" : e.getBody());
            return root.path("error").path("code").asText("AI_SERVICE_ERROR");
        } catch (Exception ignored) {
            return "AI_SERVICE_ERROR";
        }
    }

    private record ChunkDraft(int chunkIndex, Integer pageNo, String sectionPath,
                              String content, int tokenCount, String chunkHash) {
    }

    private static final class TechParseException extends RuntimeException {

        private final String code;

        private TechParseException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}

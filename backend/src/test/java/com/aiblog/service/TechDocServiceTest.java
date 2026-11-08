package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.config.UploadProperties;
import com.aiblog.dto.tech.TechDocRequest;
import com.aiblog.entity.TechDoc;
import com.aiblog.mapper.AiOutputMapper;
import com.aiblog.mapper.TechDocChunkMapper;
import com.aiblog.mapper.TechDocMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技术语料服务单测：用本地 HttpServer 模拟 Python 解析接口，
 * 覆盖成功落块、PDF 失败原因、文本兜底与 checksum 冲突。
 */
class TechDocServiceTest {

    private final TechDocMapper docMapper = mock(TechDocMapper.class);
    private final TechDocChunkMapper chunkMapper = mock(TechDocChunkMapper.class);
    private final AiOutputMapper aiOutputMapper = mock(AiOutputMapper.class);
    private final AiProperties aiProperties = new AiProperties();
    private final UploadProperties uploadProperties = new UploadProperties();

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 的列解析依赖 MyBatis-Plus 表元数据缓存
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TechDoc.class);
        TableInfoHelper.initTableInfo(assistant, com.aiblog.entity.TechDocChunk.class);
    }

    private TechDocService service(String baseUrl) {
        doAnswer(invocation -> {
            TechDoc doc = invocation.getArgument(0);
            doc.setId(1L);
            return 1;
        }).when(docMapper).insert(any(TechDoc.class));
        return new TechDocService(docMapper, chunkMapper, aiOutputMapper,
                WebClient.builder().baseUrl(baseUrl).build(), aiProperties,
                uploadProperties, new ObjectMapper());
    }

    private static HttpServer server(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tech/parse", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, resp.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(resp);
            }
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static TechDocRequest textRequest() {
        TechDocRequest request = new TechDocRequest();
        request.setTitle("令牌使用说明");
        request.setFileType("markdown");
        request.setContent("# 概述\n\n令牌用于鉴权，过期后需要刷新。\n\n## 刷新\n\n调用刷新接口。");
        return request;
    }

    @Test
    void parseSuccessPersistsChunksAndMarksReady() throws IOException {
        String body = """
                {"title":"令牌使用说明","file_type":"markdown","page_count":0,
                 "chunks":[{"chunk_index":0,"page_no":null,"section_path":"概述",
                            "content":"令牌用于鉴权","token_count":6},
                           {"chunk_index":1,"page_no":null,"section_path":"概述 > 刷新",
                            "content":"调用刷新接口","token_count":6}]}
                """;
        HttpServer server = server(200, body);
        try {
            TechDoc doc = service(baseUrl(server)).createFromText(textRequest(), 7L);
            assertEquals(TechDocService.STATUS_READY, doc.getStatus());
            assertEquals(2, doc.getChunkCount());
            verify(chunkMapper).deleteByDocId(1L);
            verify(chunkMapper, org.mockito.Mockito.times(2))
                    .insert(any(com.aiblog.entity.TechDocChunk.class));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void pdfFailureKeepsReasonAndNoChunks() throws IOException {
        String body = """
                {"error":{"code":"PDF_NO_TEXT","message":"PDF 无文本层，疑似扫描件，请先 OCR 或上传可复制文本的 PDF"}}
                """;
        HttpServer server = server(422, body);
        try {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "scan.pdf", "application/pdf", "fake".getBytes(StandardCharsets.UTF_8));
            TechDoc doc = service(baseUrl(server)).upload(file, null, null, "手册", "v1", null, 7L);
            assertEquals(TechDocService.STATUS_FAILED, doc.getStatus());
            assertTrue(doc.getErrorMessage().contains("扫描件"));
            verify(chunkMapper).deleteByDocId(1L);
            verify(chunkMapper, never()).insert(any(com.aiblog.entity.TechDocChunk.class));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void textParseFailureFallsBackToLocalChunks() throws IOException {
        HttpServer server = server(500, "{\"error\":{\"code\":\"INTERNAL_ERROR\"}}");
        try {
            TechDoc doc = service(baseUrl(server)).createFromText(textRequest(), 7L);
            assertEquals(TechDocService.STATUS_READY, doc.getStatus());
            assertTrue(doc.getChunkCount() > 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void duplicateChecksumIsRejectedBeforeParsing() {
        TechDoc existing = new TechDoc();
        existing.setId(2L);
        existing.setTitle("已有语料");
        when(docMapper.selectOne(any())).thenReturn(existing);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service("http://127.0.0.1:1").createFromText(textRequest(), 7L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void uploadRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "spec.docx", "application/octet-stream", "text".getBytes(StandardCharsets.UTF_8));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service("http://127.0.0.1:1").upload(file, null, null, null, null, null, 7L));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    void deleteRemovesChunksAndFile() throws IOException {
        java.nio.file.Path dir = Files.createTempDirectory("tech-doc-test");
        uploadProperties.setDir(dir.toString());
        TechDoc doc = new TechDoc();
        doc.setId(1L);
        doc.setFilePath("tech/missing.pdf");
        when(docMapper.selectById(1L)).thenReturn(doc);
        service("http://127.0.0.1:1").delete(1L);
        verify(chunkMapper).deleteByDocId(1L);
        verify(docMapper).deleteById(1L);
    }
}

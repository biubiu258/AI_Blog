package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.config.AiProperties;
import com.aiblog.dto.tech.TechChunkHit;
import com.aiblog.dto.tech.TechWordingRequest;
import com.aiblog.mapper.TechDocChunkMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技术措辞建议单测：RRF 融合、单侧失败降级、证据校验与限流。
 * Python 侧用本地 HttpServer 模拟，向量检索默认关闭走全文兜底。
 */
class TechAssistServiceTest {

    private static final String TEXT = "把数据写入磁盘前，先校验 checksum。如果校验失败就重试一次。";

    private final TechTermService termService = mock(TechTermService.class);
    private final TechDocChunkMapper chunkMapper = mock(TechDocChunkMapper.class);
    private final AiAuditService auditService = mock(AiAuditService.class);
    private final AiProperties properties = new AiProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        servers.forEach(server -> server.stop(0));
        servers.clear();
    }

    private static TechAssistService.Evidence evidence(String chunkId, String docId, int index,
                                                      String title, String sourceName,
                                                      String snippet) {
        return new TechAssistService.Evidence(chunkId, docId, index, title, sourceName,
                "v1.2", "第三章 > 校验", 12, snippet);
    }

    private TechChunkHit hit(long docId, int chunkIndex, String content) {
        TechChunkHit hit = new TechChunkHit();
        hit.setDocId(docId);
        hit.setChunkIndex(chunkIndex);
        hit.setContent(content);
        hit.setTitle("存储可靠性手册");
        hit.setSourceName("内部文档");
        hit.setVersion("v1.2");
        hit.setSectionPath("第三章 > 校验");
        hit.setPageNo(12);
        return hit;
    }

    private HttpServer server(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tech/wording", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, resp.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(resp);
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private TechAssistService service(int port) {
        when(auditService.begin(anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(1L);
        return new TechAssistService(termService, chunkMapper,
                WebClient.builder().baseUrl("http://127.0.0.1:" + port).build(),
                properties, auditService, objectMapper);
    }

    private TechWordingRequest request() {
        TechWordingRequest req = new TechWordingRequest();
        req.setSectionPath("第三章");
        req.setText(TEXT);
        return req;
    }

    @Test
    void mergeRrfOrdersByCombinedRankAndEnrichesFields() {
        TechAssistService.Evidence a = evidence("1:0", "1", 0, "标题A", "", "片段A");
        TechAssistService.Evidence b = evidence("1:1", "1", 1, "标题B", "", "片段B");
        TechAssistService.Evidence c = evidence("2:0", "2", 0, "标题C", "文档C", "片段C");
        TechAssistService.Evidence bFulltext = evidence("1:1", "1", 1, "", "文档B", "片段B2");

        List<TechAssistService.Evidence> merged = TechAssistService.mergeRrf(
                List.of(a, b), List.of(bFulltext, c), 6);

        assertEquals(List.of("1:1", "1:0", "2:0"),
                merged.stream().map(TechAssistService.Evidence::chunkId).toList());
        TechAssistService.Evidence top = merged.get(0);
        assertEquals("标题B", top.title(), "两侧命中应优先保留已有字段");
        assertEquals("文档B", top.sourceName(), "缺失字段应由另一侧补齐");
        assertEquals(12, top.pageNo());
    }

    @Test
    void mergeRrfKeepsFulltextWhenVectorFails() {
        TechAssistService.Evidence c = evidence("2:0", "2", 0, "标题C", "文档C", "片段C");
        List<TechAssistService.Evidence> merged =
                TechAssistService.mergeRrf(List.of(), List.of(c), 6);
        assertEquals(1, merged.size());
        assertEquals("2:0", merged.get(0).chunkId());
    }

    @Test
    void wordingDegradesWhenPythonFailsAndKeepsRuleIssues() throws IOException {
        HttpServer server = server(500, "{\"error\":{\"code\":\"LLM_ERROR\"}}");
        List<Map<String, Object>> rules = List.of(Map.of(
                "type", "terminology", "quote", "checksum", "replacement", "校验和"));
        when(termService.check(anyString())).thenReturn(rules);

        Map<String, Object> resp = service(server.getAddress().getPort()).wording(9L, request());

        assertTrue((Boolean) resp.get("degraded"));
        assertEquals("依据检索暂不可用，仅展示术语规则", resp.get("message"));
        assertNotNull(resp.get("traceId"));
        assertEquals(rules, resp.get("issues"), "降级只返回规则项");

        ArgumentCaptor<AiAuditService.AiResult> captor =
                ArgumentCaptor.forClass(AiAuditService.AiResult.class);
        verify(auditService).finish(any(), captor.capture());
        assertEquals("failed", captor.getValue().status());
    }

    @Test
    void wordingMergesRuleIssuesBeforeAiIssues() throws IOException {
        String body = """
                {"issues":[
                  {"type":"terminology","quote":"checksum","replacement":"校验和",
                   "reason":"术语表规范写法","severity":"mid"},
                  {"type":"fact","quote":"如果校验失败就重试一次","reason":"无证据","severity":"high"}
                ],"usage":{"prompt_tokens":10,"completion_tokens":20},
                "model":"test-model","prompt_version":"1.0.0"}
                """;
        HttpServer server = server(200, body);
        when(termService.check(anyString())).thenReturn(List.of(Map.of(
                "type", "terminology", "quote", "checksum", "replacement", "校验和")));

        Map<String, Object> resp = service(server.getAddress().getPort()).wording(9L, request());

        assertFalse((Boolean) resp.get("degraded"));
        List<?> issues = (List<?>) resp.get("issues");
        assertEquals(1, issues.size(), "重复 quote 保留规则项，无证据 fact 丢弃");
        assertEquals("terminology", ((Map<?, ?>) issues.get(0)).get("type"));
    }

    @Test
    void validateIssuesRequiresUniqueQuoteAndEvidenceForFact() throws IOException {
        TechAssistService.Evidence hit = evidence("1:2", "1", 2, "手册", "内部文档", "片段");
        JsonNode issues = objectMapper.readTree("""
                [
                  {"type":"fact","quote":"如果校验失败就重试一次","severity":"high",
                   "evidence_chunk_id":"1:2","replacement":"如果校验失败就报错"},
                  {"type":"fact","quote":"把数据写入磁盘前","severity":"high","evidence_chunk_id":"9:9"},
                  {"type":"terminology","quote":"不存在的片段","severity":"mid"},
                  {"type":"unknown","quote":"先校验 checksum","severity":"mid"},
                  {"type":"clarity","quote":"先校验 checksum","severity":"bogus"}
                ]
                """);
        List<Map<String, Object>> result = service(0).validateIssues(TEXT, issues, List.of(hit));

        assertEquals(2, result.size());
        Map<String, Object> fact = result.get(0);
        assertEquals("1:2", fact.get("evidenceChunkId"));
        assertEquals("1", ((Map<?, ?>) fact.get("evidence")).get("docId"));
        assertEquals(12, ((Map<?, ?>) fact.get("evidence")).get("pageNo"));
        assertEquals("mid", result.get(1).get("severity"), "非法 severity 回落 mid");
        assertNull(result.get(1).get("evidence"));
    }

    @Test
    void wordingReturnsRuleIssuesWhenModelOutputsNothing() throws IOException {
        HttpServer server = server(200, "{\"issues\":[]}");
        List<Map<String, Object>> rules = List.of(Map.of(
                "type", "terminology", "quote", "checksum", "replacement", "校验和"));
        when(termService.check(anyString())).thenReturn(rules);

        Map<String, Object> resp = service(server.getAddress().getPort()).wording(9L, request());

        assertFalse((Boolean) resp.get("degraded"));
        assertEquals(rules, resp.get("issues"));
    }

    @Test
    void eleventhCallInWindowIsRejected() throws IOException {
        HttpServer server = server(200, "{\"issues\":[]}");
        TechAssistService service = service(server.getAddress().getPort());

        for (int i = 0; i < 10; i++) {
            service.wording(7L, request());
        }
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.wording(7L, request()));
        assertEquals(ErrorCode.TOO_MANY_ATTEMPTS, error.getErrorCode());
    }

    @Test
    void fulltextHitsBecomeEvidenceWithTrimmedSnippet() {
        when(termService.check(anyString())).thenReturn(List.of());
        when(chunkMapper.searchFulltext(anyString(), anyInt()))
                .thenReturn(List.of(hit(1L, 0, "第一行\n第二行\t" + "字".repeat(300))));
        TechAssistService service = service(0);

        List<TechAssistService.Evidence> evidenceList = service.retrieveEvidence(TEXT);

        assertEquals(1, evidenceList.size());
        TechAssistService.Evidence item = evidenceList.get(0);
        assertEquals("1:0", item.chunkId());
        assertEquals(200, item.snippet().length(), "证据片段裁剪到 200 字");
        assertFalse(item.snippet().contains("\n"));
    }
}

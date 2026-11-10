package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.dto.tech.TechDocRequest;
import com.aiblog.dto.tech.TechTermRequest;
import com.aiblog.entity.TechDoc;
import com.aiblog.entity.TechDocChunk;
import com.aiblog.entity.TechTerm;
import com.aiblog.security.CurrentUser;
import com.aiblog.service.AdminLogService;
import com.aiblog.service.TechDocService;
import com.aiblog.service.TechTermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 管理端：技术语料（文档 / 切块 / 术语）与质量统计。
 */
@RestController
@RequestMapping("/api/admin/tech")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_ADMIN')")
public class AdminTechController {

    private final TechDocService docService;
    private final TechTermService termService;
    private final AdminLogService adminLogService;

    // ------------------------------------------------------------------ 文档

    @GetMapping("/docs")
    public ApiResponse<Map<String, Object>> docs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(docService.page(page, size, status, keyword));
    }

    @PostMapping("/docs")
    public ApiResponse<TechDoc> create(@Valid @RequestBody TechDocRequest request) {
        TechDoc doc = docService.createFromText(request, CurrentUser.id());
        adminLogService.record(CurrentUser.get(), "TECH_DOC_CREATE", "tech_doc:" + doc.getId(),
                Map.of("title", doc.getTitle(), "status", doc.getStatus()));
        return ApiResponse.ok(doc);
    }

    @PostMapping("/docs/upload")
    public ApiResponse<TechDoc> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String license) {
        TechDoc doc = docService.upload(file, title, docType, sourceName, version, license,
                CurrentUser.id());
        adminLogService.record(CurrentUser.get(), "TECH_DOC_UPLOAD", "tech_doc:" + doc.getId(),
                Map.of("title", doc.getTitle(), "fileType", doc.getFileType(),
                        "status", doc.getStatus()));
        return ApiResponse.ok(doc);
    }

    @PutMapping("/docs/{id}")
    public ApiResponse<TechDoc> update(@PathVariable Long id,
                                       @Valid @RequestBody TechDocRequest request) {
        TechDoc doc = docService.update(id, request);
        adminLogService.record(CurrentUser.get(), "TECH_DOC_UPDATE", "tech_doc:" + id,
                Map.of("title", doc.getTitle(), "status", doc.getStatus()));
        return ApiResponse.ok(doc);
    }

    @DeleteMapping("/docs/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        docService.delete(id);
        adminLogService.record(CurrentUser.get(), "TECH_DOC_DELETE", "tech_doc:" + id, Map.of());
        return ApiResponse.ok();
    }

    @PostMapping("/docs/{id}/index")
    public ApiResponse<Map<String, Object>> index(@PathVariable Long id) {
        Map<String, Object> result = docService.index(id);
        adminLogService.record(CurrentUser.get(), "TECH_DOC_INDEX", "tech_doc:" + id, result);
        return ApiResponse.ok(result);
    }

    @GetMapping("/docs/{id}/chunks")
    public ApiResponse<List<TechDocChunk>> chunks(@PathVariable Long id) {
        return ApiResponse.ok(docService.chunks(id));
    }

    // ------------------------------------------------------------------ 术语

    @GetMapping("/terms")
    public ApiResponse<List<TechTerm>> terms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(termService.list(keyword, category, false));
    }

    @PostMapping("/terms")
    public ApiResponse<TechTerm> createTerm(@Valid @RequestBody TechTermRequest request) {
        TechTerm term = termService.create(request, CurrentUser.id());
        adminLogService.record(CurrentUser.get(), "TECH_TERM_CREATE", "tech_term:" + term.getId(),
                Map.of("canonical", term.getCanonical()));
        return ApiResponse.ok(term);
    }

    @PutMapping("/terms/{id}")
    public ApiResponse<TechTerm> updateTerm(@PathVariable Long id,
                                            @Valid @RequestBody TechTermRequest request) {
        TechTerm term = termService.update(id, request);
        adminLogService.record(CurrentUser.get(), "TECH_TERM_UPDATE", "tech_term:" + id,
                Map.of("canonical", term.getCanonical()));
        return ApiResponse.ok(term);
    }

    @DeleteMapping("/terms/{id}")
    public ApiResponse<Void> deleteTerm(@PathVariable Long id) {
        termService.delete(id);
        adminLogService.record(CurrentUser.get(), "TECH_TERM_DELETE", "tech_term:" + id, Map.of());
        return ApiResponse.ok();
    }

    @PostMapping("/terms/import")
    public ApiResponse<Map<String, Object>> importTerms(
            @Valid @RequestBody List<TechTermRequest> items) {
        Map<String, Object> result = termService.importTerms(items, CurrentUser.id());
        adminLogService.record(CurrentUser.get(), "TECH_TERM_IMPORT", "tech_term", result);
        return ApiResponse.ok(result);
    }

    // ------------------------------------------------------------------ 统计

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(docService.stats());
    }
}

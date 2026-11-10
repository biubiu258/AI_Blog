package com.aiblog.controller;

import com.aiblog.common.ApiResponse;
import com.aiblog.service.StyleProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 个人文风库：创建（提炼）/ 列表 / 删除 / 设默认。
 */
@RestController
@RequestMapping("/api/style-profiles")
@RequiredArgsConstructor
public class StyleProfileController {

    private final StyleProfileService styleProfileService;

    /** 提炼个人文风：选 3–10 篇自己的文章作样本，同步返回提炼结果 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(styleProfileService.create(request.name(), request.articleIds()));
    }

    /** 我的文风列表（默认档在前） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(styleProfileService.listMine());
    }

    /** 设为默认文风 */
    @PutMapping("/{id}/default")
    public ApiResponse<Map<String, Object>> setDefault(@PathVariable Long id) {
        return ApiResponse.ok(styleProfileService.setDefault(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        styleProfileService.delete(id);
        return ApiResponse.ok();
    }

    public record CreateRequest(
            @NotNull @Size(max = 64, message = "风格名称不超过 64 字") String name,
            @NotEmpty List<Long> articleIds) {}
}

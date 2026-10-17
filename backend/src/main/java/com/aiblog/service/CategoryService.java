package com.aiblog.service;

import com.aiblog.common.BusinessException;
import com.aiblog.common.ErrorCode;
import com.aiblog.dto.category.CategorySaveRequest;
import com.aiblog.dto.category.CategoryItem;
import com.aiblog.entity.Article;
import com.aiblog.entity.Category;
import com.aiblog.mapper.ArticleMapper;
import com.aiblog.mapper.CategoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    public List<CategoryItem> listAll() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getId)).stream()
                .map(c -> CategoryItem.of(c, countArticles(c.getId())))
                .toList();
    }

    public CategoryItem create(CategorySaveRequest request) {
        checkNameUnique(request.getName(), null);
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类名已存在");
        }
        return CategoryItem.of(category, 0);
    }

    public CategoryItem rename(Long id, CategorySaveRequest request) {
        Category category = mustGet(id);
        checkNameUnique(request.getName(), id);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        try {
            categoryMapper.updateById(category);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类名已存在");
        }
        return CategoryItem.of(category, countArticles(id));
    }

    /**
     * 默认策略：分类下仍有文章时不可删，避免悬挂引用（见需求 1.3.3）。
     */
    public void delete(Long id) {
        mustGet(id);
        if (countArticles(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类下仍有文章，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    public Category mustGet(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    public Category mustGetOrNull(Long id) {
        return id == null ? null : categoryMapper.selectById(id);
    }

    public List<Category> mustGetAll(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return categoryMapper.selectBatchIds(ids);
    }

    private long countArticles(Long categoryId) {
        Long count = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                .eq(Article::getCategoryId, categoryId));
        return count == null ? 0 : count;
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类名已存在");
        }
    }
}

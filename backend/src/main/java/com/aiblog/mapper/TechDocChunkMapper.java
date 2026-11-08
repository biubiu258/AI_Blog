package com.aiblog.mapper;

import com.aiblog.dto.tech.TechChunkHit;
import com.aiblog.entity.TechDocChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TechDocChunkMapper extends BaseMapper<TechDocChunk> {

    /**
     * MySQL ngram 全文检索：只查 ready 文档，按相关度倒序。
     */
    @Select("""
            SELECT c.id, c.doc_id, c.chunk_index, c.page_no, c.section_path, c.content,
                   d.title, d.source_name, d.version,
                   MATCH(c.content) AGAINST (#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM tech_doc_chunk c
            JOIN tech_doc d ON d.id = c.doc_id
            WHERE d.status = 'ready'
              AND MATCH(c.content) AGAINST (#{query} IN NATURAL LANGUAGE MODE) > 0
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<TechChunkHit> searchFulltext(@Param("query") String query, @Param("limit") int limit);

    @Delete("DELETE FROM tech_doc_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);
}

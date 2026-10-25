package com.aiblog.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作者数据面板与热门排行所需的聚合查询。
 */
public interface StatsMapper {

    /** 按状态统计作者文章数 */
    @Select("""
            SELECT status AS status, COUNT(*) AS cnt
            FROM article
            WHERE author_id = #{authorId} AND deleted = 0
            GROUP BY status
            """)
    List<Map<String, Object>> countByStatus(@Param("authorId") Long authorId);

    /** 已发布文章累计阅读量 */
    @Select("""
            SELECT COALESCE(SUM(view_count), 0)
            FROM article
            WHERE author_id = #{authorId} AND deleted = 0 AND status = 'published'
            """)
    long totalViews(@Param("authorId") Long authorId);

    /** 作者收到的点赞总数 */
    @Select("""
            SELECT COUNT(*)
            FROM article_like l
            JOIN article a ON a.id = l.article_id
            WHERE a.author_id = #{authorId} AND a.deleted = 0
            """)
    long likesReceived(@Param("authorId") Long authorId);

    /** 作者收到的收藏总数 */
    @Select("""
            SELECT COUNT(*)
            FROM article_favorite f
            JOIN article a ON a.id = f.article_id
            WHERE a.author_id = #{authorId} AND a.deleted = 0
            """)
    long favoritesReceived(@Param("authorId") Long authorId);

    /** 作者收到的有效评论总数 */
    @Select("""
            SELECT COUNT(*)
            FROM comment c
            JOIN article a ON a.id = c.article_id
            WHERE a.author_id = #{authorId} AND a.deleted = 0
              AND c.deleted = 0 AND c.status = 'approved'
            """)
    long commentsReceived(@Param("authorId") Long authorId);

    /** 区间内每日阅读量 */
    @Select("""
            SELECT v.view_date AS viewDate, COUNT(*) AS cnt
            FROM article_view v
            JOIN article a ON a.id = v.article_id
            WHERE a.author_id = #{authorId} AND a.deleted = 0 AND v.view_date >= #{from}
            GROUP BY v.view_date
            ORDER BY v.view_date
            """)
    List<Map<String, Object>> dailyViews(@Param("authorId") Long authorId,
                                         @Param("from") LocalDate from);

    /** 作者阅读量最高的已发布文章 */
    @Select("""
            SELECT a.id AS id, a.title AS title,
                   a.view_count AS viewCount,
                   (SELECT COUNT(*) FROM article_like l WHERE l.article_id = a.id) AS likeCount,
                   (SELECT COUNT(*) FROM article_favorite f WHERE f.article_id = a.id) AS favoriteCount
            FROM article a
            WHERE a.author_id = #{authorId} AND a.deleted = 0 AND a.status = 'published'
            ORDER BY a.view_count DESC, a.published_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topArticles(@Param("authorId") Long authorId,
                                          @Param("limit") int limit);

    /**
     * 全站热门：区间内阅读 / 点赞 / 收藏加权排序；from 与 fromDate 同时为空表示总榜。
     */
    @Select("""
            <script>
            SELECT t.* FROM (
                SELECT a.id AS id, a.title AS title, a.summary AS summary,
                       a.author_id AS authorId, a.category_id AS categoryId,
                       a.view_count AS viewCount, a.published_at AS publishedAt,
                       u.nickname AS authorNickname, u.avatar_url AS authorAvatar,
                       c.name AS categoryName,
                       (SELECT COUNT(*) FROM article_like l
                         WHERE l.article_id = a.id
                         <if test="from != null">AND l.created_at &gt;= #{from}</if>) AS likeCount,
                       (SELECT COUNT(*) FROM article_favorite f
                         WHERE f.article_id = a.id
                         <if test="from != null">AND f.created_at &gt;= #{from}</if>) AS favoriteCount,
                       <choose>
                         <when test="fromDate != null">
                           (SELECT COUNT(*) FROM article_view v
                             WHERE v.article_id = a.id AND v.view_date &gt;= #{fromDate})
                         </when>
                         <otherwise>a.view_count</otherwise>
                       </choose> AS rangeViews
                FROM article a
                LEFT JOIN `user` u ON u.id = a.author_id
                LEFT JOIN category c ON c.id = a.category_id
                WHERE a.deleted = 0 AND a.status = 'published'
            ) t
            ORDER BY (t.rangeViews * 1 + t.likeCount * 5 + t.favoriteCount * 8) DESC,
                     t.publishedAt DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> hotArticles(@Param("from") LocalDateTime from,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("limit") int limit);
}

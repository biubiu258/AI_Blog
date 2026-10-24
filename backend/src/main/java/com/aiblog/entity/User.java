package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String email;

    private String nickname;

    private String avatarUrl;

    private String bio;

    /** USER / ADMIN / CONTENT_ADMIN */
    private String role;

    /** 0 正常 / 1 封禁 */
    private Integer status;

    /** 封禁原因（封禁期间有值） */
    private String banReason;

    /** 封禁截止时间，NULL 表示永久 */
    private LocalDateTime bannedUntil;

    /** 令牌版本：递增使已签发令牌全部失效 */
    private Integer tokenVersion;

    /** 0 未验证 / 1 已验证（不阻塞登录） */
    private Integer emailVerified;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 封禁是否仍生效：status=1 且（无期限或未到期）。
     * 已到期但未回正的记录由调用方惰性解封（回写 status=0）。
     */
    public boolean isBanActive() {
        return status != null && status == 1
                && (bannedUntil == null || bannedUntil.isAfter(LocalDateTime.now()));
    }
}

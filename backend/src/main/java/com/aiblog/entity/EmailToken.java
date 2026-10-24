package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_token")
public class EmailToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String email;
    private String token;
    /** verify / reset */
    private String type;
    private LocalDateTime expiresAt;
    private Integer used;
    private LocalDateTime createdAt;
}

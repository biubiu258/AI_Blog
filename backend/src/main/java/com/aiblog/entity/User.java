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

    /** USER / ADMIN */
    private String role;

    /** 0 正常 / 1 封禁 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.aiblog.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 64, message = "昵称最长 64 个字符")
    private String nickname;

    @Size(max = 500, message = "简介最长 500 个字符")
    private String bio;

    @Size(max = 255, message = "头像地址过长")
    private String avatarUrl;

    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;
}

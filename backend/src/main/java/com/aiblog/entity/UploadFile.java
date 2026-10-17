package com.aiblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upload_file")
public class UploadFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String filePath;

    private String url;

    private Long size;

    private LocalDateTime createdAt;
}

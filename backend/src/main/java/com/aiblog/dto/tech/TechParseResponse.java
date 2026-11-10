package com.aiblog.dto.tech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Python /v1/tech/parse 响应（字段为 snake_case）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TechParseResponse {

    private String title;

    @JsonProperty("file_type")
    private String fileType;

    @JsonProperty("page_count")
    private Integer pageCount;

    private List<TechChunkItem> chunks = new ArrayList<>();
}

package com.aiblog.dto.tech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Python 解析返回的单个块。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TechChunkItem {

    @JsonProperty("chunk_index")
    private Integer chunkIndex;

    @JsonProperty("page_no")
    private Integer pageNo;

    @JsonProperty("section_path")
    private String sectionPath;

    private String content;

    @JsonProperty("token_count")
    private Integer tokenCount;
}

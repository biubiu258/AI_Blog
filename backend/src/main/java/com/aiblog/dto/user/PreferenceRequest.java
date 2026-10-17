package com.aiblog.dto.user;

import com.aiblog.entity.UserPreference;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 写作偏好（P1）：随 AI 生成请求以 user_context.preferences 传给 Python。
 */
@Data
public class PreferenceRequest {

    @Size(max = 200, message = "口吻最长 200 个字符")
    private String tone;

    @Size(max = 200, message = "目标读者最长 200 个字符")
    private String targetReader;

    @Size(max = 5000, message = "常用结构过长")
    private String structure;

    @Size(max = 5000, message = "其他偏好过长")
    private String extra;

    public static PreferenceRequest from(UserPreference p) {
        if (p == null) {
            return null;
        }
        PreferenceRequest r = new PreferenceRequest();
        r.setTone(p.getTone());
        r.setTargetReader(p.getTargetReader());
        r.setStructure(p.getStructure());
        r.setExtra(p.getExtra());
        return r;
    }
}

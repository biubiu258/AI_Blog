<params>
文章类型：{{ article_type or "未指定" }}
写作风格：{{ style or "未指定" }}
目标读者：{{ target_reader or "未指定" }}
</params>
<material type="article_title">{{ title }}</material>
<material type="section_position">
上一节：{{ previous_section or "（无）" }}
下一节：{{ next_section or "（无）" }}
</material>
<material type="section">
章节：{{ section_title }}
要点：
{{ bullets }}
</material>
{% if instruction %}
<instruction>
{{ instruction }}
</instruction>
{% endif %}
请只输出本节正文。

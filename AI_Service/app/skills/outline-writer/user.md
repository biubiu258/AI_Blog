<params>
主题：{{ topic }}
文章类型：{{ article_type or "未指定" }}
写作风格：{{ style or "未指定" }}
目标读者：{{ target_reader or "未指定" }}
</params>
{% if instruction %}
<instruction>
{{ instruction }}
</instruction>
{% endif %}
请直接输出大纲。

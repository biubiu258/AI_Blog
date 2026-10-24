<params>
文章类型：{{ article_type or "未指定" }}
写作风格：{{ style or "未指定" }}
目标读者：{{ target_reader or "未指定" }}
</params>
<material type="outline">
{{ outline }}
</material>
{% if instruction %}
<instruction>
{{ instruction }}
</instruction>
{% endif %}
请严格按大纲展开正文。

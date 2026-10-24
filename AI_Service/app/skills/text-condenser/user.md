<params>
文章类型：{{ article_type or "未指定" }}
写作风格：{{ style or "未指定" }}
目标读者：{{ target_reader or "未指定" }}
</params>
{% if instruction %}
<instruction>
{{ instruction }}
</instruction>
{% endif %}
{% if context_note %}
<material type="preceding_context">前文末尾，仅供衔接，不要输出：{{ context_note }}</material>
{% endif %}
<material type="original_text">
{{ material_text }}
</material>
请输出缩写后的完整正文。

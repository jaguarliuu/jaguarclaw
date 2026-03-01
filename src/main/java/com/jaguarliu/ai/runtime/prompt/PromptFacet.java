package com.jaguarliu.ai.runtime.prompt;

/**
 * Prompt Facet 接口
 * 单一职责：根据上下文生成一段 prompt 片段。
 */
public interface PromptFacet {

    /**
     * 当前 Facet 写入的占位符 key（例如 SOUL / TOOLS / MEMORY）。
     */
    String key();

    /**
     * 是否在当前上下文生效。
     */
    boolean supports(PromptAssemblyContext context);

    /**
     * 生成 prompt 片段，建议返回带结尾空行（\n\n）的段落。
     */
    String render(PromptAssemblyContext context);
}

package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.runtime.SystemPromptBuilder;

/**
 * Memory Facet（Agent 作用域）
 */
public class MemoryPromptFacet implements PromptFacet {

    @SuppressWarnings("unused")
    private final MemorySearchService memorySearchService;

    public MemoryPromptFacet(MemorySearchService memorySearchService) {
        this.memorySearchService = memorySearchService;
    }

    @Override
    public String key() {
        return "MEMORY";
    }

    @Override
    public boolean supports(PromptAssemblyContext context) {
        return context.getMode() == SystemPromptBuilder.PromptMode.FULL;
    }

    @Override
    public String render(PromptAssemblyContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Memory\n\n");
        sb.append("You have access to a **dual-scope memory system** (global + agent-private):\n\n");
        sb.append(String.format("- Active agent scope: `%s`\n", context.getAgentId()));
        sb.append("- `memory_search(query, scope?)`: Search memories\n");
        sb.append("  - scope=\"both\" (default) → this agent + global\n");
        sb.append("  - scope=\"agent\" → this agent only\n");
        sb.append("  - scope=\"global\" → shared only\n");
        sb.append("- `memory_write(content, target, scope?)`: Save important information\n");
        sb.append("  - scope=\"agent\" (default) → private to current agent\n");
        sb.append("  - scope=\"global\" → shared across agents\n");
        sb.append("- `read_file(path)`: Read memory files directly when needed\n");
        sb.append("  - target=\"core\" → MEMORY.md (long-term: preferences, constraints)\n");
        sb.append("  - target=\"daily\" → Today's log (session summaries, work records)\n\n");
        sb.append("**Key point**: Global memory is cross-session and shared by all agents; ");
        sb.append("agent memory is isolated to each agent profile.\n\n");
        sb.append("**When to use memory:**\n");
        sb.append("- Search for relevant context at conversation start\n");
        sb.append("- Save stable user preferences to global core memory\n");
        sb.append("- Save agent-specific strategy/working notes to agent scope\n");
        sb.append("- Summarize significant tasks to daily log\n\n");
        return sb.toString();
    }
}

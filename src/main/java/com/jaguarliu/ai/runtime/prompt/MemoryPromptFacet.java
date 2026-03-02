package com.jaguarliu.ai.runtime.prompt;

import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.runtime.SystemPromptBuilder;

/**
 * Memory Facet（Agent 作用域）
 */
public class MemoryPromptFacet implements PromptFacet {

    @SuppressWarnings("unused")
    private final MemorySearchService memorySearchService;
    private final MemoryStore memoryStore;

    public MemoryPromptFacet(MemorySearchService memorySearchService, MemoryStore memoryStore) {
        this.memorySearchService = memorySearchService;
        this.memoryStore = memoryStore;
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

        // Inject global MEMORY.md content as the index
        if (memoryStore != null) {
            try {
                String memoryContent = memoryStore.read("MEMORY.md", null, MemoryScope.GLOBAL);
                if (memoryContent != null && !memoryContent.isBlank()) {
                    sb.append(memoryContent.trim()).append("\n\n");
                    sb.append("---\n\n");
                }
            } catch (Exception e) {
                // File doesn't exist yet or read failed — skip silently
            }
        }

        sb.append(String.format("**Memory Tools** (agent scope: `%s`):\n", context.getAgentId()));
        sb.append("- `memory_search(query, scope?)`: Search memories\n");
        sb.append("  - scope=\"both\" (default) → this agent + global\n");
        sb.append("  - scope=\"agent\" → this agent only\n");
        sb.append("  - scope=\"global\" → shared only\n");
        sb.append("- `memory_write(content, file?, scope?)`: Save important information\n");
        sb.append("  - file omitted → today's date log (default)\n");
        sb.append("  - file=\"MEMORY.md\" → update the index only (no substantive content)\n");
        sb.append("  - file=\"projects.md\" → write to named file\n");
        sb.append("  - scope=\"agent\" (default) / scope=\"global\"\n");
        sb.append("- `read_file(path)`: Read any memory file directly\n\n");

        return sb.toString();
    }
}

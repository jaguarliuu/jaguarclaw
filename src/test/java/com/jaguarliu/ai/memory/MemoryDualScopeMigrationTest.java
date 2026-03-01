package com.jaguarliu.ai.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Memory Dual Scope Migration Tests")
class MemoryDualScopeMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V20 应为 memory_chunks 增加 scope 和 agent_id 列")
    void shouldAddScopeAndAgentIdColumns() {
        List<Map<String, Object>> tableInfo = jdbcTemplate.queryForList("PRAGMA table_info('memory_chunks')");
        Set<String> columns = new HashSet<>();
        for (Map<String, Object> row : tableInfo) {
            Object name = row.get("name");
            if (name != null) {
                columns.add(name.toString().toLowerCase());
            }
        }

        assertThat(columns).contains("scope", "agent_id");
    }

    @Test
    @DisplayName("V20 应创建 scope 与 scope+agent_id 索引")
    void shouldCreateScopeIndexes() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("PRAGMA index_list('memory_chunks')");
        Set<String> indexNames = new HashSet<>();
        for (Map<String, Object> index : indexes) {
            Object name = index.get("name");
            if (name != null) {
                indexNames.add(name.toString().toLowerCase());
            }
        }

        assertThat(indexNames).contains("idx_memory_chunks_scope", "idx_memory_chunks_scope_agent");
    }

    @Test
    @DisplayName("V20 默认值应把 scope 置为 GLOBAL")
    void shouldDefaultScopeToGlobal() {
        jdbcTemplate.update(
                "INSERT INTO memory_chunks (id, file_path, line_start, line_end, content) VALUES (?, ?, ?, ?, ?)",
                "mc-scope-default",
                "MEMORY.md",
                1,
                2,
                "dual-scope migration test"
        );

        String scope = jdbcTemplate.queryForObject(
                "SELECT scope FROM memory_chunks WHERE id = ?",
                String.class,
                "mc-scope-default"
        );

        assertThat(scope).isEqualTo("GLOBAL");
    }
}

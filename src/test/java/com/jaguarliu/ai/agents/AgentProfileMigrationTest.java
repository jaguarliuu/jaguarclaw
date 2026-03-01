package com.jaguarliu.ai.agents;

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
@DisplayName("Agent Profile Migration Tests")
class AgentProfileMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V19 应创建 agent_profile 表与核心列")
    void shouldCreateAgentProfileTableWithExpectedColumns() {
        List<Map<String, Object>> tableInfo = jdbcTemplate.queryForList("PRAGMA table_info('agent_profile')");
        Set<String> columns = new HashSet<>();
        for (Map<String, Object> row : tableInfo) {
            Object name = row.get("name");
            if (name != null) {
                columns.add(name.toString().toLowerCase());
            }
        }

        assertThat(columns).isNotEmpty();
        assertThat(columns).contains(
                "id",
                "name",
                "display_name",
                "description",
                "workspace_path",
                "model",
                "enabled",
                "is_default",
                "allowed_tools",
                "excluded_tools",
                "created_at",
                "updated_at"
        );
    }

    @Test
    @DisplayName("V19 应在 agent_profile(name) 上创建唯一索引")
    void shouldCreateUniqueIndexOnName() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("PRAGMA index_list('agent_profile')");
        boolean hasUniqueNameIndex = false;

        for (Map<String, Object> index : indexes) {
            Object unique = index.get("unique");
            Object indexName = index.get("name");
            if (!(unique instanceof Number) || !(indexName instanceof String)) {
                continue;
            }
            if (((Number) unique).intValue() != 1) {
                continue;
            }

            String pragma = "PRAGMA index_info('" + indexName + "')";
            List<Map<String, Object>> indexInfo = jdbcTemplate.queryForList(pragma);
            for (Map<String, Object> col : indexInfo) {
                Object colName = col.get("name");
                if (colName != null && "name".equalsIgnoreCase(colName.toString())) {
                    hasUniqueNameIndex = true;
                    break;
                }
            }
            if (hasUniqueNameIndex) {
                break;
            }
        }

        assertThat(hasUniqueNameIndex).isTrue();
    }
}

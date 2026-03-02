package com.jaguarliu.ai.skills;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.parser.SkillParser;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Skill scope registry tests")
class SkillScopeRegistryTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGatingService gatingService;

    private SkillRegistry registry;
    private SkillIndexBuilder indexBuilder;

    private Path globalSkillsDir;
    private Path architectSkillsDir;
    private Path writerSkillsDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

        SkillParser parser = new SkillParser();
        registry = new SkillRegistry(parser, gatingService);
        indexBuilder = new SkillIndexBuilder(registry);
        indexBuilder.setIndexTokenBudget(2000);

        globalSkillsDir = tempDir.resolve("global-skills");
        architectSkillsDir = tempDir.resolve("workspace/workspace-architect/skills");
        writerSkillsDir = tempDir.resolve("workspace/workspace-writer/skills");

        Files.createDirectories(globalSkillsDir);
        Files.createDirectories(architectSkillsDir);
        Files.createDirectories(writerSkillsDir);

        registry.configure(globalSkillsDir, null, null);
        registry.configureAgentSkills(Map.of(
                "architect", architectSkillsDir,
                "writer", writerSkillsDir
        ));
    }

    @Test
    @DisplayName("global + agent skills should merge with agent isolation")
    void mergeGlobalAndAgentSkills() throws IOException {
        createSkill(globalSkillsDir, "common", "Global common", "GLOBAL COMMON");
        createSkill(globalSkillsDir, "global-only", "Global only", "GLOBAL ONLY");
        createSkill(architectSkillsDir, "agent-only", "Architect only", "ARCH ONLY");

        registry.refresh();

        Set<String> architectNames = registry.getAvailable("architect").stream()
                .map(e -> e.getMetadata().getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("common", "global-only", "agent-only"), architectNames);

        Set<String> writerNames = registry.getAvailable("writer").stream()
                .map(e -> e.getMetadata().getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("common", "global-only"), writerNames);
    }

    @Test
    @DisplayName("agent scoped skill should override global skill with same name")
    void agentOverrideGlobalSkill() throws IOException {
        createSkill(globalSkillsDir, "planner", "Global planner", "GLOBAL PLANNER");
        createSkill(architectSkillsDir, "planner", "Architect planner", "ARCH PLANNER");

        registry.refresh();

        SkillEntry architectPlanner = registry.getByName("planner", "architect").orElseThrow();
        assertEquals("Architect planner", architectPlanner.getMetadata().getDescription());

        SkillEntry writerPlanner = registry.getByName("planner", "writer").orElseThrow();
        assertEquals("Global planner", writerPlanner.getMetadata().getDescription());

        Optional<LoadedSkill> loadedArchitect = registry.activate("planner", "architect");
        assertTrue(loadedArchitect.isPresent());
        assertTrue(loadedArchitect.get().getBody().contains("ARCH PLANNER"));
    }

    @Test
    @DisplayName("skill index should follow agent scope visibility")
    void skillIndexByAgentScope() throws IOException {
        createSkill(globalSkillsDir, "global-only", "Global only", "GLOBAL");
        createSkill(architectSkillsDir, "architect-only", "Architect only", "ARCH");

        registry.refresh();

        String architectIndex = indexBuilder.buildCompactIndex("architect");
        assertTrue(architectIndex.contains("global-only"));
        assertTrue(architectIndex.contains("architect-only"));

        String writerIndex = indexBuilder.buildCompactIndex("writer");
        assertTrue(writerIndex.contains("global-only"));
        assertFalse(writerIndex.contains("architect-only"));
    }

    private void createSkill(Path baseDir, String name, String description, String body) throws IOException {
        Path skillDir = baseDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), String.format("""
                ---
                name: %s
                description: %s
                ---
                # %s
                %s
                """, name, description, name, body));
    }
}

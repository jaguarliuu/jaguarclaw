package com.jaguarliu.ai.skills.selector;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.parser.SkillParser;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("SkillSelector Tests")
class SkillSelectorTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillGatingService gatingService;

    private SkillParser parser;
    private SkillRegistry registry;
    private SkillSelector selector;

    private Path skillsDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        parser = new SkillParser();

        when(gatingService.evaluate(any())).thenReturn(GatingResult.PASSED);

        registry = new SkillRegistry(parser, gatingService);
        selector = new SkillSelector(registry);

        skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);

        registry.configure(skillsDir, null, null);
        createSkill("code-review", "代码审查");
        createSkill("git-commit", "生成 commit message");
        registry.refresh();
    }

    @Nested
    @DisplayName("手动触发（/skill-name）")
    class ManualSelectionTests {

        @Test
        @DisplayName("匹配 /skill-name")
        void matchSlashCommand() {
            SkillSelection result = selector.tryManualSelection("/code-review");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
            assertNull(result.getArguments());
            assertEquals(SkillSelection.SelectionSource.MANUAL, result.getSource());
        }

        @Test
        @DisplayName("匹配 /skill-name 带参数")
        void matchSlashCommandWithArgs() {
            SkillSelection result = selector.tryManualSelection("/code-review src/main/java/App.java");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
            assertEquals("src/main/java/App.java", result.getArguments());
        }

        @Test
        @DisplayName("匹配多行参数")
        void matchMultilineArgs() {
            String input = "/code-review 请审查以下代码：\nclass Foo {\n}";
            SkillSelection result = selector.tryManualSelection(input);

            assertTrue(result.isSelected());
            assertTrue(result.getArguments().contains("class Foo"));
        }

        @Test
        @DisplayName("不存在的 skill 返回 none")
        void nonexistentSkillReturnsNone() {
            SkillSelection result = selector.tryManualSelection("/nonexistent-skill");

            assertFalse(result.isSelected());
            assertEquals(SkillSelection.SelectionSource.NONE, result.getSource());
        }

        @Test
        @DisplayName("普通消息不匹配")
        void normalMessageNoMatch() {
            SkillSelection result = selector.tryManualSelection("请帮我审查代码");

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("null 输入返回 none")
        void nullInputReturnsNone() {
            SkillSelection result = selector.tryManualSelection(null);

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("空字符串返回 none")
        void emptyInputReturnsNone() {
            SkillSelection result = selector.tryManualSelection("");

            assertFalse(result.isSelected());
        }

        @Test
        @DisplayName("前后空格被 trim")
        void trimWhitespace() {
            SkillSelection result = selector.tryManualSelection("  /code-review  ");

            assertTrue(result.isSelected());
            assertEquals("code-review", result.getSkillName());
        }
    }

    @Nested
    @DisplayName("辅助方法")
    class HelperMethodTests {

        @Test
        @DisplayName("isSlashCommand 正确识别")
        void isSlashCommand() {
            assertTrue(selector.isSlashCommand("/code-review"));
            assertTrue(selector.isSlashCommand("/anything"));
            assertTrue(selector.isSlashCommand("/test args"));
            assertFalse(selector.isSlashCommand("not a command"));
            assertFalse(selector.isSlashCommand(null));
            assertFalse(selector.isSlashCommand(""));
        }

        @Test
        @DisplayName("extractSkillName 正确提取")
        void extractSkillName() {
            assertEquals("code-review", selector.extractSkillName("/code-review"));
            assertEquals("test", selector.extractSkillName("/test args"));
            assertNull(selector.extractSkillName("not a command"));
            assertNull(selector.extractSkillName(null));
        }
    }

    @Nested
    @DisplayName("SkillSelection 模型")
    class SkillSelectionModelTests {

        @Test
        @DisplayName("manual 工厂方法")
        void manualFactory() {
            SkillSelection manual = SkillSelection.manual("test", "args", "/test args");
            assertTrue(manual.isSelected());
            assertEquals(SkillSelection.SelectionSource.MANUAL, manual.getSource());
        }

        @Test
        @DisplayName("auto 工厂方法仍可表达自动来源")
        void autoFactory() {
            SkillSelection auto = SkillSelection.auto("test", "args");
            assertTrue(auto.isSelected());
            assertEquals(SkillSelection.SelectionSource.AUTO, auto.getSource());
        }

        @Test
        @DisplayName("none 工厂方法")
        void noneFactory() {
            SkillSelection none = SkillSelection.none("raw");
            assertFalse(none.isSelected());
            assertEquals(SkillSelection.SelectionSource.NONE, none.getSource());
        }
    }

    private void createSkill(String name, String description) throws IOException {
        Path skillDir = skillsDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: %s
                description: %s
                ---

                # %s
                """.formatted(name, description, name));
    }
}

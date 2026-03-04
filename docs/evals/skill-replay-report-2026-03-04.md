# Skill 回放评测报告（T8）

- 生成时间: 2026-03-04T02:56:44.386Z
- 数据集: `docs/evals/skill-replay-cases-2026-03-04.json`
- 样本数: 24

## 总体命中率（Before vs After）

| 指标 | Before | After | Delta |
|---|---:|---:|---:|
| 命中数 | 6 | 18 | +12 |
| 命中率 | 25.0% | 75.0% | +50.0pp |
| 未命中数 | 18 | 6 | -12 |

## 场景拆分

| 场景 | 样本数 | Before | After | Delta |
|---|---:|---:|---:|---:|
| missing_use_skill | 6 | 0/6 (0.0%) | 0/6 (0.0%) | +0.0pp |
| mixed_calls_correct_skill | 8 | 0/8 (0.0%) | 8/8 (100.0%) | +100.0pp |
| single_use_skill_correct | 6 | 6/6 (100.0%) | 6/6 (100.0%) | +0.0pp |
| typo_skill_name | 4 | 0/4 (0.0%) | 4/4 (100.0%) | +100.0pp |

## 未命中原因分布

| 原因 | Before | After |
|---|---:|---:|
| no_use_skill | 6 | 6 |
| invalid_skill_name | 4 | 0 |
| late_activation | 8 | 0 |
| wrong_skill | 0 | 0 |

## 变化样本

- 改善（Before miss -> After hit）: M01, M02, M03, M04, M05, M06, M07, M08, T01, T02, T03, T04
- 回退（Before hit -> After miss）: (none)

## 假设项说明（枚举约束推断）

- `typo_skill_name` 场景在 After 侧按 `inferredEnumSkill` 进行离线推断，模拟 `use_skill.skill_name` enum 约束后的可选项修正效果。
- 该部分属于离线估计，不代表线上模型在所有语境下必然做出相同选择。

- T01: `frontend_desgin` -> `frontend-design`
- T02: `code-reveiw` -> `code-review`
- T03: `gitcommit` -> `git-commit`
- T04: `chart-visualisation` -> `chart-visualization`

## 评测口径

- `before`：旧行为基线，`mixed_calls_correct_skill` 计为 `late_activation` miss；`skill_name` 自由字符串，拼写错误计为 `invalid_skill_name`。
- `after`：新行为基线，`use_skill` 前置激活，不再计 `late_activation` miss；`skill_name` 通过 enum 限制，拼写错误可按 `inferredEnumSkill` 离线修正。

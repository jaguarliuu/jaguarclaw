#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const MISS_REASONS = ["no_use_skill", "invalid_skill_name", "late_activation", "wrong_skill"];

function parseArgs() {
  const datasetArg = process.argv[2];
  const reportArg = process.argv[3];

  const defaultDataset = path.join("docs", "evals", "skill-replay-cases-2026-03-04.json");
  const datasetPath = datasetArg || defaultDataset;
  const reportPath = reportArg || buildDefaultReportPath(datasetPath);

  return { datasetPath, reportPath };
}

function buildDefaultReportPath(datasetPath) {
  const baseName = path.basename(datasetPath);
  const match = baseName.match(/^skill-replay-cases-(\d{4}-\d{2}-\d{2})\.json$/);
  const date = match ? match[1] : new Date().toISOString().slice(0, 10);
  return path.join(path.dirname(datasetPath), `skill-replay-report-${date}.md`);
}

function loadDataset(datasetPath) {
  const raw = fs.readFileSync(datasetPath, "utf8");
  const data = JSON.parse(raw);
  if (!Array.isArray(data)) {
    throw new Error(`Dataset must be an array: ${datasetPath}`);
  }
  return data;
}

function findUseSkillCall(toolCalls) {
  if (!Array.isArray(toolCalls)) {
    return undefined;
  }
  return toolCalls.find((call) => call && call.name === "use_skill");
}

function evaluateCase(item, mode) {
  const useSkillCall = findUseSkillCall(item.toolCalls);
  const availableSkills = Array.isArray(item.availableSkills) ? item.availableSkills : [];
  const expected = item.expectedSkill;

  if (!useSkillCall) {
    return { hit: false, reason: "no_use_skill" };
  }

  const calledSkill = String(useSkillCall.skill_name || "");

  if (mode === "before") {
    if (!availableSkills.includes(calledSkill)) {
      return { hit: false, reason: "invalid_skill_name" };
    }
    if (calledSkill !== expected) {
      return { hit: false, reason: "wrong_skill" };
    }
    if (item.scenario === "mixed_calls_correct_skill") {
      return { hit: false, reason: "late_activation" };
    }
    return { hit: true, reason: null };
  }

  let resolvedSkill = calledSkill;
  let usedInference = false;

  if (!availableSkills.includes(resolvedSkill)) {
    if (item.inferredEnumSkill && availableSkills.includes(item.inferredEnumSkill)) {
      resolvedSkill = item.inferredEnumSkill;
      usedInference = true;
    } else {
      return { hit: false, reason: "invalid_skill_name" };
    }
  }

  if (resolvedSkill !== expected) {
    return { hit: false, reason: "wrong_skill" };
  }

  return {
    hit: true,
    reason: null,
    usedInference,
    inferredFrom: usedInference ? calledSkill : undefined,
    inferredTo: usedInference ? resolvedSkill : undefined,
  };
}

function initCounters() {
  return {
    total: 0,
    hit: 0,
    miss: 0,
    reasons: MISS_REASONS.reduce((acc, reason) => {
      acc[reason] = 0;
      return acc;
    }, {}),
  };
}

function initScenarioSummary() {
  return {
    before: initCounters(),
    after: initCounters(),
  };
}

function applyResult(counter, result) {
  counter.total += 1;
  if (result.hit) {
    counter.hit += 1;
  } else {
    counter.miss += 1;
    if (!counter.reasons[result.reason]) {
      counter.reasons[result.reason] = 0;
    }
    counter.reasons[result.reason] += 1;
  }
}

function pct(numerator, denominator) {
  if (!denominator) {
    return 0;
  }
  return (numerator / denominator) * 100;
}

function fmtPct(value) {
  return `${value.toFixed(1)}%`;
}

function fmtDelta(beforeValue, afterValue, unit = "") {
  const delta = afterValue - beforeValue;
  const sign = delta >= 0 ? "+" : "";
  return `${sign}${delta.toFixed(1)}${unit}`;
}

function runEvaluation(cases) {
  const overall = {
    before: initCounters(),
    after: initCounters(),
  };
  const byScenario = {};
  const improvedCaseIds = [];
  const regressedCaseIds = [];
  const assumptionCases = [];

  for (const item of cases) {
    const scenario = item.scenario || "unknown";
    if (!byScenario[scenario]) {
      byScenario[scenario] = initScenarioSummary();
    }

    const beforeResult = evaluateCase(item, "before");
    const afterResult = evaluateCase(item, "after");

    applyResult(overall.before, beforeResult);
    applyResult(overall.after, afterResult);
    applyResult(byScenario[scenario].before, beforeResult);
    applyResult(byScenario[scenario].after, afterResult);

    if (!beforeResult.hit && afterResult.hit) {
      improvedCaseIds.push(item.id);
    }
    if (beforeResult.hit && !afterResult.hit) {
      regressedCaseIds.push(item.id);
    }
    if (afterResult.usedInference) {
      assumptionCases.push({
        id: item.id,
        inferredFrom: afterResult.inferredFrom,
        inferredTo: afterResult.inferredTo,
      });
    }
  }

  return {
    overall,
    byScenario,
    improvedCaseIds,
    regressedCaseIds,
    assumptionCases,
  };
}

function buildReport(datasetPath, results) {
  const now = new Date().toISOString();
  const total = results.overall.before.total;
  const beforeHitRate = pct(results.overall.before.hit, total);
  const afterHitRate = pct(results.overall.after.hit, total);
  const deltaHitRate = afterHitRate - beforeHitRate;

  const scenarioRows = Object.entries(results.byScenario)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([scenario, summary]) => {
      const scenarioTotal = summary.before.total;
      const beforeRate = pct(summary.before.hit, scenarioTotal);
      const afterRate = pct(summary.after.hit, scenarioTotal);
      return [
        scenario,
        String(scenarioTotal),
        `${summary.before.hit}/${scenarioTotal} (${fmtPct(beforeRate)})`,
        `${summary.after.hit}/${scenarioTotal} (${fmtPct(afterRate)})`,
        fmtDelta(beforeRate, afterRate, "pp"),
      ];
    });

  const reasonRows = MISS_REASONS.map((reason) => [
    reason,
    String(results.overall.before.reasons[reason] || 0),
    String(results.overall.after.reasons[reason] || 0),
  ]);

  const assumptionList = results.assumptionCases.length
    ? results.assumptionCases
      .map((item) => `- ${item.id}: \`${item.inferredFrom}\` -> \`${item.inferredTo}\``)
      .join("\n")
    : "- 无";

  const improvedList = results.improvedCaseIds.length
    ? results.improvedCaseIds.join(", ")
    : "(none)";
  const regressedList = results.regressedCaseIds.length
    ? results.regressedCaseIds.join(", ")
    : "(none)";

  return [
    "# Skill 回放评测报告（T8）",
    "",
    `- 生成时间: ${now}`,
    `- 数据集: \`${datasetPath}\``,
    `- 样本数: ${total}`,
    "",
    "## 总体命中率（Before vs After）",
    "",
    "| 指标 | Before | After | Delta |",
    "|---|---:|---:|---:|",
    `| 命中数 | ${results.overall.before.hit} | ${results.overall.after.hit} | ${results.overall.after.hit - results.overall.before.hit >= 0 ? "+" : ""}${results.overall.after.hit - results.overall.before.hit} |`,
    `| 命中率 | ${fmtPct(beforeHitRate)} | ${fmtPct(afterHitRate)} | ${(deltaHitRate >= 0 ? "+" : "") + deltaHitRate.toFixed(1)}pp |`,
    `| 未命中数 | ${results.overall.before.miss} | ${results.overall.after.miss} | ${results.overall.after.miss - results.overall.before.miss >= 0 ? "+" : ""}${results.overall.after.miss - results.overall.before.miss} |`,
    "",
    "## 场景拆分",
    "",
    "| 场景 | 样本数 | Before | After | Delta |",
    "|---|---:|---:|---:|---:|",
    ...scenarioRows.map((row) => `| ${row.join(" | ")} |`),
    "",
    "## 未命中原因分布",
    "",
    "| 原因 | Before | After |",
    "|---|---:|---:|",
    ...reasonRows.map((row) => `| ${row.join(" | ")} |`),
    "",
    "## 变化样本",
    "",
    `- 改善（Before miss -> After hit）: ${improvedList}`,
    `- 回退（Before hit -> After miss）: ${regressedList}`,
    "",
    "## 假设项说明（枚举约束推断）",
    "",
    "- `typo_skill_name` 场景在 After 侧按 `inferredEnumSkill` 进行离线推断，模拟 `use_skill.skill_name` enum 约束后的可选项修正效果。",
    "- 该部分属于离线估计，不代表线上模型在所有语境下必然做出相同选择。",
    "",
    assumptionList,
    "",
    "## 评测口径",
    "",
    "- `before`：旧行为基线，`mixed_calls_correct_skill` 计为 `late_activation` miss；`skill_name` 自由字符串，拼写错误计为 `invalid_skill_name`。",
    "- `after`：新行为基线，`use_skill` 前置激活，不再计 `late_activation` miss；`skill_name` 通过 enum 限制，拼写错误可按 `inferredEnumSkill` 离线修正。",
    "",
  ].join("\n");
}

function printSummary(results) {
  const total = results.overall.before.total;
  const beforeRate = pct(results.overall.before.hit, total);
  const afterRate = pct(results.overall.after.hit, total);
  const delta = afterRate - beforeRate;

  console.log("Skill replay evaluation completed.");
  console.log(`Total cases: ${total}`);
  console.log(`Hit rate before: ${beforeRate.toFixed(1)}% (${results.overall.before.hit}/${total})`);
  console.log(`Hit rate after:  ${afterRate.toFixed(1)}% (${results.overall.after.hit}/${total})`);
  console.log(`Delta: ${delta >= 0 ? "+" : ""}${delta.toFixed(1)}pp`);
}

function main() {
  const { datasetPath, reportPath } = parseArgs();
  const cases = loadDataset(datasetPath);
  const results = runEvaluation(cases);
  const report = buildReport(datasetPath, results);

  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, report, "utf8");

  printSummary(results);
  console.log(`Report written: ${reportPath}`);
}

main();

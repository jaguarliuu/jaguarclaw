# Ralph Loop Router Completion Plan

## Goal
Make Ralph loop the real top-level routing path instead of a partial runtime add-on. Route each request into `CHAT`, `DIRECT`, `LIGHT`, `HEAVY`, or blocked outcomes before building the heavy execution plan.

## Design
- Add a structured `TaskRoutingDecision` model as the single routing contract.
- Add a `TaskRouter` that combines:
  - high-confidence hard rules for machine signals only
  - LLM structured routing for open-ended semantic classification
- Move routing to `AgentRunHandler` before strategy resolution.
- Reuse lightweight context building for `DIRECT` / `LIGHT` paths.
- Keep existing `AgentStrategy` flow only for routed `HEAVY` / `EXTERNAL_DEPENDENCY` cases.

## Tasks
1. Add routing decision models and router tests.
2. Implement structured semantic router with safe fallback.
3. Extend runtime/handler tests for `hi`, direct questions, heavy tasks, and blocked flows.
4. Move handler main path to route before strategy plan creation.
5. Verify focused regression suite.

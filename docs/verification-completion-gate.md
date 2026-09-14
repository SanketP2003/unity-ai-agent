# Verification & CompletionGate Guide

The `CompletionGate` is the authoritative tribunal governing step completion and autonomous run finalization.

## Core Principle: LLMs Cannot Self-Certify

In typical AI systems, models hallucinate completion by emitting statements like *"I have finished creating the game."* 
In Autonomous Game Studio:
- The LLM **cannot** declare a run complete.
- The LLM **cannot** mark a validation report as passed.
- Only `CompletionGate` possesses the authority to declare a run `COMPLETED` or approve transitions to Release Candidate state.

---

## The Multi-Stage Verification Matrix

Before any run can transition to `COMPLETED`, `CompletionGate` validates all four tiers:

### 1. Compilation Gate
- `compile_scripts` must return `errorCount == 0`.
- All Roslyn diagnostics must be clean.
- Domain reload must successfully complete without unhandled script exceptions.

### 2. Hierarchy & Structural Gate
- Queries `inspect_scene` to verify that all specified GameObjects exist in the active scene.
- Checks parent-child hierarchies (e.g. Player character must contain required child colliders or camera rigs).
- Confirms transform bounds (objects are not positioned at `(NaN, NaN, NaN)` or clipped into ground planes).

### 3. Component & Script Binding Gate
- Asserts that target components (e.g. `Rigidbody2D`, `BoxCollider2D`, custom player scripts) are properly attached.
- Verifies that serialized field references (e.g. `speed`, `jumpForce`, `hazardTag`) are initialized with valid values.

### 4. Play Mode & Runtime Behavioral Gate
- Enters Play Mode (`START`).
- Executes for a minimum simulation window (typically 3000ms).
- Asserts:
  - 0 unhandled `NullReferenceException` occurrences.
  - 0 runtime error logs.
  - Required behavioral event triggers (e.g. player movement coordinates changed, trigger events fired, victory condition registered).
- Stops Play Mode (`STOP`).

---

## Gate Verdicts & Actions

| Verdict | State | Action Taken |
| :--- | :--- | :--- |
| **PASSED** | `SUCCESS` | Run marked `COMPLETED`. Release pipeline unlocked. |
| **COMPILATION_FAILURE** | `REPAIR_REQUIRED` | Auto-repair diagnostics fed back into `AgentLoop`. |
| **MISSING_COMPONENTS** | `REPAIR_REQUIRED` | Tool calls generated to attach missing scripts/colliders. |
| **RUNTIME_EXCEPTION** | `REPAIR_REQUIRED` | Stack trace and exception parsed for code patching. |
| **MAX_RETRIES_EXCEEDED** | `FAILED` | Run marked `FAILED`. Escalated to human operator with full diagnostic logs. |

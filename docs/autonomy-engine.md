# Autonomy Engine & AgentLoop Mechanics

The Autonomy Engine drives autonomous game creation through structured planning, Roslyn compilation diagnostics, repair iterations, and behavioral verification.

## Architecture & Responsibilities

```text
User Natural-Language Intent
            │
            ▼
 AutonomousRunController (Coordinates Sessions, SSE Streaming, State)
            │
            ▼
        AgentLoop (The Sole LLM Tool-Calling & Reasoning Loop)
            │
      ┌─────┴─────────────────────────┐
      │ Tools:                        │
      │ - execute_script              │
      │ - create_gameobject           │
      │ - inspect_scene               │
      │ - run_diagnostics             │
      │ - compile_scripts             │
      │ - play_mode                   │
      └─────┬─────────────────────────┘
            │
            ▼
     CompletionGate (Authoritative Verification Tribunal)
```

---

## The 7-Stage Autonomous Pipeline

1. **PLANNING**:
   - The LLM ingests user intent and formulates a step-by-step technical plan (game mechanics, required GameObjects, physics layers, C# scripts, UI elements).
2. **BUILDING**:
   - The LLM emits tool calls to create GameObjects, setup transforms, tag layers, and generate C# scripts.
3. **COMPILING**:
   - `compile_scripts` is invoked. Unity Roslyn compiler evaluates all new and modified assets.
4. **DIAGNOSING**:
   - Compiler errors (e.g. CS0246, CS1002, CS1519) are parsed into structured diagnostic reports.
5. **REPAIRING**:
   - If compilation errors exist, `AgentLoop` automatically synthesizes fixes, modifies the scripts, and re-triggers compilation. Up to 5 consecutive repair attempts are permitted before human escalation.
6. **TESTING**:
   - The engine triggers Unity Play Mode (`play_mode start`) for a specified test duration (e.g. 3-5 seconds). Physics simulations run, input stimuli are applied, and runtime exceptions are monitored.
7. **VALIDATING**:
   - `CompletionGate` runs multi-dimensional checks against the live scene hierarchy and component state.

---

## Safety & Invariant Guarantees
- **No LLM Bypass**: No autonomous action can execute outside `AgentLoop`.
- **C# AST Inspection**: Every generated script is scanned by `ScriptSafetyValidator` before writing to Unity assets. Dangerous calls (`System.IO.File.Delete`, `System.Diagnostics.Process`, `System.Net.Sockets`, reflection, raw pointers) are blocked.
- **Circuit Breaker**: Repeated catastrophic failures trip the safety circuit to prevent infinite loops or API exhaustion.

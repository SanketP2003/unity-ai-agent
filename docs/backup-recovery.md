# Backup & Disaster Recovery Guide

Autonomous Game Studio features automated state reconstruction, SQLite write-ahead-logging (WAL), and atomic snapshot backup/restore capabilities.

## Disaster Recovery Architecture

```text
Crash / Power Outage / Interruption
               │
               ▼
   Database Recovery (SQLite WAL)
               │
               ▼
   Interrupted Run Reconstruction
      - WAITING_FOR_UNITY
      - WAITING_FOR_PROVIDER
      - FAILED_WITH_DIAGNOSTICS
               │
               ▼
   Incomplete Release Protection
      - DRAFT status preserved
      - Broken artifacts flagged
```

---

## 1. SQLite Crash Recovery & WAL Mode

The database runs in Write-Ahead Logging (`PRAGMA journal_mode=WAL`) with synchronous normal durability (`PRAGMA synchronous=NORMAL`).
- In the event of a sudden process termination or power failure, uncommitted transactions are cleanly rolled back by the SQLite engine upon startup.
- Database consistency is verified automatically on boot via `PRAGMA integrity_check`.

---

## 2. Interrupted Autonomous Run Reconstruction

If the backend restarts while an autonomous run was active:
1. `FirstRunSetupService` and `AutonomousRunController` scan the database for active runs lacking terminal states.
2. If the run was mid-tool execution with Unity:
   - State transitions to `WAITING_FOR_UNITY` (or cleanly fails if Unity was closed).
3. If the run was waiting for an LLM response:
   - State transitions to `WAITING_FOR_PROVIDER`.
4. Run journal and event history remain preserved, allowing operators to inspect where the interruption occurred.

---

## 3. Incomplete Release Candidate Protection

If a crash occurs while building or registering release artifacts:
- The release remains in `DRAFT` or `VALIDATING` state.
- It **cannot** auto-publish.
- Any incomplete or zero-byte binary is flagged as missing or corrupted upon verification.

---

## 4. Full Backup & Restore Operations

### Creating a Backup
Backups are created via REST API:
```bash
POST /api/product/backups/create
{
  "projectId": "proj_platformer_01",
  "includeAssets": true
}
```
The backup archive contains:
- `manifest.json`: Metadata, timestamp, version, and content hashes.
- `database/`: Database snapshot.
- `assets/`: Unity project assets and C# scripts (excluding temporary Unity `Library/` and secrets).

### Restoring from Backup
```bash
POST /api/product/backups/restore
{
  "backupPath": "backups/backup_proj_platformer_01_20260914.zip",
  "targetProjectId": "proj_platformer_restored"
}
```
All files are extracted with Zip-Slip path traversal protection and SHA-256 manifest verification.

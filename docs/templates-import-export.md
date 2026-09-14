# Templates & Project Import/Export Guide

Autonomous Game Studio enables rapid project scaffolding through pre-built templates and portable `.unitypackage` / `.zip` import/export capabilities.

## Built-In Game Templates

The studio includes three pre-tested, production-ready templates:

### 1. 2D Platformer (`2d-platformer`)
- **Camera Rig**: 2D smooth follow orthographic camera.
- **Physics**: Gravity scale 3.0, custom 2D ground check raycasting.
- **Mechanics**: Horizontal movement, variable jump height, collectable pickup triggers, spike hazard collisions, and UI score counters.

### 2. Top-Down Combat (`topdown-combat`)
- **Camera Rig**: Isometric / top-down perspective with bounds clamping.
- **Physics**: 2D top-down movement with zero gravity.
- **Mechanics**: 8-directional player movement, mouse-aimed projectile shooting, enemy spawn waves, health/damage system, and defeat/victory conditions.

### 3. Third-Person Arena (`third-person-arena`)
- **Camera Rig**: Orbiting third-person spring-arm camera.
- **Physics**: 3D CharacterController or Rigidbody with slope sliding.
- **Mechanics**: 3D movement, jump, melee/ranged attack system, simple enemy pathfinding, and dynamic arena hazard rings.

---

## Project Export Workflow

Projects can be exported as self-contained archive bundles:
```bash
POST /api/product/packages/export
{
  "projectId": "proj_platformer_01",
  "packageName": "RetroPlatformer_v1.0.0"
}
```

### Security & Sanitization during Export
The export engine automatically strips:
- Plaintext `.env` files.
- Credential files (`credentials.json`, `token.json`, `*secret*`).
- Transient Unity cache directories (`Library/`, `Temp/`, `Obj/`, `Logs/`).

The exported package includes:
- Complete C# scripts in `Assets/Scripts/`.
- Prefabs, Materials, and Scenes.
- `package-manifest.json` containing package name, version, timestamp, and asset file list.

---

## Project Import Workflow

```bash
POST /api/product/packages/import
{
  "packagePath": "packages/RetroPlatformer_v1.0.0.zip",
  "targetProjectId": "proj_imported_01"
}
```
During import:
1. Archive entries are verified against Zip-Slip traversal attacks.
2. `package-manifest.json` is validated.
3. Assets are extracted into the project workspace.
4. Unity is signaled to refresh asset databases.

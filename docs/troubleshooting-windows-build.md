# Troubleshooting: Windows / OneDrive / Docker Desktop build failures

## Symptoms (seen on 1.0.11)
- `FileNotFoundException: class path resource [com/neelastack/lakhdatar/service/ProviderOrderRecoveryJob.class] cannot be opened because it does not exist`
- Many tests failing with `NoClassDefFoundError` for classes that *did* compile (`ClientAddressService`, `Enums$OrderStatus`, `AppProperties$Jwt`)
- `There is insufficient memory for the Java Runtime Environment to continue` + `hs_err_pid*.log`

`javac` reported "Compiling 83 source files" successfully, so the source compiles. The failures happen
*after* compilation, while tests run.

## Causes and fixes
1. **Project is inside OneDrive (`...\OneDrive\Desktop\...`).** OneDrive / Defender lock, re-hydrate or
   remove files under `backend\target` while Maven is running, which makes freshly compiled classes vanish.
   **Move the project to a local, non-synced path such as `C:\dev\lakhdatar-events`.**
   (Or exclude `backend\target` from OneDrive/Defender scanning.)
2. **An IDE is building the same `target\` folder** (IntelliJ "Build automatically", VS Code Java auto-build).
   Close the IDE or stop auto-build while running `mvn clean verify`, or run Maven from the IDE's own runner.
3. **Docker Desktop VM only has ~2.9 GB** and the Postgres container + two JVMs + Spring contexts exceeded it.
   Give Docker more memory (WSL2: create `%UserProfile%\.wslconfig` with `[wsl2]` / `memory=6GB`, then `wsl --shutdown`)
   and/or run without Docker: `mvn -B -ntp clean verify -Punit`.
   1.0.12 already halves the footprint (single shared container + Spring context, bounded test JVM).
4. **Stale containers/images:** `docker system prune` frees space; first run pulls ~110 MB of images.

## Commands
```powershell
cd C:\dev\lakhdatar-events\backend
mvn -B -ntp clean verify -Punit    # fast, no Docker
mvn -B -ntp clean verify           # everything, needs Docker running
```
If a JVM crash occurs, attach `target\surefire-reports\*` and `hs_err_pid*.log`.

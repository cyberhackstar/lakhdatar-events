# Neelastack stability version baseline

The event platform deliberately follows the supplied Neelastack frontend/backend baseline for overlapping components. This avoids an unnecessary framework upgrade while the platform is being hardened.

## Frontend

- Node: **24** (same production image and `.nvmrc` baseline used by Neelastack CI)
- Angular runtime: **20.3.30**
- Angular SSR: **20.3.36**
- Angular build/CLI: **20.3.36**
- Angular compiler CLI: **20.3.30**
- Express: **4.22.2**
- RxJS: **7.8.x** under `~7.8.1`
- tslib: **2.8.x** under `^2.8.0`
- zone.js: **0.15.x** under `~0.15.0`
- TypeScript: **5.9.x** under `~5.9.3`
- `qs` override: **6.16.0**

`html5-qrcode` remains at its existing project-specific version because it is a scanner feature dependency and is not part of the Neelastack platform baseline.

## Backend

- Java: **21**
- Spring Boot: **4.0.8**
- JJWT: **0.13.0**
- PostgreSQL JDBC: **42.7.12**
- Testcontainers: **1.21.4**
- Spring MVC uses `spring-boot-starter-webmvc`
- `spring-boot-jackson2` is explicitly present

The other Neelastack libraries (MapStruct, Springdoc, Sentry, Tika, POI, etc.) are intentionally not added to this application when they are not used here. Spring Boot 4.0.8 manages its own transitive Tomcat/Netty/Log4j/Jackson family consistently.

## One-time frontend lockfile refresh

The supplied Neelastack `package.json` baseline and the working Neelastack install were verified independently, but the current event-platform ZIP originally carried an Angular 21 lockfile. The sandbox cannot reach npmjs.org, so a new lockfile could not be generated here without fabricating integrity data.

On a networked developer machine, with Node **24** selected, perform once:

```powershell
./scripts/refresh-frontend-lock.ps1
```

Commit the resulting `frontend/package-lock.json`. After that, keep CI and Docker on `npm ci`; do not replace deterministic installs with `npm install`.

This release intentionally does **not** silently downgrade reproducibility just to hide the stale lockfile.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const pkg = JSON.parse(read('frontend/package.json'));
const lock = JSON.parse(read('frontend/package-lock.json'));
const pom = read('backend/pom.xml');
const angular = JSON.parse(read('frontend/angular.json'));
const styles = read('frontend/src/styles.css');
const index = read('frontend/src/index.html');
const dockerfile = read('frontend/Dockerfile');
const ci = read('.github/workflows/ci.yml');
const version = read('VERSION').trim();

const expected = {
  '@angular/animations':'^20.3.30', '@angular/common':'^20.3.30', '@angular/compiler':'^20.3.30',
  '@angular/core':'^20.3.30', '@angular/forms':'^20.3.30', '@angular/platform-browser':'^20.3.30',
  '@angular/platform-browser-dynamic':'^20.3.30', '@angular/platform-server':'^20.3.30', '@angular/router':'^20.3.30',
  '@angular/ssr':'^20.3.36', express:'^4.22.2', rxjs:'~7.8.1', tslib:'^2.8.0', 'zone.js':'~0.15.0'
};
const problems=[];
for (const [name, spec] of Object.entries(expected)) if (pkg.dependencies[name] !== spec) problems.push(`frontend ${name}: expected ${spec}, got ${pkg.dependencies[name]}`);
if (pkg.devDependencies['@angular/build'] !== '^20.3.36') problems.push('@angular/build baseline mismatch');
if (pkg.devDependencies['@angular/cli'] !== '^20.3.36') problems.push('@angular/cli baseline mismatch');
if (pkg.devDependencies['@angular/compiler-cli'] !== '^20.3.30') problems.push('@angular/compiler-cli baseline mismatch');
if (pkg.devDependencies['@types/express'] !== '^4.17.21') problems.push('@types/express baseline mismatch');
if (pkg.devDependencies['@types/node'] !== '^22.9.0') problems.push('@types/node baseline mismatch');
if (pkg.devDependencies.typescript !== '~5.9.3') problems.push('typescript baseline mismatch');
if (pkg.overrides?.qs !== '6.16.0') problems.push('qs override mismatch');
if (pkg.overrides?.piscina !== '5.3.2') problems.push('piscina security override mismatch');
if (!pom.includes('<artifactId>spring-boot-starter-parent</artifactId>\n    <version>4.0.8</version>')) problems.push('Spring Boot baseline mismatch');
if (!pom.includes('<java.version>21</java.version>')) problems.push('Java baseline mismatch');
if (!pom.includes('<jjwt.version>0.13.0</jjwt.version>')) problems.push('JJWT baseline mismatch');
if (!pom.includes('<testcontainers.version>1.21.4</testcontainers.version>')) problems.push('Testcontainers baseline mismatch');
if (!pom.includes('<artifactId>testcontainers-bom</artifactId>')) problems.push('Testcontainers BOM import missing');
if (!pom.includes('<artifactId>postgresql</artifactId>\n      <scope>test</scope>')) problems.push('Testcontainers PostgreSQL test dependency mismatch');
if (!pom.includes('<artifactId>junit-jupiter</artifactId>\n      <scope>test</scope>')) problems.push('Testcontainers JUnit integration dependency mismatch');
if (!pom.includes('<artifactId>spring-boot-starter-webmvc</artifactId>')) problems.push('Spring MVC starter mismatch');
if (!pom.includes('<artifactId>spring-boot-jackson2</artifactId>')) problems.push('Jackson2 starter missing');
if (!pom.includes('<postgresql.version>42.7.12</postgresql.version>')) problems.push('PostgreSQL driver baseline mismatch');
if (version !== pkg.version) problems.push('release version mismatch between VERSION and package.json');
if (version !== lock.version) problems.push('release version mismatch between VERSION and package-lock.json');
if (lock.packages?.['']?.version !== version) problems.push('release version mismatch in package-lock root package');
if (!pom.includes(`<artifactId>lakhdatar-events</artifactId>\n  <version>${version}</version>`)) problems.push('release version mismatch in Maven project');
if (angular.projects?.['lakhdatar-events-frontend']?.architect?.build?.builder !== '@angular/build:application') problems.push('Angular application builder mismatch');
if (!styles.includes('input,select,textarea{font-size:16px') && !styles.includes('input, select, textarea { font-size: 16px')) problems.push('iOS input zoom guard missing');
if (!index.includes('name="viewport"') || !index.includes('width=device-width')) problems.push('viewport metadata missing');
if (!dockerfile.includes('node:24-alpine3.22')) problems.push('frontend Docker Node baseline mismatch');
if (!ci.includes('node-version-file: .nvmrc')) problems.push('CI Node baseline file mismatch');
if (!ci.includes('fetch-depth: 0')) problems.push('CI secret-scan history depth mismatch');
if (ci.includes('actions/dependency-review-action@')) problems.push('Unsupported dependency-review job must not run until GitHub dependency graph is enabled');
if (read('.nvmrc').trim() !== '24') problems.push('Node runtime baseline mismatch');
if (problems.length) { console.error('Neelastack baseline verification FAILED:'); for (const p of problems) console.error(`- ${p}`); process.exit(1); }
console.log('Neelastack stability baseline: PASS');

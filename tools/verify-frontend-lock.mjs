import fs from 'node:fs';

const root = new URL('../frontend/', import.meta.url);
const pkg = JSON.parse(fs.readFileSync(new URL('package.json', root), 'utf8'));
const lockPath = new URL('package-lock.json', root);
if (!fs.existsSync(lockPath)) {
  console.error('Frontend dependency verification: FAIL');
  console.error('- frontend/package-lock.json is missing; run `npm run bootstrap:dependencies` on a networked developer/CI machine');
  process.exit(1);
}
const lock = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
const rootPkg = lock.packages?.[''];
const angular = JSON.parse(fs.readFileSync(new URL('angular.json', root), 'utf8'));
const failures = [];

const expectedVersion = fs.readFileSync(new URL('../VERSION', import.meta.url), 'utf8').trim();
if (pkg.version !== expectedVersion) failures.push(`frontend/package.json version ${pkg.version} != ${expectedVersion}`);
if (lock.version !== expectedVersion || rootPkg?.version !== expectedVersion) failures.push(`package-lock root version is not ${expectedVersion}`);
const angularPackages = ['@angular/common','@angular/compiler','@angular/core','@angular/forms','@angular/platform-browser','@angular/platform-server','@angular/router','@angular/ssr'];
for (const name of angularPackages) {
  const want = name === '@angular/ssr' ? '20.3.39' : '20.3.33';
  if (pkg.dependencies?.[name] !== want) failures.push(`${name} must be pinned to ${want}`);
}
if (pkg.overrides?.tinypool !== '2.2.0') failures.push('tinypool override must be 2.2.0 (fixes critical GHSA-5gmw-xhrv-c9v3 / GHSA-85c8-ppgw-ccpr)');
const tinypoolLock = lock.packages?.['node_modules/tinypool'];
if (!tinypoolLock || tinypoolLock.version !== '2.2.0') failures.push(`lockfile tinypool ${tinypoolLock?.version} != 2.2.0`);
const angularDevPackages = ['@angular/build','@angular/cli','@angular/compiler-cli'];
if (pkg.devDependencies?.['@types/node'] !== '22.20.5') failures.push(`@types/node must be pinned to 22.20.5, got ${pkg.devDependencies?.['@types/node']}`);
for (const name of angularDevPackages) {
  if (pkg.devDependencies?.[name] !== '20.3.33') failures.push(`${name} must be pinned to 20.3.33`);
}
if (pkg.dependencies?.['@angular/animations']) failures.push('@angular/animations must not be a direct dependency');
if (pkg.dependencies?.['@angular/platform-browser-dynamic']) failures.push('@angular/platform-browser-dynamic must not be a direct dependency');
if (lock.packages?.['node_modules/@angular/animations']) failures.push('lockfile still installs @angular/animations');
if (lock.packages?.['node_modules/@angular/platform-browser-dynamic']) failures.push('lockfile still installs @angular/platform-browser-dynamic');


const hasown = lock.packages?.['node_modules/hasown'];
if (!hasown) failures.push('node_modules/hasown entry is missing');
else {
  if (hasown.version !== '2.0.4') failures.push(`hasown version ${hasown.version} != 2.0.4`);
  if (hasown.resolved !== 'https://registry.npmjs.org/hasown/-/hasown-2.0.4.tgz') failures.push('hasown tarball is not the 2.0.4 registry artifact');
}
const hasownOverride = pkg.overrides?.hasown;
if (hasownOverride !== '2.0.4') failures.push(`hasown override ${hasownOverride} != 2.0.4`);
const coreHasown = lock.packages?.['node_modules/is-core-module']?.dependencies?.hasown;
if (coreHasown !== '^2.0.4') failures.push(`is-core-module hasown range ${coreHasown} != ^2.0.4`);
const inherits = lock.packages?.['node_modules/inherits'];
if (!inherits) failures.push('node_modules/inherits entry is missing');
else {
  if (inherits.version !== '2.0.4') failures.push(`inherits version ${inherits.version} != 2.0.4`);
  if (inherits.resolved !== 'https://registry.npmjs.org/inherits/-/inherits-2.0.4.tgz') failures.push('inherits tarball is not the 2.0.4 registry artifact');
}
const override = pkg.overrides?.['http-errors@2.0.1']?.inherits;
if (override !== '2.0.4') failures.push(`http-errors override ${override} != 2.0.4`);
// npm does not need to serialize root overrides into package-lock.json; the lock is validated by the resolved package entry below.

const forbiddenTestPackages = ['karma', 'karma-chrome-launcher', 'karma-coverage', 'karma-jasmine', 'karma-jasmine-html-reporter', '@types/jasmine', 'jasmine-core'];
for (const name of forbiddenTestPackages) {
  if (pkg.devDependencies?.[name]) failures.push(`${name} must be removed from frontend devDependencies`);
  if (Object.keys(lock.packages || {}).some(path => path === `node_modules/${name}` || path.startsWith(`node_modules/${name}/`))) {
    failures.push(`lockfile still installs deprecated test dependency ${name}`);
  }
}
if (pkg.devDependencies?.vitest !== '3.2.7') failures.push(`vitest devDependency must be 3.2.7, got ${pkg.devDependencies?.vitest}`);
if (pkg.devDependencies?.jsdom !== '29.1.1') failures.push(`jsdom devDependency must be 29.1.1, got ${pkg.devDependencies?.jsdom}`);
const vitestLock = lock.packages?.['node_modules/vitest'];
if (!vitestLock) failures.push('lockfile is missing node_modules/vitest; regenerate frontend/package-lock.json from package.json');
const jsdomLock = lock.packages?.['node_modules/jsdom'];
if (!jsdomLock) failures.push('lockfile is missing node_modules/jsdom; regenerate frontend/package-lock.json from package.json');
if (angular.projects?.['lakhdatar-events-frontend']?.architect?.test?.options?.runner !== 'vitest') failures.push('Angular unit-test runner must be Vitest');
const testOptions = angular.projects?.['lakhdatar-events-frontend']?.architect?.test?.options;
if (testOptions?.buildTarget !== 'lakhdatar-events-frontend:build:testing') failures.push('Angular unit-test target must use the isolated testing build configuration');
const testingBuild = angular.projects?.['lakhdatar-events-frontend']?.architect?.build?.configurations?.testing;
if (testingBuild?.outputMode !== 'static') failures.push('Angular testing build must use static output mode');
if (testingBuild?.ssr !== false) failures.push('Angular testing build must disable SSR');

const mcpOverride = pkg.overrides?.['@modelcontextprotocol/sdk'];
if (mcpOverride !== '1.31.0') failures.push(`@modelcontextprotocol/sdk override must be 1.31.0, got ${mcpOverride}`);
const mcpLock = lock.packages?.['node_modules/@modelcontextprotocol/sdk'];
if (mcpLock && mcpLock.version !== '1.31.0') failures.push(`lockfile @modelcontextprotocol/sdk version ${mcpLock.version} != 1.31.0`);
const rawLock = fs.readFileSync(new URL('package-lock.json', root), 'utf8');
if (rawLock.includes('inherits-2.0.5.tgz')) failures.push('stale inherits-2.0.5.tgz tarball reference remains');
if (rawLock.includes('hasown-2.0.5.tgz')) failures.push('stale hasown-2.0.5.tgz tarball reference remains');
if (rawLock.includes('http-errors-2.0.2.tgz')) failures.push('stale http-errors-2.0.2.tgz tarball reference remains');
if (rawLock.includes('void-elements-2.0.2.tgz')) failures.push('stale void-elements-2.0.2.tgz tarball reference remains');
const colorette = lock.packages?.['node_modules/colorette'];
if (!colorette) failures.push('node_modules/colorette entry is missing');
else {
  if (colorette.version !== '2.0.20') failures.push(`colorette version ${colorette.version} != 2.0.20`);
  if (colorette.resolved !== 'https://registry.npmjs.org/colorette/-/colorette-2.0.20.tgz') failures.push('colorette tarball is not the 2.0.20 registry artifact');
}
const listr2Colorette = lock.packages?.['node_modules/listr2']?.dependencies?.colorette;
if (listr2Colorette !== '^2.0.20') failures.push(`listr2 colorette range ${listr2Colorette} != ^2.0.20`);
if (rawLock.includes('colorette-2.0.21.tgz')) failures.push('unavailable colorette-2.0.21.tgz tarball reference remains');

const angularLockVersions = angularPackages.concat(angularDevPackages).map(name => [name, lock.packages?.[`node_modules/${name}`]?.version]);
for (const [name, version] of angularLockVersions) {
  const want = name === '@angular/ssr' ? '20.3.39' : '20.3.33';
  if (version !== want) failures.push(`lockfile ${name} version ${version} != ${want}`);
}
for (const [path, entry] of Object.entries(lock.packages || {})) {
  if (path === 'node_modules/braces' || path.endsWith('/braces')) {
    const version = entry?.version;
    const parts = String(version || '').split('.').map(x => Number.parseInt(x, 10));
    const vulnerable = parts.length >= 3 && parts.every(Number.isFinite) && (parts[0] < 3 || (parts[0] === 3 && parts[1] === 0 && parts[2] <= 3));
    if (vulnerable) failures.push(`vulnerable braces ${version} remains at ${path}`);
  }
  if (path === 'node_modules/karma' || path.endsWith('/karma')) failures.push(`legacy Karma remains at ${path}`);
}
const mcpLockStrict = lock.packages?.['node_modules/@modelcontextprotocol/sdk'];
if (!mcpLockStrict || mcpLockStrict.version !== '1.31.0') failures.push('lockfile must contain @modelcontextprotocol/sdk 1.31.0');
if (failures.length) {
  console.error('Frontend dependency verification: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}
console.log('Frontend dependency verification: PASS');

import fs from 'node:fs';

const root = new URL('../frontend/', import.meta.url);
const pkg = JSON.parse(fs.readFileSync(new URL('package.json', root), 'utf8'));
const lock = JSON.parse(fs.readFileSync(new URL('package-lock.json', root), 'utf8'));
const rootPkg = lock.packages?.[''];
const failures = [];

const expectedVersion = fs.readFileSync(new URL('../VERSION', import.meta.url), 'utf8').trim();
if (pkg.version !== expectedVersion) failures.push(`frontend/package.json version ${pkg.version} != ${expectedVersion}`);
if (lock.version !== expectedVersion || rootPkg?.version !== expectedVersion) failures.push(`package-lock root version is not ${expectedVersion}`);
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

if (failures.length) {
  console.error('Frontend dependency verification: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}
console.log('Frontend dependency verification: PASS');

import fs from 'node:fs';

const root = new URL('../frontend/', import.meta.url);
const pkg = JSON.parse(fs.readFileSync(new URL('package.json', root), 'utf8'));
const lock = JSON.parse(fs.readFileSync(new URL('package-lock.json', root), 'utf8'));
const rootPkg = lock.packages?.[''];
const failures = [];

if (pkg.version !== '2.0.6') failures.push(`frontend/package.json version ${pkg.version} != 2.0.6`);
if (lock.version !== '2.0.6' || rootPkg?.version !== '2.0.6') failures.push('package-lock root version is not 2.0.6');
if (pkg.dependencies?.['@angular/animations']) failures.push('@angular/animations must not be a direct dependency');
if (pkg.dependencies?.['@angular/platform-browser-dynamic']) failures.push('@angular/platform-browser-dynamic must not be a direct dependency');
if (lock.packages?.['node_modules/@angular/animations']) failures.push('lockfile still installs @angular/animations');
if (lock.packages?.['node_modules/@angular/platform-browser-dynamic']) failures.push('lockfile still installs @angular/platform-browser-dynamic');

const inherits = lock.packages?.['node_modules/inherits'];
if (!inherits) failures.push('node_modules/inherits entry is missing');
else {
  if (inherits.version !== '2.0.4') failures.push(`inherits version ${inherits.version} != 2.0.4`);
  if (inherits.resolved !== 'https://registry.npmjs.org/inherits/-/inherits-2.0.4.tgz') failures.push('inherits tarball is not the 2.0.4 registry artifact');
}
const override = pkg.overrides?.['http-errors@2.0.1']?.inherits;
if (override !== '2.0.4') failures.push(`http-errors override ${override} != 2.0.4`);
if (rootPkg?.overrides?.['http-errors@2.0.1']?.inherits !== '2.0.4') failures.push('lockfile root override missing');
const rawLock = fs.readFileSync(new URL('package-lock.json', root), 'utf8');
if (rawLock.includes('inherits-2.0.5.tgz')) failures.push('stale inherits-2.0.5.tgz tarball reference remains');

if (failures.length) {
  console.error('Frontend dependency verification: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}
console.log('Frontend dependency verification: PASS');

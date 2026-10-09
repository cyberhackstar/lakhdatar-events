import fs from 'node:fs';
import path from 'node:path';
import { copyFile, rm } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import process from 'node:process';

const root = process.cwd();
const lock = 'package-lock.json';
const backup = 'package-lock.json.enterprise-backup';

if (!fs.existsSync('package.json')) {
  console.error('Enterprise dependency bootstrap: run this command from frontend/.');
  process.exit(2);
}

const node = process.versions.node.split('.').map(Number);
const supported = (node[0] === 22 && node[1] >= 19) || node[0] === 24;
if (!supported) {
  console.error(`Enterprise dependency bootstrap: unsupported Node.js ${process.versions.node}. Use Node 22.19+ or Node 24.x.`);
  process.exit(2);
}

function resolveNpmRunner() {
  // On Windows, npm.cmd is a batch file. Node's spawnSync/execFile family cannot
  // reliably launch .cmd files with shell:false; this was the v2.0.34 failure.
  // Prefer npm's own JS entry point so npm is launched by Node directly.
  const npmExecPath = process.env.npm_execpath;
  if (npmExecPath && fs.existsSync(npmExecPath)) {
    return { file: process.execPath, prefix: [npmExecPath], shell: false };
  }

  const localCli = path.join(path.dirname(process.execPath), 'node_modules', 'npm', 'bin', 'npm-cli.js');
  if (fs.existsSync(localCli)) {
    return { file: process.execPath, prefix: [localCli], shell: false };
  }

  if (process.platform !== 'win32') {
    return { file: 'npm', prefix: [], shell: false };
  }

  // Last-resort Windows fallback. The command is fixed and all arguments below
  // are repository-authored constants; no user-provided shell fragments are used.
  return { file: process.env.ComSpec || 'cmd.exe', prefix: ['/d', '/s', '/c'], shell: false, cmdFallback: true };
}

const runner = resolveNpmRunner();

function quoteCmdArg(value) {
  // Arguments used by this script contain no shell metacharacters, but quoting
  // spaces makes this fallback robust for future changes.
  if (!/[\s"]/.test(value)) return value;
  return `"${value.replace(/"/g, '\\"')}"`;
}

function run(label, args) {
  console.log(`\n==> ${label}`);
  let file = runner.file;
  let spawnArgs;
  if (runner.cmdFallback) {
    spawnArgs = [...runner.prefix, ['npm', ...args].map(quoteCmdArg).join(' ')];
  } else {
    spawnArgs = [...runner.prefix, ...args];
  }

  const result = spawnSync(file, spawnArgs, {
    cwd: root,
    stdio: ['inherit', 'pipe', 'pipe'],
    encoding: 'utf8',
    windowsHide: false,
    shell: runner.shell,
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error) throw new Error(`${label}: ${result.error.message}`);
  if (result.status !== 0) throw new Error(`${label} failed with npm exit code ${result.status}`);
}

const hadLock = fs.existsSync(lock);
if (hadLock) await copyFile(lock, backup);

try {
  let resolved = false;
  try {
    run('Resolve frontend package-lock.json with strict peer resolution', [
      'install',
      '--package-lock-only',
      '--include=dev',
      '--ignore-scripts',
      '--no-audit',
      '--no-fund',
      '--prefer-online',
      '--loglevel=warn',
    ]);
    resolved = true;
  } catch (error) {
    console.error(`\nLock-only resolution failed: ${error.message}`);
    console.error('Retrying with a normal strict npm install; no force/legacy-peer-deps flags are used.');
    run('Recover dependency tree and generate package-lock.json', [
      'install',
      '--include=dev',
      '--ignore-scripts',
      '--no-audit',
      '--no-fund',
      '--prefer-online',
      '--loglevel=warn',
    ]);
    resolved = true;
  }

  if (!resolved || !fs.existsSync(lock)) throw new Error('npm completed without producing frontend/package-lock.json');

  await rm('node_modules', { recursive: true, force: true });
  run('Clean install from the generated lockfile', [
    'ci',
    '--include=dev',
    '--ignore-scripts',
    '--no-audit',
    '--no-fund',
  ]);

  run('Run frontend dependency contract', ['run', 'verify:dependencies']);
  run('Audit all dependencies for HIGH/CRITICAL', ['run', 'audit:high']);
  run('Audit production/runtime dependencies for HIGH/CRITICAL', ['run', 'audit:runtime']);
  console.log('\nEnterprise frontend dependency bootstrap: PASS');
} catch (error) {
  if (hadLock && fs.existsSync(backup)) {
    await copyFile(backup, lock);
    console.error('\nPrevious package-lock.json restored after dependency bootstrap failure.');
  } else if (!hadLock) {
    await rm(lock, { force: true });
    console.error('\nNo previous lockfile existed; no partial lockfile was retained.');
  }
  console.error(`\nEnterprise frontend dependency bootstrap: FAIL\n${error.message}`);
  process.exit(1);
} finally {
  await rm(backup, { force: true });
}

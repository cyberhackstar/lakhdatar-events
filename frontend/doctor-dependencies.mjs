import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import process from "node:process";

const pkg = JSON.parse(fs.readFileSync("package.json", "utf8"));
const failures = [];

function resolveNpmRunner() {
  const npmExecPath = process.env.npm_execpath;
  if (npmExecPath && fs.existsSync(npmExecPath)) return { file: process.execPath, prefix: [npmExecPath] };
  const localCli = path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js");
  if (fs.existsSync(localCli)) return { file: process.execPath, prefix: [localCli] };
  if (process.platform !== "win32") return { file: "npm", prefix: [] };
  return { file: process.env.ComSpec || "cmd.exe", prefix: ["/d", "/s", "/c", "npm --version"] , commandFallback: true };
}

const versions = () => {
  const runner = resolveNpmRunner();
  if (runner.commandFallback) return execFileSync(runner.file, runner.prefix, { encoding: "utf8" }).trim();
  return execFileSync(runner.file, [...runner.prefix, "--version"], { encoding: "utf8" }).trim();
};

if (!fs.existsSync("package-lock.json")) failures.push("package-lock.json is missing; run npm run bootstrap:dependencies");
const nodeParts = process.versions.node.split(".").map(Number);
if (!((nodeParts[0] === 22 && nodeParts[1] >= 19) || nodeParts[0] === 24)) failures.push(`Node.js must be 22.19+ or 24.x, found ${process.versions.node}`);
if (pkg.version !== "2.0.35") failures.push(`frontend package version must be 2.0.35, found ${pkg.version}`);
if (pkg.devDependencies?.vitest !== "3.2.7") failures.push("Vitest must be pinned to 3.2.7");
if (pkg.devDependencies?.jsdom !== "29.1.1") failures.push("JSDOM must be pinned to 29.1.1");
if (pkg.overrides?.["@modelcontextprotocol/sdk"] !== "1.31.0") failures.push("MCP SDK override must be 1.31.0");
if (pkg.devDependencies?.karma || pkg.devDependencies?.jasmine) failures.push("Legacy Karma/Jasmine test dependencies must not be declared");

if (failures.length) {
  console.error("Frontend dependency doctor: FAIL");
  for (const f of failures) console.error(`- ${f}`);
  process.exit(1);
}
console.log(`Frontend dependency doctor: PASS (Node ${process.versions.node}, npm ${versions()})`);

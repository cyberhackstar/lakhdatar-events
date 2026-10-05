const { expect } = require('@playwright/test');

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required for enterprise E2E`);
  return value;
}

function bearer(name) {
  const value = required(name);
  return value.startsWith('Bearer ') ? value : `Bearer ${value}`;
}

function jsonHeaders(name) {
  return { Authorization: bearer(name), 'Content-Type': 'application/json' };
}

async function expectJsonOk(response, label) {
  expect(response.ok(), `${label}: ${response.status()}`).toBeTruthy();
  const body = await response.json();
  return body;
}

module.exports = { required, bearer, jsonHeaders, expectJsonOk };

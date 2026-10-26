import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = await readFile(new URL('../infra/k6/options.js', import.meta.url), 'utf8');
const { loadConfig } = await import(`data:text/javascript,${encodeURIComponent(source)}`);

test('defaults run a small local health check', () => {
  const config = loadConfig({});
  assert.equal(config.url, 'http://host.docker.internal:8080/actuator/health');
  assert.equal(config.options.vus, 1);
  assert.equal(config.options.duration, '30s');
  assert.equal(config.params.redirects, 0);
  assert.equal(config.options.discardResponseBodies, true);
  assert.equal(config.params.headers.Authorization, undefined);
});

test('an authenticated read uses the supplied local token', () => {
  const config = loadConfig({
    BASE_URL: 'http://localhost:8081/', TARGET_PATH: '/api/v1/users/me',
    ACCESS_TOKEN: 'test-only-token', VUS: '3', DURATION: '1m',
  });
  assert.equal(config.url, 'http://localhost:8081/api/v1/users/me');
  assert.equal(config.params.headers.Authorization, 'Bearer test-only-token');
  assert.equal(config.options.vus, 3);
  assert.equal(config.options.duration, '1m');
});

test('public targets and URL credentials are rejected', () => {
  for (const BASE_URL of ['https://example.com', 'http://localhost.evil:8080',
    'http://user:password@localhost:8080', 'http://localhost:8080/path',
    'http://localhost:8080?token=secret']) {
    assert.throws(() => loadConfig({ BASE_URL }), /BASE_URL/);
  }
});

test('paths cannot change the target or record sensitive query strings', () => {
  for (const TARGET_PATH of ['https://example.com', '//example.com', '/x?token=secret',
    '/x#fragment', '/x\\evil', '/x\n']) {
    assert.throws(() => loadConfig({ TARGET_PATH }), /TARGET_PATH/);
  }
});

test('invalid or unexpectedly large load settings are rejected', () => {
  for (const VUS of ['0', '-1', '1.5', 'NaN', '51', '']) {
    assert.throws(() => loadConfig({ VUS }), /VUS/);
  }
  for (const DURATION of ['0s', '-1s', 'forever', '301s', '6m', '']) {
    assert.throws(() => loadConfig({ DURATION }), /DURATION/);
  }
});

test('sensitive URLs and tokens do not become metric tags', () => {
  const config = loadConfig({ ACCESS_TOKEN: 'test-only-token', TARGET_PATH: '/api/v1/users/me' });
  assert.ok(!config.options.systemTags.includes('url'));
  assert.deepEqual(config.params.tags, { name: 'local-read' });
  assert.ok(!JSON.stringify(config.options).includes('test-only-token'));
});

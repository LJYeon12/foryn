// k6 실행기와 오프라인 Node 테스트가 공유하는 순수 설정.
export function loadConfig(env) {
  const baseUrl = (env.BASE_URL ?? 'http://host.docker.internal:8080').replace(/\/$/, '');
  if (!/^http:\/\/(?:host\.docker\.internal|localhost|127\.0\.0\.1):[0-9]{1,5}$/.test(baseUrl)) {
    throw new Error('BASE_URL must be a local HTTP origin with a port');
  }
  const port = Number(baseUrl.slice(baseUrl.lastIndexOf(':') + 1));
  if (port < 1 || port > 65535) {
    throw new Error('BASE_URL port must be between 1 and 65535');
  }
  const path = env.TARGET_PATH ?? '/actuator/health';
  if (!/^\/(?!\/)[A-Za-z0-9/_-]*$/.test(path)) {
    throw new Error('TARGET_PATH must be a local path without a query or fragment');
  }
  const vusValue = env.VUS ?? '1';
  const vus = Number(vusValue);
  if (!/^[0-9]+$/.test(vusValue) || !Number.isInteger(vus) || vus < 1 || vus > 50) {
    throw new Error('VUS must be an integer between 1 and 50');
  }
  const duration = env.DURATION ?? '30s';
  const match = /^([1-9][0-9]*)(s|m)$/.exec(duration);
  const seconds = match ? Number(match[1]) * (match[2] === 'm' ? 60 : 1) : 0;
  if (seconds < 1 || seconds > 300) {
    throw new Error('DURATION must be between 1s and 5m');
  }
  const headers = { Accept: 'application/json' };
  if (env.ACCESS_TOKEN) {
    headers.Authorization = `Bearer ${env.ACCESS_TOKEN}`;
  }
  return {
    url: baseUrl + path,
    params: { headers, redirects: 0, timeout: '10s', tags: { name: 'local-read' } },
    options: {
      vus,
      duration,
      discardResponseBodies: true,
      systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
      tags: { testid: env.TEST_ID || 'local' },
      thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
      },
    },
  };
}

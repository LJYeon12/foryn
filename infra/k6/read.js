import http from 'k6/http';
import { check, sleep } from 'k6';
import { loadConfig } from './options.js';

const config = loadConfig(__ENV);
export const options = config.options;

export default function () {
  const response = http.get(config.url, config.params);
  check(response, { 'HTTP 200': (result) => result.status === 200 });
  sleep(1);
}

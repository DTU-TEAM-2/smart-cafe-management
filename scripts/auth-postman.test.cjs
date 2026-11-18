// Assertion-integrity tests only. Live HTTP/MySQL evidence is recorded separately.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const collection = require('../docs/api/Smart_Cafe_DT2-2_Auth.postman_collection.json');
const newman = require(process.env.NEWMAN_MODULE || 'newman');

async function runItem(number, status, body) {
  const server = http.createServer((req, res) => {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(body));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const item = collection.item[number - 1];
    const environment = { values: Object.entries({
      baseUrl: `http://127.0.0.1:${server.address().port}`, staffUsername: 'postman_fixture',
      adminUsername: 'postman_admin', adminEmail: 'postman_admin@example.test',
      testOtpCode: '123456', challengeId: 'fixture-challenge', resetToken: 'fixture-grant',
    }).map(([key, value]) => ({ key, value, enabled: true })) };
    return await new Promise((resolve, reject) => newman.run({
      collection: { ...collection, item: [item] }, environment, reporters: [], silent: true,
      timeoutRequest: 2000,
    }, (error, summary) => error ? reject(error) : resolve(summary.run.failures.length)));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
}

for (const [number, status, body, reason] of [
  [1, 200, { token: '', user: { username: 'postman_admin', role: 'ADMIN' } }, 'empty admin token'],
  [2, 200, { token: '', user: { username: 'postman_fixture', role: 'STAFF' } }, 'empty staff token'],
  [3, 200, { token: '', user: { username: 'postman_admin', role: 'ADMIN', email: 'postman_admin@example.test' } }, 'empty email-login token'],
  [13, 200, { message: 'fixture', challengeId: '', expiresInSeconds: 600 }, 'empty challenge ID'],
  [19, 400, { code: 'OTP_ERROR' }, 'wrong OTP'],
  [19, 200, { purpose: 'RESET', token: '' }, 'empty reset token'],
  [20, 200, { valid: false }, 'invalid reset token'],
  [21, 401, { code: 'INVALID_TOKEN' }, 'rejected password reset'],
  [22, 401, { code: 'UNAUTHORIZED' }, 'rejected new-password login'],
  [22, 200, { token: '', user: { username: 'postman_fixture' } }, 'empty access token'],
  [22, 200, { token: 'fixture-access', user: { username: 'another_fixture' } }, 'wrong account'],
  [23, 503, { message: 'unavailable' }, 'failed logout acknowledgement'],
]) {
  test(`happy request ${number} fails for ${reason}`, async () => {
    assert.ok(await runItem(number, status, body) > 0, 'Newman must report a failed assertion');
  });
}

for (const [number, body] of [
  [1, { token: 'fixture-access', user: { username: 'postman_admin', role: 'ADMIN' } }],
  [2, { token: 'fixture-access', user: { username: 'postman_fixture', role: 'STAFF' } }],
  [3, { token: 'fixture-access', user: { username: 'postman_admin', role: 'ADMIN', email: 'postman_admin@example.test' } }],
  [13, { message: 'fixture', challengeId: 'fixture-challenge', expiresInSeconds: 600 }],
  [19, { purpose: 'RESET', token: 'fixture-grant' }],
  [20, { valid: true }],
  [21, { message: 'Mật khẩu cập nhật thành công' }],
  [22, { token: 'fixture-access', user: { username: 'postman_fixture' } }],
  [23, { message: 'Đăng xuất thành công' }],
]) {
  test(`happy request ${number} accepts its valid response`, async () => {
    assert.equal(await runItem(number, 200, body), 0);
  });
}

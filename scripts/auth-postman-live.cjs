// Opt-in, loopback-only HTTP/MySQL/SMTP acceptance harness. No raw Newman exports.
const assert = require('node:assert/strict');
const path = require('node:path');
const net = require('node:net');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');
const { once } = require('node:events');
const newman = require(process.env.NEWMAN_MODULE || 'newman');
const collection = require('../docs/api/Smart_Cafe_DT2-2_Auth.postman_collection.json');
const profile = process.env.AUTH_TEST_PROFILE || 'auth-http-test';
assert.ok(['auth-http-test', 'dev', 'prod'].includes(profile));

const db = new URL((process.env.TEST_DB_URL || '').replace(/^jdbc:/, ''));
assert.equal(db.protocol, 'mysql:');
assert.ok(['127.0.0.1', 'localhost'].includes(db.hostname), 'Loopback test DB required');
const schema = db.pathname.slice(1);
assert.match(schema, /^\w+_test$/);
assert.ok(!db.username && !db.password);
assert.ok(process.env.TEST_DB_USERNAME && process.env.TEST_DB_PASSWORD && process.env.BCRYPT_TEST_CLASSPATH);
const mysqlBin = process.env.MYSQL_BIN || 'mysql';
const secret = crypto.randomBytes(48).toString('hex');
const prefix = `pm_auth_${crypto.randomBytes(5).toString('hex')}`;
const adminUsername = `${prefix}_admin`, staffUsername = `${prefix}_staff`;
const adminEmail = `${adminUsername}@example.test`, staffEmail = `${staffUsername}@example.test`;
const unknownEmail = `${prefix}_unknown@example.test`;
const adminPassword = `Aa1!${crypto.randomBytes(16).toString('hex')}`;
const staffPassword = `Bb2!${crypto.randomBytes(16).toString('hex')}`;
const newStaffPassword = `Cc3!${crypto.randomBytes(16).toString('hex')}`;
const emails = [adminEmail, staffEmail, unknownEmail];
const mailbox = new Map();
const sockets = new Set();
const results = [];
let child, baseUrl;

function sqlRaw(query) {
  const r = spawnSync(mysqlBin, ['-h', db.hostname, '-P', db.port || '3306', '-u', process.env.TEST_DB_USERNAME,
    `--database=${schema}`, '--batch', '--skip-column-names', '-e', `SET time_zone = '+00:00'; ${query}`], {
    env: { ...process.env, MYSQL_PWD: process.env.TEST_DB_PASSWORD }, encoding: 'utf8', windowsHide: true,
  });
  assert.equal(r.status, 0, 'Test SQL operation must succeed');
  return r.stdout.trim();
}
function sql(query) {
  assert.equal(sqlRaw('SELECT DATABASE()'), schema); // fail closed before every fixture DML
  return sqlRaw(query);
}
function quote(value) { return `'${value.replace(/'/g, "''")}'`; }
function limitKey(email) { return crypto.createHmac('sha256', secret).update(`LIMIT:${email}`).digest('hex'); }
function hash(password) {
  const r = spawnSync('java', ['--class-path', process.env.BCRYPT_TEST_CLASSPATH,
    path.join(__dirname, 'AuthFixturePassword.java')], { input: password, encoding: 'utf8', windowsHide: true });
  assert.equal(r.status, 0, 'Fixture BCrypt generation must succeed');
  assert.match(r.stdout, /^\$2[aby]\$/);
  return r.stdout;
}

function decodeSubject(raw) {
  const header = raw.split('\r\n\r\n')[0].replace(/\r\n[ \t]+/g, ' ');
  return (header.match(/^Subject:\s*(.*)$/mi)?.[1] || '').replace(/=\?[^?]+\?([BQ])\?([^?]+)\?=/gi,
    (_, encoding, value) => encoding.toUpperCase() === 'B' ? Buffer.from(value, 'base64').toString('utf8') :
      Buffer.from(value.replace(/_/g, ' ').replace(/=([0-9a-f]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16))), 'binary').toString('utf8'));
}
const smtp = net.createServer(socket => {
  sockets.add(socket); socket.on('close', () => sockets.delete(socket)); socket.on('error', () => {});
  socket.setTimeout(10000, () => socket.destroy());
  socket.write('220 localhost fixture SMTP\r\n');
  let buffer = '', recipient = '', data = null;
  socket.on('data', chunk => {
    buffer += chunk.toString('utf8');
    let end;
    while ((end = buffer.indexOf('\r\n')) >= 0) {
      const line = buffer.slice(0, end); buffer = buffer.slice(end + 2);
      if (data) {
        if (line !== '.') { data.push(line); continue; }
        const code = decodeSubject(data.join('\r\n')).match(/\b\d{6}\b/)?.[0];
        data = null;
        if (code) { mailbox.set(recipient, code); socket.write('250 captured\r\n'); }
        else socket.write('451 invalid fixture message\r\n');
      } else if (/^RCPT TO:/i.test(line)) {
        recipient = line.match(/<([^>]+)>/)?.[1] || ''; socket.write('250 OK\r\n');
      } else if (/^DATA$/i.test(line)) { data = []; socket.write('354 end with dot\r\n'); }
      else if (/^QUIT$/i.test(line)) socket.end('221 bye\r\n');
      else socket.write('250 OK\r\n');
    }
  });
});

async function api(route, body) {
  const response = await fetch(`${baseUrl}/api/v1/auth/${route}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(10000) });
  return { status: response.status, body: await response.json() };
}
async function forgot() {
  sql(`UPDATE auth_otp_limits SET next_send_at=DATE_SUB(NOW(6),INTERVAL 1 SECOND) WHERE email_key=${quote(limitKey(staffEmail))}`);
  const r = await api('forgot-password', { email: staffEmail }); assert.equal(r.status, 200);
  assert.match(r.body.challengeId, /^[0-9a-f]{64}$/); assert.match(mailbox.get(staffEmail), /^\d{6}$/);
  return { id: r.body.challengeId, code: mailbox.get(staffEmail) };
}
function envValues(values) { return { values: Object.entries(values).map(([key, value]) => ({ key, value, enabled: true })) }; }
function values(environment) { return Object.fromEntries(environment.values.map(v => [v.key, v.value])); }
async function run(name, items, environment, shouldFail = false) {
  const summary = await new Promise((resolve, reject) => newman.run({ collection: { ...collection, item: items },
    environment, reporters: [], silent: true, timeoutRequest: 10000,
  }, (error, summary) => error ? reject(error) : resolve(summary)));
  const failures = summary.run.failures.length;
  results.push({ scenario: name, requests: summary.run.stats.requests.total,
    assertions: summary.run.stats.assertions.total, failures,
    newmanVerdict: failures ? 'FAIL' : 'PASS', expected: shouldFail ? 'FAIL' : 'PASS',
    failedItems: summary.run.failures.map(f => f.source?.name || f.at || 'execution') });
  assert.equal(summary.run.stats.requests.failed, 0, 'Transport failures are not assertion-integrity evidence');
  assert.ok(summary.run.stats.assertions.total > 0, 'Scenario must execute assertions');
  if (shouldFail) assert.ok(summary.run.failures.every(f => f.error.name === 'AssertionError'), 'Expected assertion failure, not a script/setup error');
  assert.ok(shouldFail ? failures > 0 : failures === 0, 'Unexpected Newman verdict; see sanitized scenario summary');
  return summary.environment.toJSON();
}

async function main() {
  let environment;
  try {
    sql('SELECT DATABASE()');
    for (const [username, email, password, role] of [[adminUsername, adminEmail, adminPassword, 'ADMIN'], [staffUsername, staffEmail, staffPassword, 'STAFF']]) {
      sql(`INSERT INTO users(username,password_hash,full_name,email,role,status) VALUES(${quote(username)},${quote(hash(password))},'Postman fixture',${quote(email)},'${role}','ACTIVE')`);
    }
    await new Promise(resolve => smtp.listen(0, '127.0.0.1', resolve));
    const reserve = net.createServer(); await new Promise(resolve => reserve.listen(0, '127.0.0.1', resolve));
    const port = reserve.address().port; await new Promise(resolve => reserve.close(resolve));
    baseUrl = `http://127.0.0.1:${port}`;
    child = spawn('java', ['-jar', path.resolve(__dirname, '../backend/target/smart-cafe-0.0.1-SNAPSHOT.jar'),
      `--server.port=${port}`, '--server.address=127.0.0.1', `--spring.profiles.active=${profile}`,
      `--spring.datasource.url=${process.env.TEST_DB_URL}`, '--spring.flyway.enabled=false', '--spring.sql.init.mode=never',
      '--spring.mail.host=127.0.0.1', `--spring.mail.port=${smtp.address().port}`, '--spring.mail.properties.mail.smtp.auth=false',
      '--spring.mail.properties.mail.smtp.starttls.enable=false', '--spring.mail.properties.mail.smtp.starttls.required=false',
      '--spring.mail.properties.mail.smtp.ssl.enable=false'], {
      env: { ...process.env, SPRING_DATASOURCE_USERNAME: process.env.TEST_DB_USERNAME,
        SPRING_DATASOURCE_PASSWORD: process.env.TEST_DB_PASSWORD, AUTH_TOKEN_SECRET: secret },
      stdio: 'ignore', windowsHide: true,
    });
    const deadline = Date.now() + 90000;
    for (;;) {
      assert.equal(child.exitCode, null, 'Test backend must stay alive');
      try { if ((await fetch(`${baseUrl}/actuator/health`, { signal: AbortSignal.timeout(1000) })).status === 200) break; } catch {}
      assert.ok(Date.now() < deadline, 'Test backend readiness timeout');
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    environment = envValues({ baseUrl, adminUsername, staffUsername, adminEmail, staffEmail, unknownEmail,
      adminPassword, staffPassword, newStaffPassword, wrongOtpCode: '000000' });
    // Preserve original 1-23 order. Pause only to inject the OTP captured by the real loopback SMTP server.
    environment = await run('original requests 1-15', collection.item.slice(0, 15), environment);
    const code = mailbox.get(staffEmail); assert.match(code, /^\d{6}$/);
    environment = envValues({ ...values(environment), testOtpCode: code, wrongOtpCode: code === '000000' ? '111111' : '000000' });
    environment = await run('original requests 16-23', collection.item.slice(15, 23), environment);

    const expired = await forgot();
    sql(`UPDATE auth_otp_challenges SET created_at=DATE_SUB(NOW(6),INTERVAL 1 HOUR),expires_at=NOW(6) WHERE id=${quote(expired.id)}`);
    const rotated = await forgot();
    sql(`UPDATE auth_otp_challenges SET next_send_at=DATE_SUB(NOW(6),INTERVAL 1 SECOND) WHERE id=${quote(rotated.id)}`);
    assert.equal((await api('resend-otp', { challengeId: rotated.id })).status, 200);
    assert.notEqual(mailbox.get(staffEmail), rotated.code, 'Fixture requires different OTP values for the replaced-code case');
    const locked = await forgot(); sql(`UPDATE auth_otp_challenges SET attempts=5 WHERE id=${quote(locked.id)}`);
    const prior = values(environment);
    // An expired, correctly signed grant for the current auth_version; test key never leaves this process/child env.
    const grantPayload = Buffer.from(prior.resetToken.split('.')[0], 'base64url').toString().split(':');
    grantPayload[3] = '1'; grantPayload[4] = String(Date.now() - 1000);
    const payload = Buffer.from(grantPayload.join(':')).toString('base64url');
    const expiredResetToken = `${payload}.${crypto.createHmac('sha256', secret).update(payload).digest('hex')}`;
    environment = envValues({ ...prior, expiredChallengeId: expired.id, expiredOtpCode: expired.code,
      rotatedChallengeId: rotated.id, previousOtpCode: rotated.code, lockedChallengeId: locked.id, lockedOtpCode: locked.code, expiredResetToken });
    await run('recovery negative cases', collection.item.slice(23, 24), environment);

    const fresh = await forgot();
    await run('fault: wrong testOtpCode', [collection.item[18]], envValues({ ...prior, challengeId: fresh.id, testOtpCode: fresh.code === '000000' ? '111111' : '000000' }), true);
    await run('fault: invalid reset grant verification', [collection.item[19]], envValues({ ...prior, resetToken: 'invalid-fixture-grant' }), true);
    await run('fault: rejected password reset', [collection.item[20]], envValues({ ...prior, resetToken: 'invalid-fixture-grant' }), true);
    await run('fault: rejected new-password login', [collection.item[21]], envValues({ ...prior, newStaffPassword: 'WrongFixture@123' }), true);
    // Audit observations, not claims of deployed protections or proposed policies.
    for (let i = 0; i < 6; i++) assert.equal((await api('login', { username: staffUsername, password: 'WrongFixture@123' })).status, 401);
    assert.equal((await api('login', { username: staffUsername, password: newStaffPassword })).status, 200);
    for (let i = 0; i < 8; i++) {
      const email = `${prefix}_dummy${i}@example.test`; emails.push(email);
      assert.equal((await api('forgot-password', { email })).status, 200);
      assert.equal(sql(`SELECT COUNT(*) FROM auth_otp_challenges WHERE email=${quote(email)} AND user_id IS NULL`), '1');
    }
    results.push({ audit: 'no login throttle at six failures; eight distinct dummy emails accepted from loopback', policy: 'NEEDS OWNER DECISION' });
    await run('Home account after recovery', collection.item.slice(24), environment);
  } finally {
    if (child && child.exitCode === null) { const exited = once(child, 'exit'); child.kill(); await exited; }
    for (const socket of sockets) socket.destroy();
    if (smtp.listening) await new Promise(resolve => smtp.close(resolve));
    sql(`DELETE FROM auth_otp_challenges WHERE email IN (${emails.map(quote).join(',')})`);
    sql(`DELETE FROM auth_otp_limits WHERE email_key IN (${emails.map(e => quote(limitKey(e))).join(',')})`);
    sql(`DELETE FROM users WHERE username IN (${[adminUsername, staffUsername].map(quote).join(',')})`);
    assert.equal(sql(`SELECT COUNT(*) FROM auth_otp_challenges WHERE email IN (${emails.map(quote).join(',')})`), '0');
    assert.equal(sql(`SELECT COUNT(*) FROM auth_otp_limits WHERE email_key IN (${emails.map(e => quote(limitKey(e))).join(',')})`), '0');
    assert.equal(sql(`SELECT COUNT(*) FROM users WHERE username IN (${[adminUsername, staffUsername].map(quote).join(',')})`), '0');
  }
}
main().then(() => console.log(JSON.stringify({ outcome: 'PASS', backend: 'real loopback HTTP',
  profile, database: 'isolated *_test', mail: 'real loopback SMTP capture', cleanup: 'owned fixtures removed', results }, null, 2)))
  .catch(error => { console.log(JSON.stringify({ outcome: 'FAIL', errorType: error.name, results }, null, 2)); process.exitCode = 1; });

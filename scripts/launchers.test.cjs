const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const keys = ['DB_URL','DB_USERNAME','DB_PASSWORD','SERVER_PORT','AUTH_TOKEN_SECRET',
  'SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_PASSWORD','SMTP_FROM','SMTP_AUTH','SMTP_STARTTLS','SMTP_SSL',
  'SPRING_PROFILES_ACTIVE','CLOUDINARY_CLOUD_NAME','CLOUDINARY_API_KEY','CLOUDINARY_API_SECRET',
  'OTP_VALIDITY_SECONDS','AUTH_TOKEN_VALIDITY_SECONDS','PENDING_REGISTRATION_VALIDITY_SECONDS',
  'TEST_DB_URL','TEST_DB_USERNAME','TEST_DB_PASSWORD'];

function launch(script, file, override = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'smart-cafe-launcher-test-'));
  try {
    fs.mkdirSync(path.join(dir,'backend'));
    fs.copyFileSync(path.join(root, script), path.join(dir, script));
    fs.writeFileSync(path.join(dir,'backend','.env'), Object.entries(file).map(([k,v]) => `${k}=${v}`).join('\n'));
    // Stub Maven only in this isolated copied tree: never starts Spring, opens SMTP or touches DB.
    fs.writeFileSync(path.join(dir,'backend','mvnw.cmd'), '@echo off\r\npowershell -NoProfile -File "%~dp0snapshot.ps1"\r\n');
    fs.writeFileSync(path.join(dir,'backend','snapshot.ps1'), `$r=@{}; ${JSON.stringify(keys).replace(/^\[/,'@(').replace(/\]$/,')')} | ForEach-Object { $r[$_]=[Environment]::GetEnvironmentVariable($_,'Process') }; $r | ConvertTo-Json -Compress`);
    const env = {...process.env}; for (const k of keys) delete env[k]; Object.assign(env,override);
    const r = spawnSync('powershell', ['-NoProfile','-ExecutionPolicy','Bypass','-File',path.join(dir,script)], {env,encoding:'utf8',windowsHide:true,timeout:20000});
    assert.equal(r.status,0,'Copied launcher/stub must complete safely');
    return JSON.parse(r.stdout.trim());
  } finally {
    assert.ok(path.dirname(dir) === os.tmpdir() && path.basename(dir).startsWith('smart-cafe-launcher-test-'));
    fs.rmSync(dir,{recursive:true,force:true});
  }
}
test('launcher loads all advertised validity settings without changing defaults', () => {
  const values = {OTP_VALIDITY_SECONDS:'345',AUTH_TOKEN_VALIDITY_SECONDS:'456',PENDING_REGISTRATION_VALIDITY_SECONDS:'567'};
  const got = launch('start-be.ps1',values);
  for (const k of Object.keys(values)) assert.equal(got[k],values[k],k);
});
test('launcher preserves OS overrides and aligns missing username with Spring default', () => {
  assert.equal(launch('start-be.ps1',{}).DB_USERNAME,'cafe_app');
  const got=launch('start-be.ps1',{DB_USERNAME:'file_fixture',OTP_VALIDITY_SECONDS:'345'},{DB_USERNAME:'os_fixture',OTP_VALIDITY_SECONDS:'789'});
  assert.equal(got.DB_USERNAME,'os_fixture'); assert.equal(got.OTP_VALIDITY_SECONDS,'789');
});
test('test launcher loads only TEST_DB settings and keeps test process overrides', () => {
  const got=launch('test-be.ps1',{DB_URL:'jdbc:mysql://127.0.0.1/shared',OTP_VALIDITY_SECONDS:'1',
    TEST_DB_URL:'jdbc:mysql://127.0.0.1/fixture_test',TEST_DB_USERNAME:'file_fixture',TEST_DB_PASSWORD:'fixture-only'}, {TEST_DB_USERNAME:'os_fixture'});
  assert.equal(got.TEST_DB_USERNAME,'os_fixture'); assert.equal(got.TEST_DB_URL,'jdbc:mysql://127.0.0.1/fixture_test');
  assert.ok(!got.DB_URL && !got.OTP_VALIDITY_SECONDS);
});

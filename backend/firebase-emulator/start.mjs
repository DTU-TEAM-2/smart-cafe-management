import { existsSync, mkdirSync, readdirSync, readFileSync, realpathSync, statSync } from 'node:fs';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn, spawnSync } from 'node:child_process';

const directory = dirname(fileURLToPath(import.meta.url));
const data = join(directory, '.local-data');
const metadata = join(data, 'firebase-export-metadata.json');
const cli = join(directory, 'node_modules', 'firebase-tools', 'lib', 'bin', 'firebase.js');
const configFile = join(directory, 'firebase.json');

function fail(message) { console.error(message); process.exit(1); }

const [major, minor] = process.versions.node.split('.').map(Number);
if (!((major === 22 && minor >= 19) || major >= 24)) fail('Use Node 22.19+ (22.x) or Node 24+ for this project.');
if (!existsSync(cli)) fail('Install the pinned emulator dependencies: cd backend/firebase-emulator && npm ci');
const config = JSON.parse(readFileSync(configFile, 'utf8')).emulators?.storage;
if (config?.host !== '127.0.0.1' || !Number.isInteger(config.port) || config.port < 1 || config.port > 65535)
  fail('The checked-in Storage emulator config must bind to 127.0.0.1 on a valid port.');
const java = spawnSync('java', ['-version'], { encoding: 'utf8', windowsHide: true });
const javaVersion = /version "(\d+)/.exec((java.stderr || '') + (java.stdout || ''));
if (java.status !== 0 || !javaVersion || Number(javaVersion[1]) < 21) fail('Install Java 21+ and make java available on PATH.');

const hasExport = existsSync(metadata);
if (hasExport) {
  try {
    const exported = JSON.parse(readFileSync(metadata, 'utf8'));
    const storagePath = exported.storage?.path;
    if (typeof storagePath !== 'string' || !storagePath || isAbsolute(storagePath))
      fail('The local export has no valid Storage path. Restore a matching export before starting.');
    const storageDirectory = resolve(data, storagePath);
    const inside = relative(realpathSync(data), realpathSync(storageDirectory));
    if (isAbsolute(inside) || inside.split(/[\\/]/).includes('..') || !statSync(storageDirectory).isDirectory()
      || !existsSync(join(storageDirectory, 'buckets.json')))
      fail('The local Storage export is incomplete or outside the data folder. Restore it before starting.');
  } catch { fail('The local Firebase export metadata is invalid. Restore a valid export; no files were removed.'); }
} else if (existsSync(data) && readdirSync(data).length) {
  fail('The local data folder is non-empty but has no valid Firebase export. Restore or move it aside before starting.');
}
if (process.argv.includes('--check')) {
  console.log(`Local Firebase prerequisites OK; ${hasExport ? 'existing Storage export will be imported' : 'first run starts empty'}. No cloud login or billing.`);
  process.exit(0);
}

mkdirSync(data, { recursive: true });
const args = [cli, 'emulators:start', '--only', 'storage', '--project', 'demo-smart-cafe-news',
  '--config', configFile, '--export-on-exit', data];
if (hasExport) args.push('--import', data);
const environment = { ...process.env };
delete environment.GOOGLE_APPLICATION_CREDENTIALS;
console.log(`Firebase Storage is LOCAL ONLY at 127.0.0.1:${config.port}. Stop with Ctrl+C to save images for the next run.`);
const child = spawn(process.execPath, args, { cwd: directory, env: environment, stdio: 'inherit', windowsHide: true });
// The foreground child receives terminal Ctrl+C too; keep the wrapper alive for its export to finish.
process.on('SIGINT', () => {});
child.on('error', () => fail('Could not start the local Firebase CLI. Check Java/Node and npm ci.'));
child.on('exit', code => { process.exitCode = code ?? 1; });

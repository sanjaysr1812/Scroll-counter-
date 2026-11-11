import express from 'express';
import fs from 'fs';
import path from 'path';
import { execFile } from 'child_process';
import { createServer as createViteServer } from 'vite';

const app = express();
const PORT = 3000;
const ROOT_DIR = process.cwd();

app.use(express.json());

interface ProjectFileEntry {
  path: string;
  category:
    | 'gradle'
    | 'manifest-res'
    | 'accessibility'
    | 'data-room'
    | 'domain-utils'
    | 'compose-ui'
    | 'tests'
    | 'docs';
  sizeBytes: number;
  lineCount: number;
}

const INCLUDED_ROOT_PATHS = [
  'settings.gradle.kts',
  'build.gradle.kts',
  'gradle.properties',
  'gradle',
  'app',
  'README.md',
  'PRIVACY_POLICY.md',
  'BUILD_INSTRUCTIONS.md',
  'PUBLISHING_COMPLIANCE.md',
];

function classifyFileCategory(relPath: string): ProjectFileEntry['category'] {
  if (relPath.endsWith('.md')) return 'docs';
  if (relPath.includes('/test/') || relPath.includes('/androidTest/')) return 'tests';
  if (relPath.includes('/accessibility/')) return 'accessibility';
  if (relPath.includes('/data/') || relPath.includes('/schemas/')) return 'data-room';
  if (relPath.includes('/ui/') || relPath.endsWith('MainActivity.kt')) return 'compose-ui';
  if (
    relPath.includes('/domain/') ||
    relPath.includes('/utils/') ||
    relPath.includes('/notifications/') ||
    relPath.endsWith('ScrollMeterApplication.kt')
  ) {
    return 'domain-utils';
  }
  if (relPath.endsWith('.xml')) return 'manifest-res';
  return 'gradle';
}

function collectProjectFiles(): ProjectFileEntry[] {
  const results: ProjectFileEntry[] = [];

  function walk(currentRel: string) {
    const fullPath = path.join(ROOT_DIR, currentRel);
    if (!fs.existsSync(fullPath)) return;
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      const children = fs.readdirSync(fullPath).sort();
      for (const child of children) {
        walk(path.posix.join(currentRel, child));
      }
    } else if (stat.isFile()) {
      const content = fs.readFileSync(fullPath, 'utf-8');
      const lineCount = content.split('\n').length;
      results.push({
        path: currentRel,
        category: classifyFileCategory(currentRel),
        sizeBytes: stat.size,
        lineCount,
      });
    }
  }

  for (const item of INCLUDED_ROOT_PATHS) {
    walk(item);
  }

  return results;
}

app.get('/api/android-files', (_req, res) => {
  try {
    const files = collectProjectFiles();
    res.json({
      projectName: 'ScrollMeter',
      packageName: 'com.scrollmeter.app',
      minSdk: 28,
      targetSdk: 35,
      compileSdk: 35,
      apkBuilt: false,
      apkNote:
        'This preview environment runs a Node.js container without mobile SDK build tools. Export or download the complete source archive to run ./gradlew assembleDebug or ./gradlew bundleRelease locally.',
      totalFiles: files.length,
      totalLines: files.reduce((acc, f) => acc + f.lineCount, 0),
      files,
    });
  } catch (err) {
    res.status(500).json({ error: String(err) });
  }
});

app.get('/api/android-file', (req, res) => {
  try {
    const requestedPath = String(req.query.path || '');
    const normalized = path.posix.normalize(requestedPath).replace(/^\/+/, '');
    const isAllowed = INCLUDED_ROOT_PATHS.some(
      (prefix) => normalized === prefix || normalized.startsWith(`${prefix}/`)
    );

    if (!isAllowed || normalized.includes('..')) {
      res.status(400).json({ error: 'Invalid or disallowed file path' });
      return;
    }

    const fullPath = path.join(ROOT_DIR, normalized);
    if (!fs.existsSync(fullPath) || !fs.statSync(fullPath).isFile()) {
      res.status(404).json({ error: 'File not found' });
      return;
    }

    const content = fs.readFileSync(fullPath, 'utf-8');
    res.json({
      path: normalized,
      content,
      lineCount: content.split('\n').length,
      sizeBytes: Buffer.byteLength(content, 'utf-8'),
    });
  } catch (err) {
    res.status(500).json({ error: String(err) });
  }
});

app.get('/api/export-android-project', (_req, res) => {
  const existingTargets = INCLUDED_ROOT_PATHS.filter((p) =>
    fs.existsSync(path.join(ROOT_DIR, p))
  );

  res.setHeader('Content-Type', 'application/gzip');
  res.setHeader(
    'Content-Disposition',
    'attachment; filename="ScrollMeter-Project.tar.gz"'
  );

  const tarProcess = execFile(
    'tar',
    ['-czf', '-', ...existingTargets],
    { cwd: ROOT_DIR, encoding: 'buffer', maxBuffer: 50 * 1024 * 1024 },
    (error, stdout) => {
      if (error) {
        if (!res.headersSent) {
          res.status(500).json({ error: 'Failed to create project archive' });
        }
        return;
      }
      res.end(stdout);
    }
  );

  tarProcess.on('error', () => {
    if (!res.headersSent) {
      res.status(500).json({ error: 'Archive process error' });
    }
  });
});

async function startServer() {
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        hmr: false,
        watch: null,
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(ROOT_DIR, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`ScrollMeter server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer();

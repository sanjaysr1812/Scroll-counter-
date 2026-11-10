import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  Copy,
  Download,
  FileCode,
  FolderTree,
  Moon,
  Play,
  RotateCcw,
  Search,
  ShieldCheck,
  Smartphone,
  Sun,
  Terminal,
  Trash2,
} from 'lucide-react';
import {
  AccessibilitySignalSnapshot,
  PlatformId,
  QaTestCaseResult,
  runAutomatedQaTestSuite,
  SwipeDetectionVerifierEngine,
  SwipeEvaluationOutcome,
} from './engine/swipeDetectionVerifier';

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

interface ProjectManifestResponse {
  projectName: string;
  packageName: string;
  minSdk: number;
  targetSdk: number;
  compileSdk: number;
  apkBuilt: boolean;
  apkNote: string;
  totalFiles: number;
  totalLines: number;
  files: ProjectFileEntry[];
}

interface RecordedSwipeEvent {
  id: number;
  platform: PlatformId;
  timestampMs: number;
  localDate: string;
  hourOfDay: number;
  eventType: string;
  confidenceCode: string;
  confidenceScore: number;
  signatureHash: string;
}

interface DiagnosticLogItem {
  id: number;
  timestampMs: number;
  snapshot: AccessibilitySignalSnapshot;
  outcome: SwipeEvaluationOutcome;
}

type WorkbenchTab = 'explorer' | 'verifier' | 'compose-preview' | 'build-guide';
type ComposeBottomTab = 'home' | 'analytics' | 'history' | 'settings' | 'diagnostics' | 'onboarding';

const CATEGORY_LABELS: Record<string, string> = {
  all: 'All Project Files',
  accessibility: 'Accessibility Engine',
  'data-room': 'Room Database & DataStore',
  'compose-ui': 'Compose UI & Disclosure',
  'domain-utils': 'Domain, Notifications & Utils',
  gradle: 'Gradle Kotlin DSL',
  'manifest-res': 'Manifest, Icons & XML',
  tests: 'Unit & Instrumentation Tests',
  docs: 'Publishing & Build Guides',
};

function getTodayIso(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatDuration(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  if (safe === 0) return '0m';
  const hrs = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hrs > 0 && mins > 0) return `${hrs}h ${mins}m`;
  if (hrs > 0) return `${hrs}h`;
  if (mins > 0) return `${mins}m ${secs}s`;
  return `${secs}s`;
}

function formatTimeOfDay(ms: number | null): string {
  if (!ms || ms <= 0) return 'No events recorded yet';
  const d = new Date(ms);
  return d.toTimeString().split(' ')[0];
}

export default function App() {
  const [darkMode, setDarkMode] = useState<boolean>(true);
  const [activeTab, setActiveTab] = useState<WorkbenchTab>('explorer');

  // Source Explorer state
  const [manifest, setManifest] = useState<ProjectManifestResponse | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [fileSearchQuery, setFileSearchQuery] = useState<string>('');
  const [selectedFilePath, setSelectedFilePath] = useState<string>('PUBLISHING_COMPLIANCE.md');
  const [selectedFileContent, setSelectedFileContent] = useState<string>('');
  const [isLoadingFile, setIsLoadingFile] = useState<boolean>(false);
  const [copiedFile, setCopiedFile] = useState<boolean>(false);

  // Live SwipeDetectionEngine Verifier & Local Room State Mirror
  const engineRef = useRef<SwipeDetectionVerifierEngine>(new SwipeDetectionVerifierEngine(450));
  const [simClockMs, setSimClockMs] = useState<number>(() => Date.now());
  const [ytPagerIndex, setYtPagerIndex] = useState<number>(0);
  const [igPagerIndex, setIgPagerIndex] = useState<number>(0);

  // User Settings state (matching UserSettingsEntity defaults)
  const [dailyCombinedLimit, setDailyCombinedLimit] = useState<number>(250);
  const [youtubeDailyLimit] = useState<number>(150);
  const [instagramDailyLimit] = useState<number>(150);
  const [timeLimitMinutes, setTimeLimitMinutes] = useState<number>(60);
  const [cooldownMs, setCooldownMs] = useState<number>(450);
  const [trackingEnabled, setTrackingEnabled] = useState<boolean>(true);
  const [osPermissionEnabled, setOsPermissionEnabled] = useState<boolean>(true);
  const [showDisclosureModal, setShowDisclosureModal] = useState<boolean>(false);
  const [warnAt50] = useState<boolean>(true);
  const [warnAt75] = useState<boolean>(true);
  const [warnAt90] = useState<boolean>(true);
  const [warnAt100] = useState<boolean>(true);

  // Local recorded events (starts at 0 confirmed swipes — never hardcoded fake values)
  const [confirmedSwipes, setConfirmedSwipes] = useState<RecordedSwipeEvent[]>([]);
  const [uncertainCount, setUncertainCount] = useState<number>(0);
  const [ignoredCount, setIgnoredCount] = useState<number>(0);
  const [duplicateRejectedCount, setDuplicateRejectedCount] = useState<number>(0);
  const [youtubeDurationSeconds, setYoutubeDurationSeconds] = useState<number>(0);
  const [instagramDurationSeconds, setInstagramDurationSeconds] = useState<number>(0);
  const [diagnosticLogs, setDiagnosticLogs] = useState<DiagnosticLogItem[]>([]);
  const [qaResults, setQaResults] = useState<QaTestCaseResult[]>(() => runAutomatedQaTestSuite());

  // Compose Preview screen navigation
  const [composeTab, setComposeTab] = useState<ComposeBottomTab>('home');

  // Sync dark mode class on <html>
  useEffect(() => {
    const root = document.documentElement;
    if (darkMode) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [darkMode]);

  // Fetch project manifest
  useEffect(() => {
    fetch('/api/android-files')
      .then((r) => r.json())
      .then((data: ProjectManifestResponse) => {
        setManifest(data);
      })
      .catch(() => {
        // Ignore transient fetch error
      });
  }, []);

  // Fetch selected source file content
  useEffect(() => {
    if (!selectedFilePath) return;
    setIsLoadingFile(true);
    fetch(`/api/android-file?path=${encodeURIComponent(selectedFilePath)}`)
      .then((r) => r.json())
      .then((data) => {
        if (data && typeof data.content === 'string') {
          setSelectedFileContent(data.content);
        }
      })
      .finally(() => setIsLoadingFile(false));
  }, [selectedFilePath]);

  const filteredFiles = useMemo(() => {
    if (!manifest) return [];
    return manifest.files.filter((file) => {
      const matchesCat = selectedCategory === 'all' || file.category === selectedCategory;
      const matchesQuery =
        !fileSearchQuery.trim() ||
        file.path.toLowerCase().includes(fileSearchQuery.trim().toLowerCase());
      return matchesCat && matchesQuery;
    });
  }, [manifest, selectedCategory, fileSearchQuery]);

  // Dispatch an AccessibilityEvent snapshot through the 8-stage SwipeDetectionEngine
  function dispatchSnapshot(
    customSnapshot: Partial<AccessibilitySignalSnapshot> & {
      packageName: string;
      eventType: AccessibilitySignalSnapshot['eventType'];
      viewIdResourceName: string;
    },
    advanceClockMs = 650,
    estimatedSessionDeltaSec = 8
  ) {
    if (!osPermissionEnabled || !trackingEnabled) {
      return;
    }

    const nextClock = simClockMs + advanceClockMs;
    setSimClockMs(nextClock);
    engineRef.current.cooldownMs = cooldownMs;

    const fullSnapshot: AccessibilitySignalSnapshot = {
      packageName: customSnapshot.packageName,
      eventType: customSnapshot.eventType,
      eventTimestampMs: nextClock,
      className: customSnapshot.className ?? 'androidx.viewpager2.widget.ViewPager2',
      viewIdResourceName: customSnapshot.viewIdResourceName,
      fromIndex: customSnapshot.fromIndex ?? 0,
      toIndex: customSnapshot.toIndex ?? 0,
      scrollDeltaX: customSnapshot.scrollDeltaX ?? 0,
      scrollDeltaY: customSnapshot.scrollDeltaY ?? 0,
      isFullScreenVerticalContainer: customSnapshot.isFullScreenVerticalContainer ?? true,
      containerHeightRatio: customSnapshot.containerHeightRatio ?? 0.92,
      containerWidthRatio: customSnapshot.containerWidthRatio ?? 1.0,
      hasActiveShortsOrReelsMarker: customSnapshot.hasActiveShortsOrReelsMarker ?? true,
      hasCommentsOrSheetOverlay: customSnapshot.hasCommentsOrSheetOverlay ?? false,
      hasSearchInputOrGrid: customSnapshot.hasSearchInputOrGrid ?? false,
      hasProfileGridPattern: customSnapshot.hasProfileGridPattern ?? false,
      hasStandardWatchPlayerLayout: customSnapshot.hasStandardWatchPlayerLayout ?? false,
      isScreenInteractive: customSnapshot.isScreenInteractive ?? true,
      structuralNodeSignature: customSnapshot.structuralNodeSignature ?? (customSnapshot.toIndex ?? 0) + 100,
    };

    const outcome = engineRef.current.evaluate(fullSnapshot);

    setDiagnosticLogs((prev) => [
      {
        id: Date.now() + Math.random(),
        timestampMs: nextClock,
        snapshot: fullSnapshot,
        outcome,
      },
      ...prev.slice(0, 39),
    ]);

    if (outcome.isAcceptedSwipe && outcome.platform) {
      const nowDate = new Date(nextClock);
      const newEvent: RecordedSwipeEvent = {
        id: confirmedSwipes.length + 1,
        platform: outcome.platform,
        timestampMs: nextClock,
        localDate: getTodayIso(),
        hourOfDay: nowDate.getHours(),
        eventType: fullSnapshot.eventType,
        confidenceCode: outcome.confidence,
        confidenceScore: outcome.confidenceScore,
        signatureHash: outcome.eventSignatureHash,
      };
      setConfirmedSwipes((prev) => [newEvent, ...prev]);
      if (outcome.platform === 'YOUTUBE_SHORTS') {
        setYoutubeDurationSeconds((s) => s + estimatedSessionDeltaSec);
      } else {
        setInstagramDurationSeconds((s) => s + estimatedSessionDeltaSec);
      }
    } else if (outcome.isDuplicateRejected) {
      setDuplicateRejectedCount((c) => c + 1);
    } else if (outcome.isUncertainEvent) {
      setUncertainCount((c) => c + 1);
    } else {
      setIgnoredCount((c) => c + 1);
    }
  }

  function handleResetVerifierData() {
    engineRef.current.resetState();
    setConfirmedSwipes([]);
    setUncertainCount(0);
    setIgnoredCount(0);
    setDuplicateRejectedCount(0);
    setYoutubeDurationSeconds(0);
    setInstagramDurationSeconds(0);
    setDiagnosticLogs([]);
    setYtPagerIndex(0);
    setIgPagerIndex(0);
  }

  function handleExportCsv() {
    const today = getTodayIso();
    const ytSwipes = confirmedSwipes.filter((e) => e.platform === 'YOUTUBE_SHORTS').length;
    const igSwipes = confirmedSwipes.filter((e) => e.platform === 'INSTAGRAM_REELS').length;
    const total = ytSwipes + igSwipes;
    const totalDur = youtubeDurationSeconds + instagramDurationSeconds;
    const csv = [
      'date,youtube_shorts_swipes,instagram_reels_swipes,total_confirmed_swipes,uncertain_events,youtube_duration_seconds,instagram_duration_seconds,total_duration_seconds',
      `${today},${ytSwipes},${igSwipes},${total},${uncertainCount},${youtubeDurationSeconds},${instagramDurationSeconds},${totalDur}`,
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `scrollmeter_history_${today}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  const youtubeSwipeCount = useMemo(
    () => confirmedSwipes.filter((e) => e.platform === 'YOUTUBE_SHORTS').length,
    [confirmedSwipes]
  );
  const instagramSwipeCount = useMemo(
    () => confirmedSwipes.filter((e) => e.platform === 'INSTAGRAM_REELS').length,
    [confirmedSwipes]
  );
  const totalSwipeCount = youtubeSwipeCount + instagramSwipeCount;
  const totalDurationSeconds = youtubeDurationSeconds + instagramDurationSeconds;
  const remainingAllowance = Math.max(0, dailyCombinedLimit - totalSwipeCount);

  const combinedRatioPct =
    dailyCombinedLimit > 0 ? Math.round((totalSwipeCount / dailyCombinedLimit) * 100) : 0;

  const activeWarningThreshold = useMemo(() => {
    const thresholds: number[] = [];
    if (warnAt50) thresholds.push(50);
    if (warnAt75) thresholds.push(75);
    if (warnAt90) thresholds.push(90);
    if (warnAt100) thresholds.push(100);
    const crossed = thresholds.filter((t) => combinedRatioPct >= t);
    return crossed.length > 0 ? crossed[crossed.length - 1] : null;
  }, [combinedRatioPct, warnAt50, warnAt75, warnAt90, warnAt100]);

  const hourlyCounts = useMemo(() => {
    const buckets = new Array(24).fill(0);
    for (const ev of confirmedSwipes) {
      if (ev.hourOfDay >= 0 && ev.hourOfDay < 24) {
        buckets[ev.hourOfDay]++;
      }
    }
    return buckets;
  }, [confirmedSwipes]);

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100 transition-colors">
      {/* Top Bar Contract: 3 zones separated by gap-8 */}
      <header className="sticky top-0 z-30 flex items-center justify-between gap-8 px-6 py-4 bg-white/95 dark:bg-slate-900/95 backdrop-blur-md border-b border-slate-200 dark:border-slate-800">
        {/* Zone 1: Single text wordmark */}
        <a
          href="#top"
          onClick={(e) => {
            e.preventDefault();
            setActiveTab('explorer');
          }}
          className="text-lg font-bold tracking-tight text-slate-900 dark:text-white font-display whitespace-nowrap shrink-0"
        >
          ScrollMeter
        </a>

        {/* Zone 2: 4 concise single-line text navigation links */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-600 dark:text-slate-300">
          <button
            type="button"
            onClick={() => setActiveTab('explorer')}
            className={`py-1 transition-colors whitespace-nowrap shrink-0 border-b-2 ${
              activeTab === 'explorer'
                ? 'border-indigo-500 text-slate-900 dark:text-white font-semibold'
                : 'border-transparent hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            Source Explorer
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('verifier')}
            className={`py-1 transition-colors whitespace-nowrap shrink-0 border-b-2 ${
              activeTab === 'verifier'
                ? 'border-indigo-500 text-slate-900 dark:text-white font-semibold'
                : 'border-transparent hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            Detection Verifier
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('compose-preview')}
            className={`py-1 transition-colors whitespace-nowrap shrink-0 border-b-2 ${
              activeTab === 'compose-preview'
                ? 'border-indigo-500 text-slate-900 dark:text-white font-semibold'
                : 'border-transparent hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            Compose Screens
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('build-guide')}
            className={`py-1 transition-colors whitespace-nowrap shrink-0 border-b-2 ${
              activeTab === 'build-guide'
                ? 'border-indigo-500 text-slate-900 dark:text-white font-semibold'
                : 'border-transparent hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            Build & Publishing
          </button>
        </nav>

        {/* Zone 3: 1 primary action */}
        <div className="flex items-center gap-3 shrink-0">
          <a
            href="/api/export-android-project"
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors whitespace-nowrap shrink-0"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Download Project Archive</span>
          </a>
        </div>
      </header>

      {/* Main Container (1440px baseline) */}
      <main className="max-w-[1380px] mx-auto px-6 py-8 space-y-8">
        {/* Hero Overview & Publishing Compliance Status */}
        <section className="p-6 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
            <div className="space-y-2 max-w-3xl">
              <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400 font-mono tabular-nums">
                <span>Package: com.scrollmeter.app</span>
                <span aria-hidden="true">·</span>
                <span>Min SDK 28 / Target SDK 35</span>
                <span aria-hidden="true">·</span>
                <span>Publishing Metadata Compliant</span>
                <span aria-hidden="true">·</span>
                <span>
                  {manifest
                    ? `${manifest.totalFiles} Files (${manifest.totalLines.toLocaleString()} LOC)`
                    : 'Loading project files...'}
                </span>
              </div>
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 dark:text-white font-display">
                ScrollMeter — Know Every Swipe. Take Back Your Time.
              </h1>
              <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
                Complete native short-video swipe tracker with <code className="font-mono text-xs">AccessibilityService</code>,
                Room database migrations, two-step Prominent Disclosure consent dialog, adaptive launcher icons, and
                trademark-clean publishing metadata. Use the tabs to inspect all source files, test the 8-stage{' '}
                <code className="font-mono text-xs">SwipeDetectionEngine</code>, or export the project archive.
              </p>
            </div>

            {/* Workspace Toolbar & Theme Toggle */}
            <div className="flex flex-wrap items-center gap-2 shrink-0">
              <div className="flex md:hidden items-center gap-1 p-1 bg-slate-100 dark:bg-slate-800 rounded-lg">
                {(['explorer', 'verifier', 'compose-preview', 'build-guide'] as WorkbenchTab[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setActiveTab(t)}
                    className={`px-2.5 py-1.5 text-xs font-medium rounded-md whitespace-nowrap ${
                      activeTab === t
                        ? 'bg-white dark:bg-slate-900 text-slate-900 dark:text-white shadow-xs'
                        : 'text-slate-600 dark:text-slate-400'
                    }`}
                  >
                    {t === 'explorer'
                      ? 'Files'
                      : t === 'verifier'
                      ? 'Verifier'
                      : t === 'compose-preview'
                      ? 'Screens'
                      : 'Publish'}
                  </button>
                ))}
              </div>

              <button
                type="button"
                onClick={() => setDarkMode((d) => !d)}
                className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors whitespace-nowrap shrink-0"
              >
                {darkMode ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
                <span>{darkMode ? 'Light Theme' : 'Dark Theme'}</span>
              </button>
            </div>
          </div>
        </section>

        {/* TAB 1: SOURCE EXPLORER */}
        {activeTab === 'explorer' && (
          <section className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Left Sidebar: Module & File Tree */}
            <div className="lg:col-span-4 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-4 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
                  <FolderTree className="w-4 h-4 text-indigo-500" />
                  <span>Project Source Tree</span>
                </h2>
                <span className="text-xs text-slate-500 dark:text-slate-400 font-mono tabular-nums">
                  {filteredFiles.length} files
                </span>
              </div>

              {/* Search input */}
              <div className="relative">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={fileSearchQuery}
                  onChange={(e) => setFileSearchQuery(e.target.value)}
                  placeholder="Filter by filename or package..."
                  className="w-full pl-8 pr-3 py-2 text-xs rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/70 text-slate-900 dark:text-slate-100 focus:outline-none focus:border-indigo-500"
                />
              </div>

              {/* Category selector */}
              <div className="flex flex-wrap gap-1.5">
                {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setSelectedCategory(key)}
                    className={`px-2.5 py-1 text-xs rounded-md transition-colors whitespace-nowrap ${
                      selectedCategory === key
                        ? 'bg-indigo-600 text-white font-medium'
                        : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>

              {/* File list */}
              <div className="max-h-[560px] overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800/70 pr-1">
                {filteredFiles.map((file) => {
                  const isSelected = file.path === selectedFilePath;
                  const shortName = file.path.split('/').pop() || file.path;
                  const parentDir = file.path.includes('/')
                    ? file.path.substring(0, file.path.lastIndexOf('/'))
                    : 'root';
                  return (
                    <button
                      key={file.path}
                      type="button"
                      onClick={() => setSelectedFilePath(file.path)}
                      className={`w-full text-left px-3 py-2.5 transition-colors flex items-start justify-between gap-2 rounded-lg ${
                        isSelected
                          ? 'bg-indigo-50/90 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-300'
                          : 'hover:bg-slate-50 dark:hover:bg-slate-800/50 text-slate-700 dark:text-slate-200'
                      }`}
                    >
                      <div className="min-w-0">
                        <div className="text-xs font-semibold truncate font-mono">{shortName}</div>
                        <div className="text-[11px] text-slate-500 dark:text-slate-400 truncate font-mono">
                          {parentDir}
                        </div>
                      </div>
                      <span className="text-[11px] text-slate-400 dark:text-slate-500 font-mono tabular-nums shrink-0">
                        {file.lineCount}L
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Right Pane: Code Viewer */}
            <div className="lg:col-span-8 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-3.5 border-b border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900">
                <div className="flex items-center gap-2 min-w-0">
                  <FileCode className="w-4 h-4 text-indigo-500 shrink-0" />
                  <span className="text-xs font-mono font-semibold text-slate-900 dark:text-white truncate">
                    {selectedFilePath}
                  </span>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="text-xs text-slate-500 dark:text-slate-400 font-mono tabular-nums">
                    {selectedFileContent.split('\n').length} lines
                  </span>
                  <button
                    type="button"
                    onClick={() => {
                      navigator.clipboard.writeText(selectedFileContent);
                      setCopiedFile(true);
                      setTimeout(() => setCopiedFile(false), 1800);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors whitespace-nowrap"
                  >
                    {copiedFile ? (
                      <Check className="w-3.5 h-3.5 text-emerald-500" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                    <span>{copiedFile ? 'Copied' : 'Copy File'}</span>
                  </button>
                </div>
              </div>

              <div className="p-5 overflow-x-auto max-h-[680px] overflow-y-auto bg-slate-950 text-slate-100">
                {isLoadingFile ? (
                  <div className="py-12 text-center text-xs text-slate-400 font-mono">
                    Loading {selectedFilePath}...
                  </div>
                ) : (
                  <pre className="text-xs font-mono leading-relaxed tabular-nums">
                    {selectedFileContent.split('\n').map((line, idx) => (
                      <div key={idx} className="table-row hover:bg-slate-900/80">
                        <span className="table-cell pr-4 text-right text-slate-600 select-none w-10">
                          {idx + 1}
                        </span>
                        <span className="table-cell whitespace-pre">{line}</span>
                      </div>
                    ))}
                  </pre>
                )}
              </div>
            </div>
          </section>
        )}

        {/* TAB 2: SWIPEDETECTIONENGINE & ACCESSIBILITYEVENT VERIFIER */}
        {activeTab === 'verifier' && (
          <section className="space-y-8">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* Left Column: AccessibilityEvent Trace Injector */}
              <div className="lg:col-span-7 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h2 className="text-lg font-bold text-slate-900 dark:text-white font-display">
                      AccessibilityEvent Signal Verifier
                    </h2>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      Dispatch authentic AccessibilityEvent payloads through the 8-stage SwipeDetectionEngine.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={handleResetVerifierData}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors whitespace-nowrap"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>Reset Counters to 0</span>
                  </button>
                </div>

                {/* Eligible Short-Feed Navigation Scenarios */}
                <div className="space-y-2.5">
                  <div className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                    1. Eligible Short-Video Feed Navigation
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <button
                      type="button"
                      onClick={() => {
                        setYtPagerIndex(0);
                        dispatchSnapshot(
                          {
                            packageName: 'com.google.android.youtube',
                            eventType: 'TYPE_WINDOW_STATE_CHANGED',
                            viewIdResourceName: 'com.google.android.youtube:id/reel_watch_fragment_root',
                            fromIndex: 0,
                            toIndex: 0,
                            scrollDeltaY: 0,
                            hasActiveShortsOrReelsMarker: true,
                          },
                          400,
                          0
                        );
                      }}
                      className="px-3.5 py-2.5 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:border-red-500/60 bg-slate-50 dark:bg-slate-800/50 transition-colors"
                    >
                      <div className="text-xs font-semibold text-slate-900 dark:text-white">
                        Open Shorts Feed (Entry Baseline)
                      </div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                        TYPE_WINDOW_STATE_CHANGED · Not counted as swipe
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        const nextIdx = ytPagerIndex + 1;
                        setYtPagerIndex(nextIdx);
                        dispatchSnapshot(
                          {
                            packageName: 'com.google.android.youtube',
                            eventType: 'TYPE_VIEW_SCROLLED',
                            viewIdResourceName: 'com.google.android.youtube:id/reel_recycler',
                            fromIndex: nextIdx - 1,
                            toIndex: nextIdx,
                            scrollDeltaY: 1480,
                            hasActiveShortsOrReelsMarker: true,
                          },
                          750,
                          12
                        );
                      }}
                      className="px-3.5 py-2.5 text-left rounded-lg border border-red-500/40 bg-red-500/5 hover:bg-red-500/10 transition-colors"
                    >
                      <div className="text-xs font-semibold text-red-600 dark:text-red-400">
                        Swipe Next Short (Vertical)
                      </div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                        reel_recycler · index {ytPagerIndex} → {ytPagerIndex + 1} · ΔY=1480px
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setIgPagerIndex(0);
                        dispatchSnapshot(
                          {
                            packageName: 'com.instagram.android',
                            eventType: 'TYPE_WINDOW_STATE_CHANGED',
                            viewIdResourceName: 'com.instagram.android:id/clips_viewer_view_pager',
                            fromIndex: 0,
                            toIndex: 0,
                            scrollDeltaY: 0,
                            hasActiveShortsOrReelsMarker: true,
                          },
                          400,
                          0
                        );
                      }}
                      className="px-3.5 py-2.5 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:border-fuchsia-500/60 bg-slate-50 dark:bg-slate-800/50 transition-colors"
                    >
                      <div className="text-xs font-semibold text-slate-900 dark:text-white">
                        Open Reels Feed (Entry Baseline)
                      </div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                        TYPE_WINDOW_STATE_CHANGED · Not counted as swipe
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        const nextIdx = igPagerIndex + 1;
                        setIgPagerIndex(nextIdx);
                        dispatchSnapshot(
                          {
                            packageName: 'com.instagram.android',
                            eventType: 'TYPE_VIEW_SCROLLED',
                            viewIdResourceName: 'com.instagram.android:id/clips_viewer_view_pager',
                            fromIndex: nextIdx - 1,
                            toIndex: nextIdx,
                            scrollDeltaY: 1620,
                            hasActiveShortsOrReelsMarker: true,
                          },
                          750,
                          14
                        );
                      }}
                      className="px-3.5 py-2.5 text-left rounded-lg border border-fuchsia-500/40 bg-fuchsia-500/5 hover:bg-fuchsia-500/10 transition-colors"
                    >
                      <div className="text-xs font-semibold text-fuchsia-600 dark:text-fuchsia-400">
                        Swipe Next Reel (Vertical)
                      </div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                        clips_viewer_view_pager · index {igPagerIndex} → {igPagerIndex + 1} · ΔY=1620px
                      </div>
                    </button>
                  </div>
                </div>

                {/* Excluded & Deduplicated Scenarios */}
                <div className="space-y-2.5">
                  <div className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                    2. Excluded Contexts, Cooldown Bursts & Uncertain Events (Must NOT Increment Confirmed Count)
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <button
                      type="button"
                      onClick={() =>
                        dispatchSnapshot({
                          packageName: 'com.instagram.android',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.instagram.android:id/layout_comment_thread_list',
                          fromIndex: 3,
                          toIndex: 10,
                          scrollDeltaY: 680,
                          hasCommentsOrSheetOverlay: true,
                        })
                      }
                      className="px-3 py-2 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <div className="text-xs font-medium text-slate-900 dark:text-white">
                        Scroll Comments Drawer
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        Excluded: COMMENTS_OVERLAY
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        dispatchSnapshot({
                          packageName: 'com.google.android.youtube',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.google.android.youtube:id/watch_next_feed',
                          fromIndex: 1,
                          toIndex: 4,
                          scrollDeltaY: 920,
                          hasActiveShortsOrReelsMarker: false,
                          hasStandardWatchPlayerLayout: true,
                        })
                      }
                      className="px-3 py-2 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <div className="text-xs font-medium text-slate-900 dark:text-white">
                        Scroll Standard 16:9 Watch Page
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        Excluded: ORDINARY_VIDEO_WATCH
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        dispatchSnapshot({
                          packageName: 'com.google.android.youtube',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.google.android.youtube:id/results_recycler_view',
                          fromIndex: 0,
                          toIndex: 6,
                          scrollDeltaY: 840,
                          hasActiveShortsOrReelsMarker: false,
                          hasSearchInputOrGrid: true,
                        })
                      }
                      className="px-3 py-2 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <div className="text-xs font-medium text-slate-900 dark:text-white">
                        Scroll Search Results
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        Excluded: SEARCH_RESULTS
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        dispatchSnapshot({
                          packageName: 'com.instagram.android',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.instagram.android:id/reel_viewer',
                          fromIndex: 0,
                          toIndex: 1,
                          scrollDeltaX: 1080,
                          scrollDeltaY: 30,
                          hasActiveShortsOrReelsMarker: true,
                        })
                      }
                      className="px-3 py-2 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <div className="text-xs font-medium text-slate-900 dark:text-white">
                        Horizontal Stories / Carousel Swipe
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        Excluded: deltaX=1080 &gt; deltaY=30
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        dispatchSnapshot(
                          {
                            packageName: 'com.google.android.youtube',
                            eventType: 'TYPE_VIEW_SCROLLED',
                            viewIdResourceName: 'com.google.android.youtube:id/reel_recycler',
                            fromIndex: Math.max(0, ytPagerIndex - 1),
                            toIndex: ytPagerIndex,
                            scrollDeltaY: 290,
                            hasActiveShortsOrReelsMarker: true,
                          },
                          95,
                          0
                        )
                      }
                      className="px-3 py-2 text-left rounded-lg border border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <div className="text-xs font-medium text-slate-900 dark:text-white">
                        Duplicate Burst (95ms &lt; {cooldownMs}ms Cooldown)
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        Rejected: Cooldown / Signature Hash
                      </div>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        dispatchSnapshot({
                          packageName: 'com.instagram.android',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.instagram.android:id/obfuscated_container_0x7f',
                          fromIndex: 1,
                          toIndex: 2,
                          scrollDeltaY: 1250,
                          hasActiveShortsOrReelsMarker: false,
                          isFullScreenVerticalContainer: true,
                        })
                      }
                      className="px-3 py-2 text-left rounded-lg border border-amber-500/40 bg-amber-500/5 hover:bg-amber-500/10 transition-colors"
                    >
                      <div className="text-xs font-medium text-amber-600 dark:text-amber-400">
                        Ambiguous Full-Screen Scroll (Uncertain)
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono">
                        Logged as UNCERTAIN · Never silently counted
                      </div>
                    </button>
                  </div>
                </div>
              </div>

              {/* Right Column: Live Pipeline Telemetry & Counter Output */}
              <div className="lg:col-span-5 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-5">
                <h3 className="text-base font-bold text-slate-900 dark:text-white font-display">
                  Live Engine Output & Diagnostics State
                </h3>

                <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 font-mono tabular-nums">
                  <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800">
                    <div className="text-[11px] text-slate-500 dark:text-slate-400">Confirmed Total</div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">{totalSwipeCount}</div>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800">
                    <div className="text-[11px] text-red-500">Shorts Swipes</div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">{youtubeSwipeCount}</div>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800">
                    <div className="text-[11px] text-fuchsia-500">Reels Swipes</div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">{instagramSwipeCount}</div>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800">
                    <div className="text-[11px] text-slate-500 dark:text-slate-400">Excluded / Ignored</div>
                    <div className="text-xl font-semibold text-slate-700 dark:text-slate-200">{ignoredCount}</div>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800">
                    <div className="text-[11px] text-slate-500 dark:text-slate-400">Duplicates Rejected</div>
                    <div className="text-xl font-semibold text-slate-700 dark:text-slate-200">
                      {duplicateRejectedCount}
                    </div>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800">
                    <div className="text-[11px] text-amber-500">Uncertain (Unconfirmed)</div>
                    <div className="text-xl font-semibold text-amber-600 dark:text-amber-400">{uncertainCount}</div>
                  </div>
                </div>

                {/* Recent Event Evaluation Trace Log */}
                <div className="space-y-2">
                  <div className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                    Real-Time Evaluation Trace Log ({diagnosticLogs.length} events)
                  </div>
                  <div className="max-h-[290px] overflow-y-auto space-y-2 pr-1">
                    {diagnosticLogs.length === 0 ? (
                      <div className="p-4 rounded-lg bg-slate-50 dark:bg-slate-800/40 text-xs text-slate-500 dark:text-slate-400">
                        No AccessibilityEvents dispatched yet. Click any event button on the left to inspect how SwipeDetectionEngine evaluates it.
                      </div>
                    ) : (
                      diagnosticLogs.map((item) => (
                        <div
                          key={item.id}
                          className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-800 text-xs space-y-1 font-mono"
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span
                              className={`font-semibold ${
                                item.outcome.isAcceptedSwipe
                                  ? 'text-emerald-600 dark:text-emerald-400'
                                  : item.outcome.isUncertainEvent
                                  ? 'text-amber-600 dark:text-amber-400'
                                  : 'text-slate-600 dark:text-slate-400'
                              }`}
                            >
                              {item.outcome.stageReached}
                            </span>
                            <span className="text-[11px] text-slate-400 tabular-nums">
                              {item.outcome.confidence}
                            </span>
                          </div>
                          <div className="text-[11px] text-slate-600 dark:text-slate-300">
                            {item.outcome.diagnosticReason}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Automated 11-Case QA Verification Suite */}
            <div className="rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold text-slate-900 dark:text-white font-display">
                    Automated SwipeDetectionEngine QA Verification Suite ({qaResults.filter((r) => r.passed).length} / {qaResults.length} Executed & Passed)
                  </h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Executes the unit test assertions defined in <code className="font-mono">SwipeDetectionEngineTest.kt</code> against the live detection engine.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setQaResults(runAutomatedQaTestSuite())}
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-500 transition-colors whitespace-nowrap"
                >
                  <Play className="w-3.5 h-3.5" />
                  <span>Re-Run QA Suite</span>
                </button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400">
                      <th className="py-2.5 pr-4 font-medium">ID</th>
                      <th className="py-2.5 pr-4 font-medium">Test Scenario</th>
                      <th className="py-2.5 pr-4 font-medium">Category</th>
                      <th className="py-2.5 pr-4 font-medium">Expected Behavior</th>
                      <th className="py-2.5 pr-4 font-medium">Actual Engine Result</th>
                      <th className="py-2.5 text-right font-medium">Result</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800/70 font-mono tabular-nums">
                    {qaResults.map((row) => (
                      <tr key={row.id} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/40">
                        <td className="py-2.5 pr-4 font-semibold text-slate-700 dark:text-slate-300">{row.id}</td>
                        <td className="py-2.5 pr-4 font-sans font-medium text-slate-900 dark:text-white">
                          {row.name}
                        </td>
                        <td className="py-2.5 pr-4 text-slate-500 dark:text-slate-400">{row.category}</td>
                        <td className="py-2.5 pr-4 font-sans text-slate-600 dark:text-slate-300">
                          {row.expectedBehavior}
                        </td>
                        <td className="py-2.5 pr-4 text-slate-500 dark:text-slate-400">{row.actualOutcome}</td>
                        <td className="py-2.5 text-right font-semibold text-emerald-600 dark:text-emerald-400">
                          {row.passed ? 'PASS' : 'FAIL'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        )}

        {/* TAB 3: COMPOSE SCREENS & PROMINENT DISCLOSURE INSPECTOR */}
        {activeTab === 'compose-preview' && (
          <section className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Interactive Compose Screen Frame */}
            <div className="lg:col-span-5 flex flex-col items-center">
              <div className="relative w-full max-w-[410px] rounded-[28px] border-2 border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B0F17] overflow-hidden shadow-lg">
                {/* Simulated Mobile Status Bar */}
                <div className="px-5 py-2.5 flex items-center justify-between text-[11px] font-mono text-slate-500 dark:text-slate-400 border-b border-slate-200/70 dark:border-slate-800/80">
                  <span>API 35 Target</span>
                  <span>ScrollMeter Compose UI</span>
                </div>

                {/* Prominent Disclosure Modal Overlay (Store Policy Requirement) */}
                {showDisclosureModal && (
                  <div className="absolute inset-0 z-20 bg-black/70 backdrop-blur-xs flex items-center justify-center p-5">
                    <div className="w-full rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-700 p-5 space-y-3 text-xs shadow-xl">
                      <div className="text-sm font-bold text-slate-900 dark:text-white font-display">
                        Accessibility Service Disclosure
                      </div>
                      <p className="text-slate-600 dark:text-slate-300 leading-relaxed">
                        ScrollMeter requires your explicit consent to use the AccessibilityService API for core short-video swipe counting.
                      </p>
                      <p className="text-slate-600 dark:text-slate-300 leading-relaxed">
                        <strong>What Data Is Accessed:</strong> Structural scroll events strictly within supported short-video packages.
                      </p>
                      <p className="text-slate-600 dark:text-slate-300 leading-relaxed">
                        <strong>Privacy Guarantee:</strong> ScrollMeter never reads messages, passwords, or typed text, and has no internet permission—all data remains 100% offline.
                      </p>
                      <div className="flex items-center justify-end gap-2 pt-2">
                        <button
                          type="button"
                          onClick={() => setShowDisclosureModal(false)}
                          className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 font-medium"
                        >
                          Decline
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setShowDisclosureModal(false);
                            setOsPermissionEnabled(true);
                          }}
                          className="px-3.5 py-1.5 rounded-lg bg-indigo-600 text-white font-semibold"
                        >
                          Accept &amp; Enable
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* Screen Viewport */}
                <div className="p-4 min-h-[560px] max-h-[600px] overflow-y-auto space-y-3.5">
                  {/* Top Tracking Status Banner */}
                  <div
                    className={`p-3 rounded-xl border text-xs space-y-1.5 ${
                      !osPermissionEnabled
                        ? 'border-red-500/60 bg-white dark:bg-[#131B2E]'
                        : !trackingEnabled
                        ? 'border-amber-500/60 bg-white dark:bg-[#131B2E]'
                        : 'border-emerald-500/60 bg-white dark:bg-[#131B2E]'
                    }`}
                  >
                    <div className="font-semibold text-slate-900 dark:text-white">
                      {!osPermissionEnabled
                        ? 'Accessibility Permission Disabled — Tracking Stopped'
                        : !trackingEnabled
                        ? 'Tracking Paused by User'
                        : 'Tracking Active · Monitoring Short-Video Feeds'}
                    </div>
                    <div className="text-[11px] text-slate-500 dark:text-slate-400">
                      {!osPermissionEnabled
                        ? 'Review the Prominent Disclosure to enable ScrollMeter in Accessibility Settings.'
                        : `Last event: ${formatTimeOfDay(
                            confirmedSwipes[0]?.timestampMs ?? diagnosticLogs[0]?.timestampMs ?? null
                          )}`}
                    </div>
                    <div className="flex flex-wrap items-center gap-2 pt-1">
                      {!osPermissionEnabled && (
                        <button
                          type="button"
                          onClick={() => setShowDisclosureModal(true)}
                          className="px-2.5 py-1 text-[11px] font-semibold rounded-md bg-indigo-600 text-white"
                        >
                          Review Disclosure &amp; Enable
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => setComposeTab('diagnostics')}
                        className="px-2.5 py-1 text-[11px] font-medium rounded-md border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200"
                      >
                        Tracking Diagnostics
                      </button>
                      <button
                        type="button"
                        onClick={() => setComposeTab('onboarding')}
                        className="px-2.5 py-1 text-[11px] font-medium rounded-md border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200"
                      >
                        Onboarding Flow
                      </button>
                    </div>
                  </div>

                  {/* HOME DASHBOARD SCREEN */}
                  {composeTab === 'home' && (
                    <div className="space-y-3.5">
                      {activeWarningThreshold !== null && (
                        <div className="p-3 rounded-xl border border-amber-500/70 bg-white dark:bg-[#131B2E] text-xs">
                          <div className="font-semibold text-amber-600 dark:text-amber-400">
                            Daily Threshold Alert ({activeWarningThreshold}% Reached)
                          </div>
                          <div className="text-[11px] text-slate-600 dark:text-slate-300 mt-0.5">
                            {totalSwipeCount} / {dailyCombinedLimit} confirmed swipes ({remainingAllowance} remaining).
                          </div>
                        </div>
                      )}

                      <div className="p-4 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-2">
                        <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
                          Confirmed Swipes Today
                        </div>
                        <div className="text-4xl font-bold font-mono tabular-nums text-slate-900 dark:text-white">
                          {totalSwipeCount}
                        </div>
                        <div className="w-full h-2 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                          <div
                            className="h-full bg-indigo-500 transition-transform"
                            style={{
                              width: `${Math.min(100, combinedRatioPct)}%`,
                            }}
                          />
                        </div>
                        <div className="flex justify-between text-[11px] font-mono tabular-nums text-slate-500 dark:text-slate-400">
                          <span>Daily limit: {dailyCombinedLimit}</span>
                          <span>Remaining: {remainingAllowance}</span>
                        </div>
                        {uncertainCount > 0 && (
                          <div className="text-[11px] text-amber-600 dark:text-amber-400">
                            Uncertain transitions excluded from total: {uncertainCount}
                          </div>
                        )}
                      </div>

                      <div className="grid grid-cols-2 gap-3">
                        <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1.5">
                          <div className="text-xs font-semibold text-red-500">Shorts Feed</div>
                          <div className="text-2xl font-bold font-mono tabular-nums text-slate-900 dark:text-white">
                            {youtubeSwipeCount}
                          </div>
                          <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">
                            Limit: {youtubeDailyLimit} · Est. {formatDuration(youtubeDurationSeconds)}
                          </div>
                        </div>

                        <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1.5">
                          <div className="text-xs font-semibold text-fuchsia-500">Reels Feed</div>
                          <div className="text-2xl font-bold font-mono tabular-nums text-slate-900 dark:text-white">
                            {instagramSwipeCount}
                          </div>
                          <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">
                            Limit: {instagramDailyLimit} · Est. {formatDuration(instagramDurationSeconds)}
                          </div>
                        </div>
                      </div>

                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1">
                        <div className="flex justify-between items-center">
                          <span className="text-xs font-semibold text-slate-900 dark:text-white">
                            Estimated Short-Feed Time
                          </span>
                          <span className="text-sm font-bold font-mono text-indigo-500">
                            {formatDuration(totalDurationSeconds)}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-500 dark:text-slate-400">
                          Estimated strictly from verified short-video sessions (target limit: {timeLimitMinutes}m).
                        </p>
                      </div>

                      <div className="space-y-2">
                        <div className="text-xs font-semibold text-slate-900 dark:text-white">
                          Today&apos;s Activity Timeline
                        </div>
                        {confirmedSwipes.length === 0 ? (
                          <div className="p-4 rounded-xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 text-xs text-slate-500 dark:text-slate-400">
                            0 Confirmed Swipes Today. Use the test controls on the right to dispatch real AccessibilityEvent signals.
                          </div>
                        ) : (
                          confirmedSwipes.slice(0, 6).map((ev) => (
                            <div
                              key={ev.id}
                              className="px-3 py-2 rounded-xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs"
                            >
                              <div>
                                <div className="font-semibold text-slate-900 dark:text-white">
                                  {ev.platform === 'YOUTUBE_SHORTS' ? 'Shorts Feed' : 'Reels Feed'}
                                </div>
                                <div className="text-[11px] text-slate-500 font-mono">
                                  {ev.confidenceCode} ({Math.round(ev.confidenceScore * 100)}%)
                                </div>
                              </div>
                              <span className="text-[11px] font-mono text-slate-400 tabular-nums">
                                {formatTimeOfDay(ev.timestampMs)}
                              </span>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  )}

                  {/* ANALYTICS SCREEN */}
                  {composeTab === 'analytics' && (
                    <div className="space-y-3.5">
                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-2">
                        <div className="text-xs font-semibold text-slate-900 dark:text-white">
                          Platform Distribution &amp; Hourly Activity
                        </div>
                        <div className="grid grid-cols-3 gap-2 font-mono text-xs tabular-nums">
                          <div>
                            <div className="text-[11px] text-slate-500">Total</div>
                            <div className="font-bold text-slate-900 dark:text-white">{totalSwipeCount}</div>
                          </div>
                          <div>
                            <div className="text-[11px] text-red-500">Shorts</div>
                            <div className="font-bold text-slate-900 dark:text-white">{youtubeSwipeCount}</div>
                          </div>
                          <div>
                            <div className="text-[11px] text-fuchsia-500">Reels</div>
                            <div className="font-bold text-slate-900 dark:text-white">{instagramSwipeCount}</div>
                          </div>
                        </div>
                      </div>

                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-2">
                        <div className="text-xs font-semibold text-slate-900 dark:text-white">
                          Hourly Swipe Distribution (00:00 – 23:00)
                        </div>
                        {totalSwipeCount === 0 ? (
                          <div className="py-8 text-center text-xs text-slate-500">
                            0 confirmed records in Room database.
                          </div>
                        ) : (
                          <div className="h-28 flex items-end gap-1 pt-4">
                            {hourlyCounts.map((count, hr) => {
                              const maxC = Math.max(1, ...hourlyCounts);
                              const hPct = Math.max(6, Math.round((count / maxC) * 100));
                              return (
                                <div key={hr} className="flex-1 flex flex-col items-center">
                                  <div
                                    className={`w-full rounded-t ${
                                      count > 0 ? 'bg-indigo-500' : 'bg-slate-200 dark:bg-slate-800'
                                    }`}
                                    style={{ height: `${hPct}%` }}
                                  />
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                  {/* HISTORY & CSV EXPORT SCREEN */}
                  {composeTab === 'history' && (
                    <div className="space-y-3.5">
                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-2.5">
                        <div className="text-xs font-semibold text-slate-900 dark:text-white">
                          Personal Statistics Export &amp; Local Data Wipe
                        </div>
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={handleExportCsv}
                            className="flex-1 py-2 text-xs font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-500"
                          >
                            Export CSV
                          </button>
                          <button
                            type="button"
                            onClick={handleResetVerifierData}
                            className="flex-1 py-2 text-xs font-semibold rounded-lg border border-red-500/50 text-red-600 dark:text-red-400"
                          >
                            Delete All Data
                          </button>
                        </div>
                      </div>

                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1">
                        <div className="flex justify-between text-xs font-semibold text-slate-900 dark:text-white">
                          <span>{getTodayIso()} (Today)</span>
                          <span className="font-mono text-indigo-500">{totalSwipeCount} swipes</span>
                        </div>
                        <div className="text-[11px] text-slate-500 font-mono">
                          Shorts: {youtubeSwipeCount} ({formatDuration(youtubeDurationSeconds)}) · Reels:{' '}
                          {instagramSwipeCount} ({formatDuration(instagramDurationSeconds)})
                        </div>
                      </div>
                    </div>
                  )}

                  {/* SETTINGS SCREEN */}
                  {composeTab === 'settings' && (
                    <div className="space-y-3.5">
                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-2.5 text-xs">
                        <div className="font-semibold text-slate-900 dark:text-white">
                          Configurable Daily Limits
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                          <label className="space-y-1">
                            <span className="text-[11px] text-slate-500">Combined Limit</span>
                            <input
                              type="number"
                              value={dailyCombinedLimit}
                              onChange={(e) => setDailyCombinedLimit(Math.max(10, Number(e.target.value) || 10))}
                              className="w-full px-2.5 py-1.5 rounded border border-slate-200 dark:border-slate-700 bg-transparent font-mono"
                            />
                          </label>
                          <label className="space-y-1">
                            <span className="text-[11px] text-slate-500">Time Limit (min)</span>
                            <input
                              type="number"
                              value={timeLimitMinutes}
                              onChange={(e) => setTimeLimitMinutes(Math.max(5, Number(e.target.value) || 5))}
                              className="w-full px-2.5 py-1.5 rounded border border-slate-200 dark:border-slate-700 bg-transparent font-mono"
                            />
                          </label>
                        </div>
                        <div className="space-y-1 pt-1">
                          <span className="text-[11px] text-slate-500 font-mono">
                            Deduplication Cooldown: {cooldownMs}ms
                          </span>
                          <input
                            type="range"
                            min={250}
                            max={1500}
                            step={50}
                            value={cooldownMs}
                            onChange={(e) => setCooldownMs(Number(e.target.value))}
                            className="w-full"
                          />
                        </div>
                        <div className="flex items-center justify-between pt-1">
                          <span>Active Tracking Enabled</span>
                          <input
                            type="checkbox"
                            checked={trackingEnabled}
                            onChange={(e) => setTrackingEnabled(e.target.checked)}
                          />
                        </div>
                      </div>
                    </div>
                  )}

                  {/* DIAGNOSTICS SCREEN */}
                  {composeTab === 'diagnostics' && (
                    <div className="space-y-3 text-xs">
                      <div className="p-3.5 rounded-2xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1.5 font-mono">
                        <div className="font-sans font-semibold text-slate-900 dark:text-white">
                          Tracking Diagnostics &amp; Service Health
                        </div>
                        <div>OS Accessibility Permission: {osPermissionEnabled ? 'ENABLED' : 'DISABLED'}</div>
                        <div>Confirmed Swipes Accepted: {totalSwipeCount}</div>
                        <div>Excluded Non-Short Events: {ignoredCount}</div>
                        <div>Duplicate Burst Rejected: {duplicateRejectedCount}</div>
                        <div className="text-amber-500">Uncertain Events (Not Counted): {uncertainCount}</div>
                      </div>
                    </div>
                  )}

                  {/* ONBOARDING SCREEN */}
                  {composeTab === 'onboarding' && (
                    <div className="space-y-2.5 text-xs">
                      <div className="p-3 rounded-xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1">
                        <div className="font-semibold text-slate-900 dark:text-white">
                          1. Prominent Disclosure &amp; Consent
                        </div>
                        <p className="text-[11px] text-slate-500 dark:text-slate-400">
                          Presents an explicit Accept / Decline consent dialog before opening system Accessibility Settings.
                        </p>
                      </div>
                      <div className="p-3 rounded-xl bg-white dark:bg-[#131B2E] border border-slate-200 dark:border-slate-800 space-y-1">
                        <div className="font-semibold text-slate-900 dark:text-white">
                          2. 100% Local Offline Privacy
                        </div>
                        <p className="text-[11px] text-slate-500 dark:text-slate-400">
                          Zero internet permission in AndroidManifest.xml. Never reads messages, passwords, or typed text.
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => setShowDisclosureModal(true)}
                        className="w-full py-2 font-semibold rounded-lg bg-indigo-600 text-white"
                      >
                        Preview Prominent Disclosure Dialog
                      </button>
                    </div>
                  )}
                </div>

                {/* Bottom Navigation Bar (Home, Analytics, History, Settings) */}
                <div className="grid grid-cols-4 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-[#131B2E] py-2 text-[11px] font-medium">
                  {(['home', 'analytics', 'history', 'settings'] as ComposeBottomTab[]).map((tab) => (
                    <button
                      key={tab}
                      type="button"
                      onClick={() => setComposeTab(tab)}
                      className={`py-1.5 capitalize transition-colors ${
                        composeTab === tab
                          ? 'text-indigo-600 dark:text-indigo-400 font-semibold'
                          : 'text-slate-500 dark:text-slate-400'
                      }`}
                    >
                      {tab}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Right Column: Live State & Quick AccessibilityEvent Controls */}
            <div className="lg:col-span-7 space-y-6">
              <div className="rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-4">
                <h2 className="text-lg font-bold text-slate-900 dark:text-white font-display">
                  Interactive Compose State &amp; Prominent Disclosure Inspector
                </h2>
                <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
                  The dashboard initializes at <strong className="font-mono">0 confirmed swipes</strong> rather than displaying hardcoded mock metrics.
                  Use the controls below to trigger real <code className="font-mono">SwipeDetectionEngine</code> evaluations or preview the store-compliant{' '}
                  <code className="font-mono">AccessibilityProminentDisclosureDialog</code>.
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      const nextIdx = ytPagerIndex + 1;
                      setYtPagerIndex(nextIdx);
                      dispatchSnapshot(
                        {
                          packageName: 'com.google.android.youtube',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.google.android.youtube:id/reel_recycler',
                          fromIndex: nextIdx - 1,
                          toIndex: nextIdx,
                          scrollDeltaY: 1480,
                          hasActiveShortsOrReelsMarker: true,
                        },
                        750,
                        15
                      );
                    }}
                    className="p-3.5 text-left rounded-xl border border-red-500/40 bg-red-500/5 hover:bg-red-500/10 transition-colors"
                  >
                    <div className="text-xs font-semibold text-red-600 dark:text-red-400">
                      +1 Shorts Vertical Swipe
                    </div>
                    <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                      Dispatches TYPE_VIEW_SCROLLED on reel_recycler
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      const nextIdx = igPagerIndex + 1;
                      setIgPagerIndex(nextIdx);
                      dispatchSnapshot(
                        {
                          packageName: 'com.instagram.android',
                          eventType: 'TYPE_VIEW_SCROLLED',
                          viewIdResourceName: 'com.instagram.android:id/clips_viewer_view_pager',
                          fromIndex: nextIdx - 1,
                          toIndex: nextIdx,
                          scrollDeltaY: 1620,
                          hasActiveShortsOrReelsMarker: true,
                        },
                        750,
                        15
                      );
                    }}
                    className="p-3.5 text-left rounded-xl border border-fuchsia-500/40 bg-fuchsia-500/5 hover:bg-fuchsia-500/10 transition-colors"
                  >
                    <div className="text-xs font-semibold text-fuchsia-600 dark:text-fuchsia-400">
                      +1 Reels Vertical Swipe
                    </div>
                    <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                      Dispatches TYPE_VIEW_SCROLLED on clips_viewer_view_pager
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setShowDisclosureModal(true)}
                    className="p-3.5 text-left rounded-xl border border-indigo-500/40 bg-indigo-500/5 hover:bg-indigo-500/10 transition-colors"
                  >
                    <div className="text-xs font-semibold text-indigo-600 dark:text-indigo-400">
                      Preview Prominent Disclosure Dialog
                    </div>
                    <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                      Mandatory 2-button consent before OS Settings
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={handleResetVerifierData}
                    className="p-3.5 text-left rounded-xl border border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                  >
                    <div className="text-xs font-semibold text-slate-900 dark:text-white flex items-center gap-1.5">
                      <Trash2 className="w-3.5 h-3.5 text-red-500" />
                      <span>Wipe All Local Records (Reset to 0)</span>
                    </div>
                    <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                      Verifies empty-state rendering across all screens
                    </div>
                  </button>
                </div>
              </div>

              {/* Privacy & Publishing Compliance Summary */}
              <div className="rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-slate-900 dark:text-white font-display">
                  <ShieldCheck className="w-4 h-4 text-emerald-500" />
                  <span>Publishing &amp; Store Review Readiness</span>
                </div>
                <ul className="text-xs text-slate-600 dark:text-slate-300 space-y-2 leading-relaxed">
                  <li>
                    <strong>Zero Reserved / Prohibited Terms in App Metadata:</strong>{' '}
                    <code className="font-mono">metadata.json</code> and <code className="font-mono">index.html</code> use clean, trademark-free product copy (<code className="font-mono">ScrollMeter</code>).
                  </li>
                  <li>
                    <strong>Prominent Disclosure Dialog:</strong>{' '}
                    <code className="font-mono">AccessibilityProminentDisclosureDialog.kt</code> enforces explicit Accept/Decline consent before invoking <code className="font-mono">ACTION_ACCESSIBILITY_SETTINGS</code>.
                  </li>
                  <li>
                    <strong>Accessibility Tool Declaration:</strong>{' '}
                    <code className="font-mono">accessibility_service_config.xml</code> sets{' '}
                    <code className="font-mono">android:isAccessibilityTool=&quot;false&quot;</code> as required for digital wellbeing utilities on API 31+.
                  </li>
                  <li>
                    <strong>Adaptive Launcher Icons &amp; Release Signing:</strong> Includes vector adaptive icons (<code className="font-mono">@mipmap/ic_launcher</code>), <code className="font-mono">backup_rules.xml</code>, and <code className="font-mono">keystore.properties</code> signing configuration.
                  </li>
                </ul>
              </div>
            </div>
          </section>
        )}

        {/* TAB 4: BUILD & PUBLISHING GUIDE */}
        {activeTab === 'build-guide' && (
          <section className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            <div className="lg:col-span-8 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-6">
              <div className="space-y-1">
                <h2 className="text-xl font-bold text-slate-900 dark:text-white font-display">
                  Publishing Compliance &amp; Release AAB / APK Build Guide
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  All fixes applied to eliminate &quot;No reserved or prohibited terms&quot; errors and pass store review.
                </p>
              </div>

              <div className="space-y-4 text-sm text-slate-700 dark:text-slate-300">
                <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-800 space-y-2">
                  <div className="font-semibold text-slate-900 dark:text-white">
                    01. Resolved: &quot;No Reserved or Prohibited Terms&quot; in Publishing Metadata
                  </div>
                  <p className="text-xs leading-relaxed">
                    Publishing validators block app names and descriptions containing third-party or platform trademarks.{' '}
                    <code className="font-mono">metadata.json</code>, <code className="font-mono">index.html</code>, and{' '}
                    <code className="font-mono">strings.xml</code> have been updated with clean, trademark-free copy:
                  </p>
                  <pre className="p-3 rounded-lg bg-slate-950 text-slate-100 text-xs font-mono overflow-x-auto">
{`Name: ScrollMeter
Description: Know every swipe and take back your time with real-time short-video vertical scroll tracking, daily limits, usage analytics, and offline data storage.`}
                  </pre>
                </div>

                <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-800 space-y-2">
                  <div className="font-semibold text-slate-900 dark:text-white">
                    02. Resolved: AccessibilityService Prominent Disclosure &amp; Manifest Requirements
                  </div>
                  <p className="text-xs leading-relaxed">
                    Added <code className="font-mono">AccessibilityProminentDisclosureDialog.kt</code> (requiring explicit user acceptance before opening system Accessibility Settings), set <code className="font-mono">android:isAccessibilityTool=&quot;false&quot;</code> in <code className="font-mono">accessibility_service_config.xml</code>, and added adaptive launcher icons (<code className="font-mono">@mipmap/ic_launcher</code>) plus <code className="font-mono">backup_rules.xml</code>.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-800 space-y-2">
                  <div className="font-semibold text-slate-900 dark:text-white">
                    03. Build Signed Release Bundle (.aab) or APK (.apk)
                  </div>
                  <pre className="p-3 rounded-lg bg-slate-950 text-slate-100 text-xs font-mono overflow-x-auto">
{`# 1. Run JVM Unit Tests
./gradlew testDebugUnitTest

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Build Signed Release App Bundle (.aab) for Store Upload
./gradlew bundleRelease

# 4. Build Signed Release APK (.apk)
./gradlew assembleRelease`}
                  </pre>
                </div>
              </div>
            </div>

            <div className="lg:col-span-4 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-6 space-y-4">
              <div className="flex items-center gap-2 text-sm font-bold text-slate-900 dark:text-white font-display">
                <Smartphone className="w-4 h-4 text-indigo-500" />
                <span>Inspect Compliance Files</span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Jump directly to the updated publishing and release files in the Source Explorer:
              </p>
              <div className="space-y-2">
                {[
                  {
                    label: 'PUBLISHING_COMPLIANCE.md',
                    path: 'PUBLISHING_COMPLIANCE.md',
                  },
                  {
                    label: 'AccessibilityProminentDisclosureDialog.kt',
                    path: 'app/src/main/java/com/scrollmeter/app/ui/onboarding/AccessibilityProminentDisclosureDialog.kt',
                  },
                  {
                    label: 'AndroidManifest.xml',
                    path: 'app/src/main/AndroidManifest.xml',
                  },
                  {
                    label: 'accessibility_service_config.xml',
                    path: 'app/src/main/res/xml/accessibility_service_config.xml',
                  },
                  {
                    label: 'strings.xml (Disclosure & Disclaimer)',
                    path: 'app/src/main/res/values/strings.xml',
                  },
                  {
                    label: 'app/build.gradle.kts (Release Signing)',
                    path: 'app/build.gradle.kts',
                  },
                ].map((item) => (
                  <button
                    key={item.path}
                    type="button"
                    onClick={() => {
                      setSelectedFilePath(item.path);
                      setActiveTab('explorer');
                    }}
                    className="w-full text-left px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-indigo-500/50 text-xs font-mono text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors flex items-center justify-between"
                  >
                    <span className="truncate">{item.label}</span>
                    <Terminal className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  </button>
                ))}
              </div>
            </div>
          </section>
        )}
      </main>
    </div>
  );
}

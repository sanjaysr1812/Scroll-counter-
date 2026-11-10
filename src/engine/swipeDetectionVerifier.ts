/**
 * Exact TypeScript execution mirror of com.scrollmeter.app.accessibility.SwipeDetectionEngine
 * and FeedContextDetector for live interactive verification of AccessibilityEvent traces
 * and executing the 11-case QA test suite in the Android Studio Project Workbench.
 */

export type PlatformId = 'YOUTUBE_SHORTS' | 'INSTAGRAM_REELS';

export type DetectionConfidenceCode =
  | 'CONFIRMED_HIGH'
  | 'CONFIRMED_MEDIUM'
  | 'UNCERTAIN'
  | 'IGNORED_NON_SHORT_FEED'
  | 'UNAVAILABLE';

export type FeedContextCode =
  | 'YOUTUBE_SHORTS_FEED'
  | 'INSTAGRAM_REELS_FEED'
  | 'COMMENTS_OVERLAY'
  | 'DESCRIPTION_OR_SHARE_OVERLAY'
  | 'SEARCH_RESULTS'
  | 'PROFILE_GRID'
  | 'ORDINARY_VIDEO_WATCH'
  | 'HORIZONTAL_STORIES_OR_CAROUSEL'
  | 'HOME_OR_OTHER_SCREEN'
  | 'UNKNOWN_CONTEXT';

export interface AccessibilitySignalSnapshot {
  packageName: string;
  eventType: 'TYPE_VIEW_SCROLLED' | 'TYPE_WINDOW_STATE_CHANGED' | 'TYPE_WINDOW_CONTENT_CHANGED';
  eventTimestampMs: number;
  className: string;
  viewIdResourceName: string;
  fromIndex: number;
  toIndex: number;
  scrollDeltaX: number;
  scrollDeltaY: number;
  isFullScreenVerticalContainer: boolean;
  containerHeightRatio: number;
  containerWidthRatio: number;
  hasActiveShortsOrReelsMarker: boolean;
  hasCommentsOrSheetOverlay: boolean;
  hasSearchInputOrGrid: boolean;
  hasProfileGridPattern: boolean;
  hasStandardWatchPlayerLayout: boolean;
  isScreenInteractive: boolean;
  structuralNodeSignature: number;
}

export interface SwipeEvaluationOutcome {
  platform: PlatformId | null;
  feedContext: FeedContextCode;
  confidence: DetectionConfidenceCode;
  confidenceScore: number;
  isAcceptedSwipe: boolean;
  isDuplicateRejected: boolean;
  isUncertainEvent: boolean;
  eventSignatureHash: string;
  diagnosticReason: string;
  stageReached: string;
}

const YOUTUBE_SHORTS_TOKENS = [
  'reel_recycler',
  'reel_player_page_container',
  'shorts_player',
  'reel_watch_fragment_root',
  'reel_multi_format_link',
  'shorts_pivot_button',
];

const INSTAGRAM_REELS_TOKENS = [
  'clips_viewer_view_pager',
  'clips_video_container',
  'reel_viewer',
  'clips_item_overlay',
  'clips_tab',
];

const COMMENTS_TOKENS = [
  'engagement_panel',
  'comment_composer',
  'comments_list',
  'row_comment',
  'layout_comment_thread',
  'bottom_sheet_container',
  'design_bottom_sheet',
  'description_content',
  'share_sheet',
];

const SEARCH_TOKENS = [
  'search_edit_text',
  'search_typeahead',
  'action_bar_search_edit_text',
  'results_recycler_view',
];

const PROFILE_TOKENS = ['profile_tab_layout', 'profile_header', 'channel_header', 'grid_card'];

const STANDARD_WATCH_TOKENS = [
  'watch_while_layout',
  'player_fragment_container',
  'watch_next_feed',
  'related_chip_cloud',
];

interface PlatformTrackerState {
  isInsideEligibleFeed: boolean;
  feedEnteredAtMs: number;
  lastAcceptedSwipeTimestampMs: number;
  lastPagerIndex: number;
  lastStructuralSignature: number;
  recentSignatureHashes: string[];
}

export class SwipeDetectionVerifierEngine {
  public cooldownMs: number;
  private stateByPlatform: Record<PlatformId, PlatformTrackerState>;

  constructor(cooldownMs = 450) {
    this.cooldownMs = Math.max(250, Math.min(1500, cooldownMs));
    this.stateByPlatform = {
      YOUTUBE_SHORTS: this.freshState(),
      INSTAGRAM_REELS: this.freshState(),
    };
  }

  private freshState(): PlatformTrackerState {
    return {
      isInsideEligibleFeed: false,
      feedEnteredAtMs: 0,
      lastAcceptedSwipeTimestampMs: -10000,
      lastPagerIndex: -1,
      lastStructuralSignature: 0,
      recentSignatureHashes: [],
    };
  }

  public resetState(): void {
    this.stateByPlatform.YOUTUBE_SHORTS = this.freshState();
    this.stateByPlatform.INSTAGRAM_REELS = this.freshState();
  }

  public evaluate(snapshot: AccessibilitySignalSnapshot): SwipeEvaluationOutcome {
    // Stage 1: Package verification
    const platform: PlatformId | null =
      snapshot.packageName === 'com.google.android.youtube'
        ? 'YOUTUBE_SHORTS'
        : snapshot.packageName === 'com.instagram.android'
        ? 'INSTAGRAM_REELS'
        : null;

    if (!platform) {
      return {
        platform: null,
        feedContext: 'UNKNOWN_CONTEXT',
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: `Ignored non-target package: ${snapshot.packageName}`,
        stageReached: 'Stage 1: Package Allowlist Verification',
      };
    }

    const tracker = this.stateByPlatform[platform];

    // Stage 2: App foreground & interactive screen validation
    if (!snapshot.isScreenInteractive) {
      tracker.isInsideEligibleFeed = false;
      return {
        platform,
        feedContext: 'UNKNOWN_CONTEXT',
        confidence: 'UNAVAILABLE',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Ignored event while device screen is locked / non-interactive',
        stageReached: 'Stage 2: Screen Interactive Check',
      };
    }

    // Stage 3 & 4: Feed context & Exclusion checks
    const viewIdLower = snapshot.viewIdResourceName.toLowerCase();
    const classLower = snapshot.className.toLowerCase();

    if (
      snapshot.hasCommentsOrSheetOverlay ||
      COMMENTS_TOKENS.some((t) => viewIdLower.includes(t))
    ) {
      return {
        platform,
        feedContext: 'COMMENTS_OVERLAY',
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Comments drawer or engagement bottom sheet active (excluded)',
        stageReached: 'Stage 4: Exclusion Filter (Comments/Sheet)',
      };
    }

    if (snapshot.hasSearchInputOrGrid || SEARCH_TOKENS.some((t) => viewIdLower.includes(t))) {
      tracker.isInsideEligibleFeed = false;
      return {
        platform,
        feedContext: 'SEARCH_RESULTS',
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Search input or search results list active (excluded)',
        stageReached: 'Stage 4: Exclusion Filter (Search)',
      };
    }

    if (snapshot.hasProfileGridPattern || PROFILE_TOKENS.some((t) => viewIdLower.includes(t))) {
      tracker.isInsideEligibleFeed = false;
      return {
        platform,
        feedContext: 'PROFILE_GRID',
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Profile header or 3-column media grid detected (excluded)',
        stageReached: 'Stage 4: Exclusion Filter (Profile Grid)',
      };
    }

    if (
      platform === 'YOUTUBE_SHORTS' &&
      (snapshot.hasStandardWatchPlayerLayout ||
        STANDARD_WATCH_TOKENS.some((t) => viewIdLower.includes(t))) &&
      !snapshot.hasActiveShortsOrReelsMarker
    ) {
      tracker.isInsideEligibleFeed = false;
      return {
        platform,
        feedContext: 'ORDINARY_VIDEO_WATCH',
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Standard YouTube 16:9 watch page detected (excluded)',
        stageReached: 'Stage 4: Exclusion Filter (Standard Watch Page)',
      };
    }

    const absDx = Math.abs(snapshot.scrollDeltaX);
    const absDy = Math.abs(snapshot.scrollDeltaY);
    if (absDx > 0 && absDx > absDy * 1.25) {
      return {
        platform,
        feedContext: 'HORIZONTAL_STORIES_OR_CAROUSEL',
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: `Horizontal scroll vector rejected (deltaX=${absDx} > deltaY=${absDy})`,
        stageReached: 'Stage 4: Exclusion Filter (Horizontal Vector)',
      };
    }

    const hasKnownToken =
      platform === 'YOUTUBE_SHORTS'
        ? YOUTUBE_SHORTS_TOKENS.some((t) => viewIdLower.includes(t))
        : INSTAGRAM_REELS_TOKENS.some((t) => viewIdLower.includes(t));

    const isPagerOrRecycler =
      classLower.includes('viewpager') ||
      classLower.includes('recyclerview') ||
      classLower.includes('framelayout') ||
      classLower.includes('viewgroup');

    const isFullHeight =
      snapshot.isFullScreenVerticalContainer || snapshot.containerHeightRatio >= 0.8;

    const feedContext: FeedContextCode =
      (hasKnownToken || snapshot.hasActiveShortsOrReelsMarker) && (isFullHeight || isPagerOrRecycler)
        ? platform === 'YOUTUBE_SHORTS'
          ? 'YOUTUBE_SHORTS_FEED'
          : 'INSTAGRAM_REELS_FEED'
        : isFullHeight && isPagerOrRecycler && snapshot.containerWidthRatio >= 0.9
        ? 'UNKNOWN_CONTEXT'
        : 'HOME_OR_OTHER_SCREEN';

    if (feedContext !== 'YOUTUBE_SHORTS_FEED' && feedContext !== 'INSTAGRAM_REELS_FEED') {
      if (feedContext === 'UNKNOWN_CONTEXT' && snapshot.eventType === 'TYPE_VIEW_SCROLLED' && isFullHeight) {
        return {
          platform,
          feedContext: 'UNKNOWN_CONTEXT',
          confidence: 'UNCERTAIN',
          confidenceScore: 0.42,
          isAcceptedSwipe: false,
          isDuplicateRejected: false,
          isUncertainEvent: true,
          eventSignatureHash: '',
          diagnosticReason:
            'Uncertain scroll inside full-screen container without verified Shorts/Reels marker (not counted as confirmed swipe)',
          stageReached: 'Stage 4: Uncertainty Guard',
        };
      }
      tracker.isInsideEligibleFeed = false;
      return {
        platform,
        feedContext,
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Main home feed or unrelated screen (excluded)',
        stageReached: 'Stage 3: Feed Context Identification',
      };
    }

    // Stage 5: Initial feed entry baseline
    if (!tracker.isInsideEligibleFeed) {
      tracker.isInsideEligibleFeed = true;
      tracker.feedEnteredAtMs = snapshot.eventTimestampMs;
      tracker.lastPagerIndex = snapshot.toIndex >= 0 ? snapshot.toIndex : snapshot.fromIndex;
      tracker.lastStructuralSignature = snapshot.structuralNodeSignature;

      if (snapshot.eventType !== 'TYPE_VIEW_SCROLLED' || absDy < 140) {
        return {
          platform,
          feedContext,
          confidence: 'IGNORED_NON_SHORT_FEED',
          confidenceScore: 0,
          isAcceptedSwipe: false,
          isDuplicateRejected: false,
          isUncertainEvent: false,
          eventSignatureHash: '',
          diagnosticReason:
            'Initial Shorts/Reels feed entry baseline established (opening video without swiping is not counted)',
          stageReached: 'Stage 5: Initial Entry Baseline',
        };
      }
    }

    // Stage 6: Vertical navigation signal check
    const currentIndex = snapshot.toIndex >= 0 ? snapshot.toIndex : snapshot.fromIndex >= 0 ? snapshot.fromIndex : -1;
    const hasIndexAdvancement =
      currentIndex >= 0 && tracker.lastPagerIndex >= 0 && currentIndex !== tracker.lastPagerIndex;
    const hasFirstIndexScroll =
      currentIndex > 0 && tracker.lastPagerIndex === -1 && snapshot.eventType === 'TYPE_VIEW_SCROLLED';
    const hasSignificantVerticalDelta =
      snapshot.eventType === 'TYPE_VIEW_SCROLLED' && absDy >= 140 && absDy > absDx * 1.5;
    const hasCustomPagerTransition =
      snapshot.eventType === 'TYPE_WINDOW_CONTENT_CHANGED' &&
      isFullHeight &&
      snapshot.structuralNodeSignature !== 0 &&
      tracker.lastStructuralSignature !== 0 &&
      snapshot.structuralNodeSignature !== tracker.lastStructuralSignature &&
      snapshot.eventTimestampMs - tracker.feedEnteredAtMs > 600;

    if (!hasIndexAdvancement && !hasFirstIndexScroll && !hasSignificantVerticalDelta && !hasCustomPagerTransition) {
      return {
        platform,
        feedContext,
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: false,
        isUncertainEvent: false,
        eventSignatureHash: '',
        diagnosticReason: 'Sub-threshold scroll or non-navigation layout event',
        stageReached: 'Stage 6: Vertical Delta & Pager Index Check',
      };
    }

    // Stage 7: Cooldown & Duplicate suppression
    const elapsed = snapshot.eventTimestampMs - tracker.lastAcceptedSwipeTimestampMs;
    const timeBucket = Math.floor(snapshot.eventTimestampMs / this.cooldownMs);
    const signatureHash = `${platform}:${currentIndex >= 0 ? `idx_${currentIndex}` : `bucket_${timeBucket}`}:${
      snapshot.scrollDeltaY >= 0 ? 'down' : 'up'
    }`;

    if (
      (elapsed >= 0 && elapsed < this.cooldownMs) ||
      tracker.recentSignatureHashes.includes(signatureHash)
    ) {
      return {
        platform,
        feedContext,
        confidence: 'IGNORED_NON_SHORT_FEED',
        confidenceScore: 0,
        isAcceptedSwipe: false,
        isDuplicateRejected: true,
        isUncertainEvent: false,
        eventSignatureHash: signatureHash,
        diagnosticReason: `Duplicate/cooldown burst suppressed (${elapsed}ms < ${this.cooldownMs}ms cooldown)`,
        stageReached: 'Stage 7: Deduplication & Cooldown Gate',
      };
    }

    // Stage 8: Confidence classification & commit
    const isHighConfidence = hasIndexAdvancement || hasFirstIndexScroll;
    const confidence: DetectionConfidenceCode = isHighConfidence
      ? 'CONFIRMED_HIGH'
      : 'CONFIRMED_MEDIUM';
    const confidenceScore = isHighConfidence ? 0.94 : 0.78;

    tracker.lastAcceptedSwipeTimestampMs = snapshot.eventTimestampMs;
    if (currentIndex >= 0) tracker.lastPagerIndex = currentIndex;
    if (snapshot.structuralNodeSignature !== 0) {
      tracker.lastStructuralSignature = snapshot.structuralNodeSignature;
    }
    if (tracker.recentSignatureHashes.length >= 16) {
      tracker.recentSignatureHashes.shift();
    }
    tracker.recentSignatureHashes.push(signatureHash);

    return {
      platform,
      feedContext,
      confidence,
      confidenceScore,
      isAcceptedSwipe: true,
      isDuplicateRejected: false,
      isUncertainEvent: false,
      eventSignatureHash: signatureHash,
      diagnosticReason: `Accepted ${
        platform === 'YOUTUBE_SHORTS' ? 'YouTube Shorts' : 'Instagram Reels'
      } vertical swipe (${confidence})`,
      stageReached: 'Stage 8: Confirmed Swipe Committed',
    };
  }
}

export interface QaTestCaseResult {
  id: string;
  name: string;
  category: string;
  expectedBehavior: string;
  actualOutcome: string;
  passed: boolean;
}

/**
 * Executes the 11-case automated QA test suite matching SwipeDetectionEngineTest.kt and DateTimeUtilsTest.kt.
 */
export function runAutomatedQaTestSuite(): QaTestCaseResult[] {
  const results: QaTestCaseResult[] = [];
  const engine = new SwipeDetectionVerifierEngine(450);

  // Test 1: Opening YouTube Shorts without swiping
  const t1 = engine.evaluate({
    packageName: 'com.google.android.youtube',
    eventType: 'TYPE_WINDOW_STATE_CHANGED',
    eventTimestampMs: 1000,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.google.android.youtube:id/reel_watch_fragment_root',
    fromIndex: 0,
    toIndex: 0,
    scrollDeltaX: 0,
    scrollDeltaY: 0,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.92,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 100,
  });
  results.push({
    id: 'QA-01',
    name: 'Opening YouTube Shorts without swiping',
    category: 'Exclusion',
    expectedBehavior: 'Establishes feed baseline; isAcceptedSwipe = false',
    actualOutcome: `isAcceptedSwipe=${t1.isAcceptedSwipe} (${t1.stageReached})`,
    passed: t1.isAcceptedSwipe === false && t1.feedContext === 'YOUTUBE_SHORTS_FEED',
  });

  // Test 2: Vertical swipe in YouTube Shorts
  const t2 = engine.evaluate({
    packageName: 'com.google.android.youtube',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 2200,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.google.android.youtube:id/reel_recycler',
    fromIndex: 0,
    toIndex: 1,
    scrollDeltaX: 0,
    scrollDeltaY: 1420,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.92,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 101,
  });
  results.push({
    id: 'QA-02',
    name: 'YouTube Shorts vertical swipe (index 0 -> 1)',
    category: 'Detection',
    expectedBehavior: 'Accepted as CONFIRMED_HIGH for YOUTUBE_SHORTS',
    actualOutcome: `isAcceptedSwipe=${t2.isAcceptedSwipe}, confidence=${t2.confidence}`,
    passed: t2.isAcceptedSwipe === true && t2.confidence === 'CONFIRMED_HIGH' && t2.platform === 'YOUTUBE_SHORTS',
  });

  // Test 3: Duplicate event burst within 450ms cooldown
  const t3 = engine.evaluate({
    packageName: 'com.google.android.youtube',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 2340,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.google.android.youtube:id/reel_recycler',
    fromIndex: 0,
    toIndex: 1,
    scrollDeltaX: 0,
    scrollDeltaY: 310,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.92,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 101,
  });
  results.push({
    id: 'QA-03',
    name: 'Duplicate fling burst suppression (140ms < 450ms cooldown)',
    category: 'Deduplication',
    expectedBehavior: 'Rejected as duplicate (isDuplicateRejected = true)',
    actualOutcome: `isDuplicateRejected=${t3.isDuplicateRejected}, isAcceptedSwipe=${t3.isAcceptedSwipe}`,
    passed: t3.isDuplicateRejected === true && t3.isAcceptedSwipe === false,
  });

  // Test 4: Rapid consecutive swipe after cooldown (550ms later)
  const t4 = engine.evaluate({
    packageName: 'com.google.android.youtube',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 2750,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.google.android.youtube:id/reel_recycler',
    fromIndex: 1,
    toIndex: 2,
    scrollDeltaX: 0,
    scrollDeltaY: 1480,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.92,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 102,
  });
  results.push({
    id: 'QA-04',
    name: 'Rapid consecutive swipe after cooldown (550ms elapsed)',
    category: 'Detection',
    expectedBehavior: 'Accepted as CONFIRMED_HIGH (index 1 -> 2)',
    actualOutcome: `isAcceptedSwipe=${t4.isAcceptedSwipe}, confidence=${t4.confidence}`,
    passed: t4.isAcceptedSwipe === true && t4.confidence === 'CONFIRMED_HIGH',
  });

  // Test 5: Instagram Reels entry + vertical swipe
  engine.evaluate({
    packageName: 'com.instagram.android',
    eventType: 'TYPE_WINDOW_STATE_CHANGED',
    eventTimestampMs: 5000,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.instagram.android:id/clips_viewer_view_pager',
    fromIndex: 0,
    toIndex: 0,
    scrollDeltaX: 0,
    scrollDeltaY: 0,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.94,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 200,
  });
  const t5 = engine.evaluate({
    packageName: 'com.instagram.android',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 6100,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.instagram.android:id/clips_viewer_view_pager',
    fromIndex: 0,
    toIndex: 1,
    scrollDeltaX: 0,
    scrollDeltaY: 1620,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.94,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 201,
  });
  results.push({
    id: 'QA-05',
    name: 'Instagram Reels vertical swipe detection',
    category: 'Detection',
    expectedBehavior: 'Accepted as CONFIRMED_HIGH for INSTAGRAM_REELS',
    actualOutcome: `isAcceptedSwipe=${t5.isAcceptedSwipe}, platform=${t5.platform}`,
    passed: t5.isAcceptedSwipe === true && t5.platform === 'INSTAGRAM_REELS',
  });

  // Test 6: Scrolling Instagram comments drawer
  const t6 = engine.evaluate({
    packageName: 'com.instagram.android',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 8000,
    className: 'androidx.recyclerview.widget.RecyclerView',
    viewIdResourceName: 'com.instagram.android:id/layout_comment_thread_list',
    fromIndex: 2,
    toIndex: 9,
    scrollDeltaX: 0,
    scrollDeltaY: 740,
    isFullScreenVerticalContainer: false,
    containerHeightRatio: 0.55,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: true,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 205,
  });
  results.push({
    id: 'QA-06',
    name: 'Instagram comments sheet scrolling exclusion',
    category: 'Exclusion',
    expectedBehavior: 'Classified as COMMENTS_OVERLAY; isAcceptedSwipe = false',
    actualOutcome: `feedContext=${t6.feedContext}, isAcceptedSwipe=${t6.isAcceptedSwipe}`,
    passed: t6.isAcceptedSwipe === false && t6.feedContext === 'COMMENTS_OVERLAY',
  });

  // Test 7: Ordinary YouTube video watch page scrolling exclusion
  const t7 = engine.evaluate({
    packageName: 'com.google.android.youtube',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 9500,
    className: 'androidx.recyclerview.widget.RecyclerView',
    viewIdResourceName: 'com.google.android.youtube:id/watch_next_feed',
    fromIndex: 1,
    toIndex: 5,
    scrollDeltaX: 0,
    scrollDeltaY: 880,
    isFullScreenVerticalContainer: false,
    containerHeightRatio: 0.62,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: false,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: true,
    isScreenInteractive: true,
    structuralNodeSignature: 301,
  });
  results.push({
    id: 'QA-07',
    name: 'Ordinary YouTube video watch-page scrolling exclusion',
    category: 'Exclusion',
    expectedBehavior: 'Classified as ORDINARY_VIDEO_WATCH; isAcceptedSwipe = false',
    actualOutcome: `feedContext=${t7.feedContext}, isAcceptedSwipe=${t7.isAcceptedSwipe}`,
    passed: t7.isAcceptedSwipe === false && t7.feedContext === 'ORDINARY_VIDEO_WATCH',
  });

  // Test 8: Search page scrolling exclusion
  const t8 = engine.evaluate({
    packageName: 'com.google.android.youtube',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 11000,
    className: 'androidx.recyclerview.widget.RecyclerView',
    viewIdResourceName: 'com.google.android.youtube:id/results_recycler_view',
    fromIndex: 0,
    toIndex: 6,
    scrollDeltaX: 0,
    scrollDeltaY: 920,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.85,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: false,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: true,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 401,
  });
  results.push({
    id: 'QA-08',
    name: 'Search results scrolling exclusion',
    category: 'Exclusion',
    expectedBehavior: 'Classified as SEARCH_RESULTS; isAcceptedSwipe = false',
    actualOutcome: `feedContext=${t8.feedContext}, isAcceptedSwipe=${t8.isAcceptedSwipe}`,
    passed: t8.isAcceptedSwipe === false && t8.feedContext === 'SEARCH_RESULTS',
  });

  // Test 9: Horizontal carousel / Stories swipe exclusion
  const t9 = engine.evaluate({
    packageName: 'com.instagram.android',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 12500,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.instagram.android:id/reel_viewer',
    fromIndex: 0,
    toIndex: 1,
    scrollDeltaX: 1080,
    scrollDeltaY: 35,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.92,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: true,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 501,
  });
  results.push({
    id: 'QA-09',
    name: 'Horizontal Stories / carousel navigation exclusion',
    category: 'Exclusion',
    expectedBehavior: 'Classified as HORIZONTAL_STORIES_OR_CAROUSEL; isAcceptedSwipe = false',
    actualOutcome: `feedContext=${t9.feedContext}, isAcceptedSwipe=${t9.isAcceptedSwipe}`,
    passed: t9.isAcceptedSwipe === false && t9.feedContext === 'HORIZONTAL_STORIES_OR_CAROUSEL',
  });

  // Test 10: Ambiguous full-screen scroll flagged as UNCERTAIN (never silently counted)
  const t10 = engine.evaluate({
    packageName: 'com.instagram.android',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 14000,
    className: 'androidx.viewpager2.widget.ViewPager2',
    viewIdResourceName: 'com.instagram.android:id/obfuscated_0x7f09',
    fromIndex: 1,
    toIndex: 2,
    scrollDeltaX: 0,
    scrollDeltaY: 1300,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.9,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: false,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 601,
  });
  results.push({
    id: 'QA-10',
    name: 'Uncertain event transparency (obfuscated container without marker)',
    category: 'Honest Accuracy',
    expectedBehavior: 'isUncertainEvent = true, isAcceptedSwipe = false (never silently counted)',
    actualOutcome: `isUncertainEvent=${t10.isUncertainEvent}, isAcceptedSwipe=${t10.isAcceptedSwipe}`,
    passed: t10.isUncertainEvent === true && t10.isAcceptedSwipe === false && t10.confidence === 'UNCERTAIN',
  });

  // Test 11: Non-target package exclusion
  const t11 = engine.evaluate({
    packageName: 'com.android.chrome',
    eventType: 'TYPE_VIEW_SCROLLED',
    eventTimestampMs: 15500,
    className: 'android.webkit.WebView',
    viewIdResourceName: 'com.android.chrome:id/compositor_view_holder',
    fromIndex: 0,
    toIndex: 4,
    scrollDeltaX: 0,
    scrollDeltaY: 1400,
    isFullScreenVerticalContainer: true,
    containerHeightRatio: 0.9,
    containerWidthRatio: 1.0,
    hasActiveShortsOrReelsMarker: false,
    hasCommentsOrSheetOverlay: false,
    hasSearchInputOrGrid: false,
    hasProfileGridPattern: false,
    hasStandardWatchPlayerLayout: false,
    isScreenInteractive: true,
    structuralNodeSignature: 701,
  });
  results.push({
    id: 'QA-11',
    name: 'Strict package allowlist enforcement (non-YouTube/Instagram package)',
    category: 'Privacy',
    expectedBehavior: 'Immediately rejected at Stage 1',
    actualOutcome: `platform=${t11.platform}, isAcceptedSwipe=${t11.isAcceptedSwipe}`,
    passed: t11.platform === null && t11.isAcceptedSwipe === false,
  });

  return results;
}

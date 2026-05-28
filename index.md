# 📰 Khoborakhobor: Architectural Index & Codebase Analysis
Welcome to the comprehensive technical index of the **Khoborakhobor** codebase. This document serves as a high-fidelity reference manual detailing every subsystem, lifecycle, and component of this premium GeckoView-powered newspaper library application.
---
## 🗺️ High-Level System Architecture
Khoborakhobor uses a **Single Activity Architecture** that blends **Legacy Android Views (XML + Fragments)** with a reactive **Jetpack Compose Screen Layer** hosted inside `MainActivity`.
```mermaid
graph TD
    MA[MainActivity.kt] --> VM[AppViewModel.kt]
    MA --> NS[NativeShell.kt]
    MA --> CS[Compose Screen Layer]
    
    NS --> HNF[HomeNativeFragment]
    NS --> SNF[SourcesNativeFragment]
    NS --> ONF[OfflineNativeFragment]
    NS --> FNF[FavoritesNativeFragment]
    NS --> SET[SettingsNativeFragment]
    
    CS --> BS[BrowserScreen]
    CS --> ORS[OfflineReaderScreen]
    
    VM --> PREF[AppPreferences.kt]
    VM --> OPR[OfflinePageRepository.kt]
    VM --> NSR[NewsSourceRepository.kt]
    
    BS --> GR[GeckoRuntimeProvider]
    BS --> ABC[AdBlockController]
    BS --> LDC[KhoborLiveDarkController]
    
    ORS --> RPR[ReaderPageRepository.kt]
🗃️ Codebase Core Subsystems
1. The Native Android Shell (NativeShell.kt & MainActivity.kt)
Role: Bridges traditional XML/Fragment navigation with modern Jetpack Compose.
Key Interfaces:
NativeStateConsumer: Enforces that any fragment loaded inside the shell reactively consumes the unified AppUiState and ThemePalette changes.
Fragments:
HomeNativeFragment: Implements a high-performance RecyclerView of sources divided into Popular Bangla, English, and International curated cards.
SourcesNativeFragment: Offers categorized filtering and real-time search of all configured sources.
OfflineNativeFragment / FavoritesNativeFragment: Feeds specific lists from AppUiState.
BrowserComposeFragment: Acts as the transition zone, popping open the full Jetpack Compose browser.
2. GeckoView Web Rendering (GeckoRuntimeProvider.kt)
Role: Manages the life, setup, and teardown of the Mozilla GeckoView engine.
Key Details:
GeckoRuntime is a very heavy, memory-intensive component. GeckoRuntimeProvider uses a lazy thread-safe singleton pattern and asserts (BROWSER_RUNTIME_SCREENS) that Gecko is never loaded outside active reading views to preserve device memory.
3. Integrated Ad-Blocking (AdBlockController.kt & UBlockOriginManager.kt)
Role: High-fidelity ad blocking via native uBlock Origin web extension.
Key Details:
Tier 1 Blocker: UBlockOriginManager validates assets/ublock/manifest.json at startup and uses GeckoView's ensureBuiltIn API to register and run Raymond Hill's official uBlock Origin extension.
Tier 2 Fallback: If uBlock Origin fails to load or experiences compatibility exceptions, the AdBlockController gracefully downgrades to GeckoPrivacyBlocker.
Fallback Blocker: GeckoPrivacyBlocker configures the native ContentBlocking.Settings in GeckoView to Strict Anti-Tracking (AD, ANALYTIC, SOCIAL, FINGERPRINTING), query parameter stripping, and cookie purging.
4. Reading & Offline Archiving (OfflinePageArchiver.kt & ReaderHtmlGenerator.kt)
Role: Full-fidelity page download for offline usage, and conversion into clean typographic layouts.
Archiving Flow:
Fetches pages using a preconfigured OkHttpClient mimicking a mobile Safari user-agent.
Uses Jsoup to parse raw HTML and strip tracking/ads (SCRIPT_SKIP_MARKERS).
Extracts, downloads, and caches external assets (images, posters, custom fonts, stylesheets) into a page-specific assets/ directory on disk up to a safe 32MB budget limit.
Rewrites stylesheet rules and image sources internally to point to local assets using relative URIs.
Reader Mode Flow:
ReaderHtmlGenerator uses Jsoup to inspect document structures, searching for major content nodes (article, main, .post, .news-details, etc.).
Removes headers, navbars, and banners, rendering a clean light or dark reading mode optimized with beautiful typography for Bangla and English scripts.
5. Multi-Thread State Management (AppViewModel.kt)
Role: Central source of truth for the application state (AppUiState).
Key Details:
State is exposed as a read-only Kotlin StateFlow.
Loads preferences, sources, and offline pages concurrently off the UI thread via Dispatchers.IO using structured async/await coroutines to minimize boot latency.
6. Dynamic Icon Cache (SourceIconCacheManager.kt & SourceIconResolver.kt)
Role: Handles background downloading and caching of news source favicons without blocking lists.
Key Details:
Resolves fallback icons using a Google Favicon proxy server.
Maintains a thread-safe thread pool (MAX_CONCURRENT_DOWNLOADS = 1) to fetch icons in the background.
Validates file headers (checking signature for WebP format) and cleans older versions to guarantee optimal filesystem utilization.
7. Performance & Jank Tracking (AppJankTracker.kt & PerformanceLogger.kt)
Role: Real-time rendering quality logging.
Key Details:
Integrates Jetpack Performance Metrics (JankStats) to record frames dropping below the refresh threshold.
Attaches state markers (isSavingOffline, isGeneratingReader) to identify exactly what operation was occurring when jank was detected.
BackgroundThreadGuard enforces that heavy read/write operations never run on Android's Main thread.
🎨 Theme & Styling System
The application relies on a unified, high-contrast, editorial design style. Themes are managed by ThemeManager which maps directly to the active light/dark state.

Token	Light Theme	Dark Theme
appBackground	#FFF4EFE6 (Warm Paper)	#FF080808 (Deep Ink Black)
cardBackground	#FFFDF8 (Soft Cream)	#FF151515 (Elevated Obsidian)
cardBorder	#DED6CA (Neutral Sand)	#FF2A2A2A (Muted Charcoal)
primaryText	#FF17130D (Charcoal Black)	#FFF5F2EC (Warm White)
secondaryText	#6F675C (Muted Slate)	#A8A29A (Muted Grey)
⚡ Live Dark Mode Overlay Implementation
One of the most complex features of the app is Webpage Live Dark Mode. Since external newspaper pages are loaded live via GeckoView, the app injects a dark stylesheet programmatically.

There are two redundancy levels for live dark mode:

WebExtension Injection (khobor-web-dark/content.js): A dedicated GeckoView WebExtension runs locally. It listens to native messages (khobor_dark) from KhoborLiveDarkController.kt and injects/removes a comprehensive CSS overlay dynamically.
Fallback Evaluation (liveDarkOverlayJavascriptUri): If the WebExtension fails or takes too long to bind, the browser screen evaluates a javascript: URL directly into the GeckoView session to append a style override with !important rules.
📋 Directory Reference
For quick code access, here are the absolute file locations of key components:

Main Android Entry: 
MainActivity.kt
Native Shell & Fragments: 
NativeShell.kt
Central State View Model: 
AppViewModel.kt
Offline Archiver: 
OfflinePageArchiver.kt
Reader Layout Generator: 
ReaderHtmlGenerator.kt
uBlock Extension Manager: 
UBlockOriginManager.kt
Live Dark Mode Extension: 
content.js
Source Data configurations: 
news_sources.json
🛡️ Coding Guardrails & Anti-Regression Rules
When writing code or adding features to this repository, keep the following guidelines in mind:

Main Thread Safety: Never execute filesystem writes (metadata.json, cached icons, reader cache) or Jsoup parses on the main thread. Always wrap them in a BackgroundThreadGuard block and execute under Dispatchers.IO.
Gecko Runtime Singleton: Never instantiate GeckoRuntime directly. Always resolve it through GeckoRuntimeProvider.get(context, disableAds).
Color Alignment: Only use color values resolved from the dynamic ThemeManager.palette(isDark) palette. Do not hardcode custom hexes in Jetpack Compose or Native fragments.
Webpage URL Safeguard: Ensure top-level navigation inside GeckoView is always checked via isAllowedTopLevelUrl against the allowed hosts in news_sources.json to prevent users from leaving the curated reader sandboxed environment.
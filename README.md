# AI Trading Army — Android

## 📲 Download APK
**👉 [Latest release APK](https://github.com/SQLRIZWAN/TradingAgentArmy-Android/releases/download/latest-build/TradingAgentArmy-v3.0.1-release.apk)** ← tap to install ( Releases page me bhi milega )

Production Android client for the TradingAgentArmy fleet (package `com.rizwan.tradingagentarmy`, version **3.0.1**).

## Features
- **Chat** — AI trade assistant with streaming replies. Pinned model = Tier‑1 (Gemini), automatic fallback chain: Gemini → OpenAI → Claude → Ollama → offline rule‑based replies. Backend `/api/chat` is tried first when configured.
- **Dashboard** — live market tickers (Binance/Bybit/Bitget public data), bot P&L, WS connection state.
- **Bots** — fleet list with Demo→Live gates (Gate 1 backtest / Gate 2 paper 72h / Gate 3 micro live), bot detail with trade history + cumulative P&L chart, force‑deploy behind CONFIRM.
- **🤖 AI Agent Army (14 agents)** — News/Sentiment/Technical/On-Chain/Chart analysts + Bull vs Bear debate + Trader + Risk Manager + Portfolio Manager. Agents can use a bounded, read-only context from local trades, bots, chat, and decision history, search the web (DDG), and reach a BUY/SELL/HOLD decision with entry/SL/TP. 24/7 foreground service + notifications.
- **⚡ HFT Scalper** — fast mobile scalper (configurable polling, EMA 9/21 + RSI) with persistent Room positions, spread/slippage/fee gates, cooldown and paper-by-default safety. This is not colocated institutional HFT.
- **🏦 Bitget** — HMAC Spot/Futures orders with deterministic local client IDs, exchange-side preset TP/SL, duplicate guards and startup reconciliation hooks. PAPER remains the default.
- **🪙 Bitget CFD/MT5 account** — XAUUSD/EURUSD-style symbols route through Bitget's direct CFD Open API with exchange-side TP/SL and position queries. No separate server or MT5 terminal is required for this app path.
- **📡 CFD market data** — configured Bitget CFD accounts use `/api/v3/cfd/market/tickers` for bid/ask prices and `/api/v3/cfd/market/history-candlestick` for Gold/Forex candles; public fallback data is used only when CFD credentials are not configured.
- **📈 Trading chart** — bundled Lightweight Charts WebView with candlesticks, EMA, RSI, volume view and 1m/5m/15m/1h/1D controls. CFD 5m candles are client-aggregated from 1m data because Bitget's CFD candle API does not expose every crypto interval.
- **📱 On-device model** — select a Gemma `.task` file (Settings → Local Model) and chat/agents run fully offline via MediaPipe.
- **Settings** — tabbed: AI Keys / Local Model / Exchange / Backend / Army / Look. Auto-save on typing. Encrypted exchange API keys (Bitget / Binance / Bybit) with connection tests and an explicit Bitget Demo API mode, AI provider keys + model pickers + key tests, fallback chain reorder (↑↓), backend REST/WS endpoints, notification preferences, AMOLED theme, danger zone.
- **Notifications** — trade alerts, bot crash, circuit breaker, daily P&L summary (WorkManager, on‑demand init).
- **Firebase** — project `sqlrrr`: anonymous auth, Firestore sync of chats/trades/bots/history, FCM. All Firebase config stays in **GitHub Secrets** — never in the repo.

## Build

```bash
# local
export STORE_PASSWORD=... KEY_PASSWORD=... KEY_ALIAS=...
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

CI (`.github/workflows/android-release.yml`): every push to `main` auto‑bumps `versionCode`, builds a signed release APK and publishes/updates a GitHub Release tagged `latest-build`. Tag pushes (`v*.*.*`) publish a full release with the same APK.

## GitHub Secrets

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | base64 of the release keystore |
| `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD` | signing credentials |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase Admin (sync tooling) |
| `GOOGLE_SERVICES_JSON` | *(optional)* `app/google-services.json` — injected at CI time if set |

### Generating your own keystore
```bash
keytool -genkeypair -v -storetype JKS -keystore keystore.jks -alias tradingarmy \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass '<password>' -keypass '<password>' \
  -dname "CN=<Your Name>, O=<Org>, C=PK"
gh secret set KEYSTORE_BASE64 --body "$(base64 -w0 keystore.jks)"
gh secret set KEY_ALIAS --body "tradingarmy"
gh secret set KEY_PASSWORD --body '<password>'
gh secret set STORE_PASSWORD --body '<password>'
```
Keep `keystore.jks` + password backed up — without them the app can never be updated in place on devices.

## Firebase
Configured — `GOOGLE_SERVICES_JSON` secret is set (injected by CI at build time, never committed). Firestore rules: [`firestore.rules`](firestore.rules) (auth‑required; deployed on `cloud.firestore` release channel).

To rotate the config later:
1. Firebase console → Project settings → Android app → download `google-services.json`
2. `gh secret set GOOGLE_SERVICES_JSON < app/google-services.json`

Without the secret the app still builds and runs — Firebase features stay dormant and everything works locally via Room.

## Live-trading safety

Live Army entries require a non-HOLD plan with both SL and TP. Bitget Spot/Futures opening orders send exchange-side protection fields, and the app persists an OPEN trade with a deterministic `clientOid`. A foreground watchdog checks local open trades after restart and can issue a secondary exit for a kill-switch or local SL/TP hit. Exchange-side protection is primary; this is not a guarantee against exchange outages, slippage, rejected orders or network failure. Use API keys without withdrawal permission and validate on a demo/small account first.

For Bitget CFD, create the CFD account in Bitget first, then create API keys with UTA account read and UTA trade permission. For a demo key, enable **Bitget Demo API key** in Settings so requests include Bitget's `paptrading: 1` header; turn it off for a live API key. The account's CFD mode determines the symbol suffix (`XAUUSD`, `XAUUSD.s`, or `XAUUSD.pro`); configure the matching symbol in the app.

## Current live-trading boundaries

The app is a controlled trading client, not a risk-free autonomous trading guarantee. CFD reconciliation now reads Bitget positions, stores `positionId`, verifies quantity/entry/TP/SL where the exchange returns them, recovers missing local rows, retries `EXIT_PENDING`/`UNKNOWN` rows, and closes an individual CFD position with `positionId + qty`. A symbol-wide CFD close-all request is intentionally not used for normal exits.

Before unattended live use, these operational requirements still apply:

- Android battery optimisation and OEM background restrictions must be disabled for the foreground Army service; Android cannot guarantee an app runs forever after force-stop, reboot, OS kill or network loss.
- The HFT loop is mobile REST/quote polling, not exchange-colocated millisecond HFT. Spread, slippage budget, fee budget and cooldown gates must be configured for the instrument.
- Exchange/API permission, symbol precision, CFD mode suffix, actual fills and exchange-side protection must be verified on a demo or very small account first.
- Never enable withdrawal permission. A failed/unknown exchange response must be treated as requiring manual verification.

The authoritative CFD endpoint details are in [Bitget CFD Trade](https://www.bitget.com/docs/catalog/cfd-trade/cfd-trade) and [Bitget CFD Market](https://www.bitget.com/docs/catalog/cfd-market/cfd-market).

## Stack
Kotlin 2.0 · Compose BOM 2024.09 · Material 3 · Hilt · Room · Retrofit/OkHttp/kotlinx‑serialization · WorkManager · Firebase BOM 33 · minSdk 26 / target 35.

## Keystore (this repo's generated one)
- alias: `tradingarmy` · store & key password: **see GitHub Secrets** (`STORE_PASSWORD` / `KEY_PASSWORD` are identical).

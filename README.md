# AI Trading Army — Android

## 📲 Download APK
**👉 [Latest release APK](https://github.com/SQLRIZWAN/TradingAgentArmy-Android/releases/download/latest-build/TradingAgentArmy-v3.0.1-release.apk)** ← tap to install ( Releases page me bhi milega )

Production Android client for the TradingAgentArmy fleet (package `com.rizwan.tradingagentarmy`, version **3.0.1**).

## Features
- **Chat** — AI trade assistant with streaming replies. Pinned model = Tier‑1 (Gemini), automatic fallback chain: Gemini → OpenAI → Claude → Ollama → offline rule‑based replies. Backend `/api/chat` is tried first when configured.
- **Dashboard** — live market tickers (Binance/Bybit/Bitget public data), bot P&L, WS connection state.
- **Bots** — fleet list with Demo→Live gates (Gate 1 backtest / Gate 2 paper 72h / Gate 3 micro live), bot detail with trade history + cumulative P&L chart, force‑deploy behind CONFIRM.
- **🤖 AI Agent Army (14 agents)** — News/Sentiment/Technical/On-Chain/Chart analysts + Bull vs Bear debate + Trader + Risk Manager + Portfolio Manager. Agents chat in a War Room, read the trade DB (past results), search the web (DDG), and reach a BUY/SELL/HOLD decision with entry/SL/TP. 24/7 foreground service + notifications.
- **⚡ HFT Scalper** — fast loop (2s ticks, EMA 9/21 cross + RSI) with risk guards, paper by default.
- **🏦 Bitget** — HMAC v2 Spot/Futures orders with deterministic client IDs, exchange-side preset TP/SL, duplicate guards and a restart watchdog. PAPER remains the default.
- **🪙 MT5 CFD bridge** — XAUUSD/EURUSD-style symbols can route through a user-hosted MT5 EA/bridge. Android stores the login/server encrypted, but the MT5 terminal must run on a VPS/PC; the app blocks LIVE MT5 without a configured bridge.
- **📱 On-device model** — select a Gemma `.task` file (Settings → Local Model) and chat/agents run fully offline via MediaPipe.
- **Settings** — tabbed: AI Keys / Local Model / Exchange / Backend / Army / Look. Auto-save on typing. encrypted exchange API keys (Bitget / Binance / Bybit / MT5) with connection tests, AI provider keys + model pickers + key tests, fallback chain reorder (↑↓), backend REST/WS endpoints, notification preferences, AMOLED theme, danger zone.
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

For MT5, configure the bridge URL plus login/server in Settings. The bridge contract is `POST /api/mt5/order` and `POST /api/mt5/close`, returning `{ "ok": true, "orderId": "...", "clientOid": "..." }`. The bridge/EA is responsible for MT5 authentication, symbol mapping, broker volume rules and exchange-confirmed SL/TP.

## Stack
Kotlin 2.0 · Compose BOM 2024.09 · Material 3 · Hilt · Room · Retrofit/OkHttp/kotlinx‑serialization · WorkManager · Firebase BOM 33 · minSdk 26 / target 35.

## Keystore (this repo's generated one)
- alias: `tradingarmy` · store & key password: **see GitHub Secrets** (`STORE_PASSWORD` / `KEY_PASSWORD` are identical).

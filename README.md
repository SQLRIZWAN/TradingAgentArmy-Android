# AI Trading Army — Android

Production Android client for the TradingAgentArmy fleet (package `com.rizwan.tradingagentarmy`, version **3.0.1**).

## Features
- **Chat** — AI trade assistant with streaming replies. Pinned model = Tier‑1 (Gemini), automatic fallback chain: Gemini → OpenAI → Claude → Ollama → offline rule‑based replies. Backend `/api/chat` is tried first when configured.
- **Dashboard** — live market tickers (Binance/Bybit/Bitget public data), bot P&L, WS connection state.
- **Bots** — fleet list with Demo→Live gates (Gate 1 backtest / Gate 2 paper 72h / Gate 3 micro live), bot detail with trade history + cumulative P&L chart, force‑deploy behind CONFIRM.
- **Settings** — encrypted exchange API keys (Bitget / Binance / Bybit / MT5) with connection tests, AI provider keys + model pickers + key tests, fallback chain reorder (↑↓), backend REST/WS endpoints, notification preferences, AMOLED theme, danger zone.
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
1. Enable **API Keys API** for project `sqlrrr`: https://console.developers.google.com/apis/api/apikeys.googleapis.com/overview?project=605955318342
2. Download `google-services.json` (Android app `com.rizwan.tradingagentarmy`).
3. `gh secret set GOOGLE_SERVICES_JSON < app/google-services.json`
4. Firestore rules: [`firestore.rules`](firestore.rules) (auth‑required; deployed on `cloud.firestore` release channel).

Without these the app still builds and runs — Firebase features stay dormant and everything works locally via Room.

## Stack
Kotlin 2.0 · Compose BOM 2024.09 · Material 3 · Hilt · Room · Retrofit/OkHttp/kotlinx‑serialization · WorkManager · Firebase BOM 33 · minSdk 26 / target 35.

## Keystore (this repo's generated one)
- alias: `tradingarmy` · store & key password: **see GitHub Secrets** (`STORE_PASSWORD` / `KEY_PASSWORD` are identical).

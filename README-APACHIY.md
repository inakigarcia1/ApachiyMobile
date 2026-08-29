# Apachiy Mobile

Apachiy is a self-hosted fork of Nuvio Mobile. Cloud accounts, library, devices,
and addons talk to **your** Apachiy backend (`supabase.apachiy.org` /
`api.apachiy.org`), never to Nuvio official infra.

The Kotlin package stays `com.nuvio.app`. The Android application id is
`com.apachiy.app` (debug: `com.apachiy.app.debug`).

## Open in Android Studio

1. Open the **Apachiy-Mobile** folder (this repo), not the TV fork.
2. Copy `local.example.properties` to `local.properties` (gitignored) and fill in:
   - `sdk.dir`
   - `APACHIY_SUPABASE_URL`
   - `APACHIY_SUPABASE_ANON_KEY`
   - `APACHIY_API_BASE_URL`
   - the same TMDB / Trakt / Simkl keys you use on Apachiy TV
3. Leave `NUVIO_SUPABASE_FALLBACK_URL` empty.
4. Sync Gradle.
5. Select variant **fullDebug** (`com.apachiy.app.debug`).

```bash
./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full
```

iOS shares `commonMain` (including JWT on `api.apachiy.org`). Xcode setup is out
of scope for this port.

## Cloud

Production Apachiy endpoints:

- Supabase: `https://supabase.apachiy.org`
- API: `https://api.apachiy.org`
- Dashboard / devices: `https://apachiy.org/dashboard`

Do not copy Coolify, Kong, Traefik, Studio, `infra/supabase/`, SQL migrations,
or `tv-logins-exchange` into this repo. Those live in the cloud / TV / API stacks.

## Tests

```bash
./gradlew :composeApp:cleanAllTests :composeApp:allTests -Pnuvio.android.distribution=full
```

If iOS `allTests` is too heavy, run Android host tests instead:

```bash
./gradlew :composeApp:testDebugUnitTest -Pnuvio.android.distribution=full
```

# SideQuest Native Mobile — first increment

Expo SDK 58 / React Native, sharing **read-only** public Quest APIs with SideQuest Spring Boot.

## Current functionality
- Native Quest feed with search, pagination, details, empty/loading/error states
- No separate database; reads from the existing backend and PostgreSQL
- No mock approval, signup, login or join flows. Session-authenticated writes are intentionally deferred until native cookie+CSRF design is verified.

## On your Mac
Start the existing backend separately from `backend/`, using your existing `.env.properties`:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Then in another terminal:

```sh
cd mobile
npm install
npx expo install --fix
npx expo-doctor
npm run start
```

- **iOS simulator:** set `EXPO_PUBLIC_API_URL=http://127.0.0.1:8080` in `mobile/.env.local`; press `i` in Expo (Xcode simulator needed).
- **Android emulator:** `EXPO_PUBLIC_API_URL=http://10.0.2.2:8080`; press `a`.
- **Physical device:** `localhost` resolves to the *phone*, not your Mac. Use your Mac's LAN IP for `EXPO_PUBLIC_API_URL`, ensure the backend listens on your LAN interface, and check firewall/LAN permissions. Only use HTTP on a trusted local network; production must use HTTPS. Expo Go's network and native transport restrictions may require a development build.

Copy `.env.example` to `.env.local` and edit the URL before starting Expo. Never put PostgreSQL credentials in mobile env files; `EXPO_PUBLIC_*` is publicly embedded in the client bundle.

## Next increment
Design and verify session-cookie and CSRF storage on iOS/Android, then add account registration/login, join/complete, XP/profile, friends and groups. Do not replace established server authorization with client-only roles.

## Validation
The connected GitHub editor has **not** run `npm install`, TypeScript checks, Metro, simulators or device tests; run the commands above locally before review. The feature is a standalone branch, not merged.

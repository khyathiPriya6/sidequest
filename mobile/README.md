# SideQuest native app — multi-feature integration candidate

The mobile client shares SideQuest's existing Spring Boot API and PostgreSQL database. It is not a duplicate backend.

## Screens and current scope
- Explore, Quest search and details, report a Quest
- Register, login, logout (session cookie + CSRF through native fetch; **device verification pending**)
- My Quests: joined/in-progress/completed, create/edit drafts, submit for moderation, publish when approved, archive
- Join and complete a Quest; the backend awards XP only once; confirmation for completion
- Profile, achievements, XP history and privacy settings
- Friend requests (send, accept, reject, cancel), friends, remove/block and rankings
- Teams (create, invite, accept/reject/cancel invites, roster, remove/leave/archive), challenges and leaderboards
- Moderator-only review queue, approve/reject/hide/dismiss reports, legacy intake
- Native navigation with scrollable tabs, loading/error state and refresh

These screens use server endpoints. They are **not yet release-ready**: the connected GitHub editor has not run or verified the TypeScript build, native login, cookies, CSRF, user flows, or simulator screenshots. Some web refinements remain for a later parity pass, including richer pagination/filtering, accessibility/device layout polish, moderation appeals, and social profile management.

## Run on Mac (iOS simulator)
Start the existing backend from `backend/` using your existing database and local profile. Then:

```sh
cd mobile
npm install
npx expo install --fix
npx expo-doctor
npm run check
npx expo start --clear
```

Press `i` to open the simulator. The default API URL is `http://127.0.0.1:8080`, which works for an iOS simulator on the same Mac. Android emulator uses `http://10.0.2.2:8080`. For a physical phone use the Mac's LAN IP and an explicitly LAN-accessible backend on a trusted network. Set `EXPO_PUBLIC_API_URL` inside an ignored `mobile/.env.local`. Production requires HTTPS. Never put secrets in `EXPO_PUBLIC_*`.

## Required manual acceptance before approval
1. Create a user, sign out, sign in again, close/reopen Expo Go and confirm session restoration.
2. After login, load My Quests and Profile. If 401/403 or `csrf_invalid` appears, **stop**: native session persistence requires a dedicated fix. Do not disable CSRF or turn off backend authorization.
3. Create draft, submit, approve with a separately provisioned staff account, publish.
4. Join from a different account and complete once; confirm no duplicate XP.
5. Test privacy, friend requests, team invites, team capacity and owner restrictions with 3 accounts.
6. Test moderator-only tab, approve/reject and reported Quest flow; confirm non-moderators cannot access.
7. Check loading/empty/error states, accessibility, small display, keyboard and offline/uncertain mutation recovery.

The existing backend validates ownership and permissions; UI visibility is never authorization. A mutation failure can have an uncertain outcome: refresh server state before repeating. No new DB migrations or deployment required by this mobile branch.

## Security limitation
React Native does not share the browser's cookie jar behavior by assumption. The initial implementation uses native `fetch(..., {credentials:'include'})` and obtains a session-bound CSRF token for each mutation. **This must be verified on actual iOS/Android runtimes.** If Expo Go fails to persist HttpOnly cookies, design a verified native-session adapter or a separately reviewed backend protocol; never extract HttpOnly cookies into unencrypted app state.

## Review
This branch targets `feat/m6-group-quests-complete`. PR #122 and the mobile PR are drafts. No automatic merge, deployment or migration of real databases.

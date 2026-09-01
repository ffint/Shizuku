# Android 17 Compatibility Plan

Temporary execution checklist. This file must be deleted before the final squash merge/release.

## Goal

Make this Shizuku fork usable on Android 17 while preserving older behavior, specifically fixing the application/authorization list and wireless-debugging startup/pairing.

## 1. Baseline and references

- [x] Inspect upstream Shizuku baseline, build, app-management path, wireless ADB/mDNS implementation, submodule, and CI.
- [x] Review upstream Shizuku PR #2233 and HiddenApi PR #11 for Android 17 ABI changes.
- [x] Review `thedjchi/Shizuku` 13.7.0 Android 17 fixes.
- [x] Review Stellar mDNS recovery behavior and isolate only applicable reliability changes.

## 2. Android 17 hidden API compatibility

- [x] Compile against API 37 while keeping `targetSdk 36` to avoid an unnecessary Android 17 local-network runtime permission migration.
- [x] Confirm HiddenApi 4.5.0 is not published to the configured Maven repositories; do not ship an unreproducible dependency.
- [x] Add self-contained `InstalledPackagesCompat` for the Android 17 `IPackageManager#getInstalledPackages` return-type ABI change and route Manager/Server enumeration through it.
- [x] Use `SystemServiceBinder` rather than reflection into HiddenApi private fields, preserving Shizuku's binder wrapper and avoiding R8 name fragility.
- [x] Add `UserManagerCompat` for the Android 16/17 `IUserManager#getUsers(boolean)` signature and route user enumeration through it.
- [x] Retain the published HiddenApi 4.4.0 dependency for unchanged compatibility APIs such as PermissionManager fallbacks.

## 3. Android 17 wireless debugging

- [x] Add `USE_LOOPBACK_INTERFACE` support.
- [x] Keep `ACCESS_LOCAL_NETWORK` out of this target-36 build; Android 17 requires the runtime permission only for apps targeting API 37+.
- [x] Stop assuming ADB pairing/connect always uses `127.0.0.1`; retain the mDNS-resolved host and pass it to pairing/manual start/notification pairing/boot auto-start.
- [x] Probe the resolved local host/port rather than loopback.
- [x] Add bounded NSD recovery (maximum four restarts), timeout handling, stale-listener checks, and a concurrent-resolve guard.

## 4. Build and release plumbing

- [x] Enable the fork's Actions workflow and install the actual Android 17 preview SDK package (`platforms;android-37.0`).
- [x] Make signing safe when official release secrets are absent; fall back to the Android debug signing configuration and clearly label the limitation.
- [x] Keep APK upload as an Actions artifact.
- [x] Add an idempotent release step gated by a final `[release]` commit on `master`.
- [x] Obtain a successful full `:manager:assemble` build and confirm both debug and release APKs exist in the artifact (run 33545459450).
- [ ] Re-run CI after the final UserManager/R8-safe compatibility refinements and require green.

## 5. Final review and cleanup

- [x] First complete diff review found and fixed concurrent `NsdManager.resolveService` re-entry.
- [x] Check retry bounds, listener lifecycle, stop/start cleanup, host propagation, permission scope, and Android <=16 regression surface.
- [x] Manually inspect for embedded credentials; GitHub Advanced Security secret-scanning API is unavailable on this fork, and no real key/token is present in the diff.
- [ ] Review the final net diff from `master` again after all refinements.
- [ ] Verify no staged patch/debug/planning helper remains.
- [ ] Delete this `plan.md`.
- [ ] Run CI once more on the no-plan final PR head and confirm the APK artifact.

## 6. Merge and release

- [ ] Convert the validated PR to the final focused PR description.
- [ ] Squash merge so `master` receives one clean net commit; because `plan.md` is deleted before the squash, the final master commit/history does not contain the temporary planning commits.
- [ ] Include `[release]` in the squash commit title to trigger the final master build/release.
- [ ] Verify the master build, `v13.7.0-android17` GitHub Release, and APK asset.

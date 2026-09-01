# Android 17 Compatibility Plan

This file is a temporary execution checklist for the Android 17 compatibility work. It must be removed before the final merge/release.

## Goal

Update this Shizuku fork so that it works correctly on Android 17 (API 37), with particular focus on:

- application/authorization management showing Shizuku-capable apps correctly;
- Android 17 hidden API compatibility;
- Wireless debugging pairing/startup on Android 17;
- reliable ADB mDNS discovery and reconnect behavior;
- preserving behavior on Android 16 and older as much as possible;
- keeping the patch small and close to upstream Shizuku.

## Execution order

### 1. Baseline and reference analysis

- [x] Record the current fork baseline, build configuration, submodules, manager manifest, application-management path, and ADB/mDNS implementation.
- [x] Compare the relevant Android 17 changes from current `RikkaApps/Shizuku`, including upstream PR #2233 and the HiddenApi 4.5.0 fix.
- [x] Compare `thedjchi/Shizuku` `v13.7.0-thedjchi`, isolating Android 17/API 37 and local-network changes from unrelated fork changes.
- [x] Compare `roro2239/Stellar`, isolating only the wireless ADB/mDNS reliability changes that are applicable to Shizuku.
- [x] Write down the minimal set of files/changes required before editing production code.

Decision record for the implementation:

1. Follow upstream PR #2233 for the app-list bug: compile against API 37 and use HiddenApi 4.5.0. Keep `targetSdk` at 36 for this compatibility release; Android's Android 17 local-network documentation explicitly keeps legacy target SDKs on the implicit `INTERNET`-based local-network grant, avoiding an unnecessary new runtime permission flow.
2. Do not add the broad reflection-based `InstalledPackagesCompat` workaround from thedjchi because HiddenApi 4.5.0 fixes the changed Android 17 `IPackageManager#getInstalledPackages` ABI centrally and is the upstream direction.
3. Add Android 17 loopback permission support and stop assuming that the ADB pairing endpoint is always `127.0.0.1`: carry the mDNS-resolved host through pairing/connect call sites while retaining the existing port callback API.
4. Import only bounded NSD recovery concepts from Stellar (start-failure retry and controlled discovery restart/cleanup), not its unrelated UI/startup architecture or long/high-frequency refresh loop.
5. Keep existing Shizuku CI structure, but make fork signing safe when release secrets are absent and publish a final Android 17 compatibility release only from the final merge commit.
6. The upstream Shizuku-API gitlink update from `a27f6e4` to `68cb8c6` is not required by this patch: the latter is the direct next commit and only changes demo UserManager/example layout code, not the API/stub modules consumed by this manager/server build.

### 2. Android 17 application-list / hidden-API compatibility

- [x] Update compile SDK and HiddenApi dependency as required for API 37 compatibility (`compileSdk 37`, HiddenApi `4.5.0`); keep `targetSdk 36`.
- [x] Verify PackageManager and PermissionManager compatibility: HiddenApi 4.5.0 supplies the API-37 `IPackageManager` ABI, while its PermissionManager compat path already falls back across persistent-device-id, integer-device-id, and legacy grant/revoke signatures.
- [x] Verify the application-management data path uses `rikka.hidden.compat.PackageManagerApis`, so it no longer depends on the obsolete Android 17 method signature after the dependency update.

### 3. Android 17 wireless-debugging permissions and access

- [x] Add the API-37 loopback permission needed by the Android 17 networking model (`USE_LOOPBACK_INTERFACE`).
- [x] Runtime `ACCESS_LOCAL_NETWORK` handling is intentionally not added: this build remains `targetSdk 36`, for which Android 17 keeps legacy local-network access via the existing `INTERNET` permission. Requesting the target-37 permission in this build would create an unnecessary migration/UX change.
- [x] Preserve Android 16 and older behavior by keeping `targetSdk 36` and all Android 17-specific behavior additive.

### 4. Wireless ADB / mDNS reliability

- [x] Harden `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` discovery without replacing Shizuku's architecture.
- [x] Add bounded recovery for NSD discovery start/stop/failure/timeout states: at most four restarts, with cleanup and stale-listener identity checks.
- [x] Validate the resolved endpoint on a local interface and probe the resolved host/port instead of hardcoded `127.0.0.1`; propagate the resolved host to pairing, manual-start discovery, notification pairing, and boot auto-start paths.
- [x] Review lifecycle/thread/callback behavior and add a `resolving` guard to prevent concurrent `resolveService` calls; callbacks from stale discovery generations are ignored.

### 5. Build and CI verification

- [x] Preserve the existing GitHub Actions build workflow structure and make fork signing safe when official release secrets are absent.
- [x] Preserve manager APK upload as an Actions artifact and add an idempotent final release step gated by a `[release]` commit on `master`.
- [ ] Run/observe CI for the implementation branch and fix every compile/build failure. (Currently blocked because this fresh fork reports zero check runs; GitHub Actions likely needs its one-time fork workflow enablement.)
- [ ] Confirm an installable manager APK is actually produced; source-level plausibility is not sufficient.

### 6. Full code review before finalization

- [x] Review the complete diff from `master` to the implementation branch once; one concurrent-NSD-resolution race was found and fixed.
- [x] Check API-level guards and Android 16-or-older regression risk.
- [x] Check permission declarations and runtime permission scope/UX.
- [x] Check NSD/mDNS lifecycle, callback ordering, thread visibility, retry bounds, cleanup, and failure paths; add stale-listener and resolving guards.
- [x] Check that no unrelated Stellar/thedjchi functionality or broad reflection workaround was imported.
- [ ] Run targeted secret scanning on the final diff/content and resolve any finding.
- [ ] Re-run the complete review and build verification after the remaining CI/final cleanup steps.

### 7. Final cleanup, merge, and release

- [ ] Update this checklist so completed work is reflected.
- [ ] Remove `plan.md` from the implementation branch before finalization.
- [ ] Create a clean final branch directly from `master` and commit only the reviewed production/CI files, so neither the final PR nor its commit history contains `plan.md` or planning commits.
- [ ] Ensure the final code tree contains no temporary planning/debug files.
- [ ] Create a focused final PR with root cause, implementation, compatibility, and build/review notes; close the temporary validation PR without merging it.
- [ ] Squash merge the clean final PR with `[release]` in the final commit title so `master` contains one focused implementation commit and triggers release publication.
- [ ] Verify the resulting commit/PR/build/release state and confirm the final APK asset.

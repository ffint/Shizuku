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
3. Add Android 17 loopback permission support and stop assuming that the ADB pairing endpoint is always `127.0.0.1`: carry the mDNS-resolved host together with the port through pairing/connect call sites.
4. Import only bounded NSD recovery concepts from Stellar (start-failure retry and controlled discovery restart/cleanup), not its unrelated UI/startup architecture or long/high-frequency refresh loop.
5. Keep existing Shizuku CI structure, but make fork signing safe when release secrets are absent and publish a final Android 17 compatibility release only from the final merge commit.

### 2. Android 17 application-list / hidden-API compatibility

- [ ] Update compile/build SDK and HiddenApi/Shizuku-API dependencies only as required for API 37 compatibility.
- [ ] Adapt PackageManager/PermissionManager hidden API usage for Android 17 while retaining API guards/backward compatibility.
- [ ] Verify the application-management data path no longer depends on an obsolete Android 17 method signature.

### 3. Android 17 wireless-debugging permissions and access

- [ ] Add only the Android 17/local-network permissions actually required for loopback, NSD/mDNS, and Wi-Fi service discovery.
- [ ] Add runtime permission handling where Android 17 requires it; do not rely on manifest declarations alone.
- [ ] Ensure Android 16 and older do not enter Android 17-only permission flows.

### 4. Wireless ADB / mDNS reliability

- [ ] Harden `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` discovery without replacing Shizuku's architecture.
- [ ] Add bounded recovery for NSD discovery start/stop/failure/timeout states where justified by Stellar/reference behavior.
- [ ] Validate resolved host/port before attempting ADB connection and avoid stale service results.
- [ ] Review lifecycle/thread/callback cleanup to prevent retry loops, leaks, duplicate discovery, or unnecessary battery drain.

### 5. Build and CI verification

- [ ] Preserve or minimally update the existing GitHub Actions build workflow.
- [ ] Make the manager APK available as an Actions artifact if the existing workflow does not already do so.
- [ ] Run/observe CI for the implementation branch and fix every compile/build failure.
- [ ] Confirm an installable manager APK is actually produced; source-level plausibility is not sufficient.

### 6. Full code review before finalization

- [ ] Review the complete diff from `master` to the implementation branch, file by file.
- [ ] Check API-level guards and Android 16-or-older regression risk.
- [ ] Check permission declarations and runtime permission UX/flow.
- [ ] Check NSD/mDNS lifecycle, callback ordering, thread safety, retry bounds, cleanup, and failure paths.
- [ ] Check that no unrelated Stellar/thedjchi functionality or broad refactor was imported.
- [ ] Run targeted secret scanning on the final diff/content and resolve any finding.
- [ ] Fix all review findings and re-run build verification after fixes.

### 7. Final cleanup, merge, and release

- [ ] Update this checklist so completed work is reflected.
- [ ] Remove `plan.md` from the implementation branch before the final PR/merge.
- [ ] Ensure the final code tree contains no temporary planning/debug files.
- [ ] Create a focused PR from `android17-compat` to `master` with root cause, implementation, compatibility, and build/review notes.
- [ ] Prefer squash merge so the final `master` history does not retain the temporary planning commit.
- [ ] Ensure the final GitHub build/release path publishes a clearly labeled Android 17 compatibility APK.
- [ ] Verify the resulting commit/PR/build/release state and report the final APK location.

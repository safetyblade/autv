# AUTV Android builds, updates and account retention

**Only two downloadable application types should be offered.**

| Build | Package | Purpose | Account data | Update policy |
| --- | --- | --- | --- | --- |
| **AUTV signed update** | `dev.prestwich.autv` | Main application | Existing app data | Updates only when signed with the *same permanent key* and higher versionCode |
| **AUTV Trial** | `dev.prestwich.autv.trial` | Full app QA on TV/phone | Separate private app data | Only updates an existing Trial with the same signing certificate; ordinary GitHub runner debug keys are not stable |

The old `AUTV-Full-App-Debug.apk` artifacts are QA builds, **not guaranteed over-install updates**. They must not be offered as a production release.

## Permanent signing — one-time owner setup

On a trusted computer with authenticated GitHub CLI, JDK keytool and repository Actions-secret administration permission:

```sh
bash scripts/setup-android-signing.sh
```

The script stores the permanent key *outside* the repository and writes Actions secrets `AUTV_SIGNING_KEYSTORE_BASE64`, `AUTV_SIGNING_STORE_PASSWORD`, `AUTV_SIGNING_KEY_PASSWORD`, and the public pin `AUTV_SIGNING_CERT_SHA256`. Retain an encrypted private backup; do not upload the keystore/password to this chat, a PR or the repo. Never regenerate an established key. The GitHub connector cannot provision these secrets.

The **Native app** workflow produces signed `app-release.apk` only after verifying this identity and the monotonic versionCode. A missing or mismatched signing identity must **fail the workflow** rather than producing a disposable key under the main package.

## Installing without losing sessions

- Confirm the installed Android package, signing certificate and versionCode before claiming an in-place update.
- Android refuses replacement by an APK signed with a different certificate. No ZIP rename, `adb install -r`, or account QR re-link changes that rule.
- An installed older debug build with a disposable signature may need a one-time data-clearing uninstall when migrating to the permanent signing identity. That loses encrypted provider sessions and favourites/app preferences unless separately recoverable. **Do not uninstall automatically.**
- Until a permanent compatible build is verified, use the separate **AUTV Trial** APK for test feedback. It cannot read account data from main AUTV.
- Test updates on a device with matched signing by installing two successive signed runs; confirm 9Now, SBS, 7plus and optional ABC sessions and Favourites are retained.

Use [the Android QA workflow](../.github/workflows/native-provider.yml) for regression evidence, not as the download channel. Full app builder source is `android/app/build.gradle.kts`.

# Releasing

Pushing a tag `vMAJOR.MINOR.PATCH` runs [.github/workflows/release.yml](.github/workflows/release.yml), which:

1. Builds a signed, R8-minified AAB and APK (`versionName` = tag, `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH`).
2. Uploads the AAB to Google Play (if Play credentials are configured).
3. Creates a GitHub release with the APK attached and auto-generated release notes.

```sh
git tag v0.1.0
git push origin v0.1.0
```

## One-time setup

### 1. Upload key

Google Play App Signing holds the real app signing key; we only sign uploads with an *upload key*.
Generate it once and keep it **outside** the repository, backed up in a password manager:

```sh
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

If the upload key is ever lost or leaked, it can be reset from Play Console (App integrity).

### 2. GitHub secrets

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) | gh secret set UPLOAD_KEYSTORE_BASE64
gh secret set UPLOAD_KEYSTORE_PASSWORD
gh secret set UPLOAD_KEY_ALIAS        # e.g. upload
gh secret set UPLOAD_KEY_PASSWORD
```

To build a signed release locally, add the same values to `~/.gradle/gradle.properties`
(`UPLOAD_KEYSTORE_FILE=<absolute path to upload.jks>`, `UPLOAD_KEYSTORE_PASSWORD=...`, `UPLOAD_KEY_ALIAS=...`, `UPLOAD_KEY_PASSWORD=...`).

### 3. Google Play Console

1. Create a [Play Console developer account](https://play.google.com/console/signup) (one-time fee, identity verification).
   New personal accounts must run a **closed test with at least 12 testers for 14 days** before production access is granted.
2. Create the app. The package name is `io.github.emilmelgaard.pathwake` and can never be changed.
3. Push the first tag, download the AAB from the workflow run's artifacts, and upload it **manually** to the
   internal testing track (the Play API can only publish to apps that already have an upload).
4. Complete the store listing, content rating, target audience and Data safety form, and add a privacy policy URL.
5. Under Policy > App content, declare the alarm permissions (all are core alarm-clock functionality):
   - **Exact alarms** (`USE_EXACT_ALARM`): the app is an alarm clock; alarms must ring at the time the user set.
   - **Full-screen intent** (`USE_FULL_SCREEN_INTENT`): a ringing alarm shows its puzzles over the lock screen.
   - **Foreground service** (`systemExempted`): the alarm rings (sound, vibration) until its puzzles are solved or
     it times out. If Play Console asks for a video, record an alarm ringing on a locked device.

### 4. Automated Play uploads

1. In Google Cloud, create a project, enable the **Google Play Android Developer API**, create a service account and
   download a JSON key.
2. In Play Console > Users and permissions, invite the service-account e-mail and grant release permissions for this app.
3. `gh secret set PLAY_SERVICE_ACCOUNT_JSON < service-account.json`, then delete the local JSON file.

Optional repository variables (`gh variable set NAME --body VALUE`):

| Variable              | Default    | Notes                                                       |
|-----------------------|------------|-------------------------------------------------------------|
| `PLAY_TRACK`          | `internal` | `internal`, `alpha` (closed), `beta` (open), `production`   |
| `PLAY_RELEASE_STATUS` | `draft`    | Set to `completed` once the app has been published once     |

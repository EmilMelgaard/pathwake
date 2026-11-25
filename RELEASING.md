# Releasing

Pushing a tag `vMAJOR.MINOR.PATCH` runs [.github/workflows/release.yml](.github/workflows/release.yml), which:

1. Builds a signed, R8-minified APK and AAB (`versionName` = tag, `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH`).
2. Creates a GitHub release with the APK, its SHA-256 and the signing certificate's SHA-256, and auto-generated
   release notes. `0.x` versions are marked as pre-releases.
3. Uploads the AAB to Google Play (only once Play credentials are configured, see below).

```sh
git tag v0.1.0
git push origin v0.1.0
```

## One-time setup

### 1. Release key

Every release is signed with the **release key**. Android only installs an update over an app signed with the same
key, so it must never change: if it is lost, everyone who installed from GitHub has to uninstall (losing their
alarms) to get the next version. Keep the key file and its password **outside** the repository, backed up in a
password manager.

It was generated once with (PKCS12, so the key password is the store password):

```sh
keytool -genkeypair -keystore pathwake-release.jks -storetype PKCS12 -alias pathwake -keyalg RSA -keysize 4096 \
  -validity 10000 -dname "CN=Pathwake"
```

### 2. GitHub secrets

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pathwake-release.jks")) | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS        # pathwake
gh secret set RELEASE_KEY_PASSWORD     # same as the store password
```

To build a signed release locally, add the same values to `~/.gradle/gradle.properties`
(`RELEASE_KEYSTORE_FILE=<absolute path to pathwake-release.jks>`, `RELEASE_KEYSTORE_PASSWORD=...`,
`RELEASE_KEY_ALIAS=...`, `RELEASE_KEY_PASSWORD=...`).

## Google Play (not set up yet)

### 3. Play Console

1. Create a [Play Console developer account](https://play.google.com/console/signup) (one-time fee, identity verification).
   New personal accounts must run a **closed test with at least 12 testers for 14 days** before production access is granted.
2. Create the app. The package name is `io.github.emilmelgaard.pathwake` and can never be changed.
3. Play App Signing: choose to **use your own app signing key** and upload the release key (Play Console explains
   how to encrypt it with its PEPK tool). Then Play and GitHub builds have the same signature and can update each
   other. The release key can also be registered as the upload key, which is what the workflow signs the AAB with.
   (If Google generates the app signing key instead, Play and GitHub installs can't update each other.)
4. Push a tag, download the AAB from the workflow run's artifacts, and upload it **manually** to the internal
   testing track (the Play API can only publish to apps that already have an upload).
5. Complete the store listing, content rating, target audience and Data safety form, and add a privacy policy URL.
6. Under Policy > App content, declare the alarm permissions (all are core alarm-clock functionality):
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

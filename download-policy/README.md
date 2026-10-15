# Download servers

Network & internet contains the shared page for OS and Apps downloads: Automatic (the default), Europe only or Canada only. Its footer says what Automatic does and what the servers see.

| Content | Europe | Canada |
| --- | --- | --- |
| OS | `releases.diamaneos.de` | `releases-na.diamaneos.de` |
| Apps | `apps.diamaneos.de` | `apps-na.diamaneos.de` |

The public-SDK client reads `diamaneos_download_server_policy` through Settings.Global. One atomic string contains `2:automatic`, `2:primary_eu` or `2:canada`. Missing or unknown values select Automatic. Authority read failures stop downloads. URLs are not stored.

Version 1 values keep working: `1:primary_eu:0` and `1:canada:0` keep their server, and fallback on (`:1`) becomes Automatic.

Settings and the client's write helper require user 0 and existing `WRITE_SECURE_SETTINGS`. The platform SettingsProvider enforces that permission. Existing privileged platform writers retain device-wide control. There is no custom provider-side restriction. Reads require no additional permission or framework field.

The shared page component is `com.android.settings/com.android.settings.Settings$DownloadServersActivity`. Its action is `de.diamaneos.settings.DOWNLOAD_SERVERS`. The page and write helper use existing `WRITE_SECURE_SETTINGS`. Settings, Setup and Updater use `DiamaneOSDownloadPolicyClient`. Apps carries identical public-SDK sources. Other services do not consume this policy.

Transport uses fixed HTTPS origins and refuses redirects. TLS, redirect, parsing, signature, hash and downgrade failures never move a download to the other server. Native content verification remains required.

Automatic:

- Sends HEAD for a small file both servers serve to each, with bounded timeouts, and tries the faster first. Near ties stay on Europe.
- Keeps that order for ten minutes, and probes again after a failure.
- Moves to the other server after a connection or temporary server failure before the body.
- Continues a download that fails or stalls mid-way on the other server from the same byte offset. The answer must carry the exact `Content-Range` of a file of the same size.
- Tries each server once per download.

Run `python3 -m unittest discover -s download-policy/tests -p 'test_*.py'`. `DOWNLOAD_POLICY_JAVA_HOME` selects a host JDK. The tests use stand-ins for the Android settings authority and HTTPS, so they do not test the Android runtime, the rendered UI or the cryptography.

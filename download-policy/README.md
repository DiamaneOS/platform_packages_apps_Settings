# Download servers

Network & internet contains the shared page for OS and Apps downloads. Europe is the default; Canada and fallback are optional. The selected server sees the client's IP address. Fallback can expose that address to the other region.

| Content | Europe | Canada |
| --- | --- | --- |
| OS | `releases.diamaneos.de` | `releases-na.diamaneos.de` |
| Apps | `apps.diamaneos.de` | `apps-na.diamaneos.de` |

The public-SDK client reads `diamaneos_download_server_policy` through Settings.Global. One atomic string contains `1:primary_eu:0`, `1:primary_eu:1`, `1:canada:0` or `1:canada:1`. Missing or unknown values select Europe with fallback off. Authority read failures stop downloads. URLs are not stored.

Settings and the client's write helper require user 0 and existing `WRITE_SECURE_SETTINGS`. The platform SettingsProvider enforces that permission. Existing privileged platform writers retain device-wide control; there is no custom provider-side restriction. Reads require no additional permission or framework field.

The shared page component is `com.android.settings/com.android.settings.Settings$DownloadServersActivity`; its action is `de.diamaneos.settings.DOWNLOAD_SERVERS`. The page and write helper use existing `WRITE_SECURE_SETTINGS`. Settings and Updater use `DiamaneOSDownloadPolicyClient`; Apps carries identical public-SDK sources. Other services do not consume this policy.

Transport uses fixed HTTPS origins, refuses redirects and permits one opted-in alternate attempt before body consumption for connection or temporary server failures. TLS, parsing, signature, hash and downgrade failures are not retried. Native content verification remains required.

Run `python3 -m unittest discover -s download-policy/tests -p 'test_*.py'`. `DOWNLOAD_POLICY_JAVA_HOME` selects a host JDK. Tests use explicit Android authority and HTTPS doubles; they do not qualify Android runtime, rendered UI or cryptography.

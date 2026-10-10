"""Native JVM unit fixtures, with explicit minimal Android API doubles; no device or network."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'android/Manifest.java': 'package android; public class Manifest { public static class permission { public static final String WRITE_SECURE_SETTINGS="android.permission.WRITE_SECURE_SETTINGS"; } }',
    'android/os/UserManager.java': 'package android.os; public class UserManager { public boolean main=true; public boolean isSystemUser(){return main;} }',
    'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager {\n        public static final int SIGNATURE_MATCH=0,PERMISSION_GRANTED=0; public boolean settingsSigned=true;\n        public int checkSignatures(String a,String b){return settingsSigned?0:-1;} }',
    'android/content/Context.java': 'package android.content; public class Context {\n        public final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();\n        public final android.os.UserManager users=new android.os.UserManager();\n        public final ContentResolver resolver=new ContentResolver();\n        public android.content.pm.PackageManager getPackageManager(){return pm;}\n        public ContentResolver getContentResolver(){return resolver;}\n        public <T> T getSystemService(Class<T> t){return t.cast(users);}\n        public int checkSelfPermission(String p){return resolver.permitted?0:-1;} }',
    'android/content/ContentResolver.java': 'package android.content; public class ContentResolver { public boolean permitted,forceDeny; }',
    'android/provider/Settings.java': 'package android.provider; public class Settings { public static class Global {\n        public static String value; public static int reads,writes; public static boolean failRead,failWrite;\n        private static void key(String k){if(!k.equals("diamaneos_download_server_policy"))throw new AssertionError("Unexpected key");}\n        public static String getString(android.content.ContentResolver r,String k){key(k);reads++;if(failRead)throw new IllegalStateException();return value;}\n        public static boolean putString(android.content.ContentResolver r,String k,String v){key(k);\n            if(!r.permitted||r.forceDeny)throw new SecurityException("WRITE_SECURE_SETTINGS fixture");\n            if(failWrite)return false;value=v;writes++;return true;} } }',
    'android/content/ComponentName.java': 'package android.content; public class ComponentName { public final String pkg,cls; public ComponentName(String p,String c){pkg=p;cls=c;} }',
    'android/content/Intent.java': 'package android.content; public class Intent { public ComponentName component; public Intent(String a){} public Intent setComponent(ComponentName c){component=c;return this;} }',
}


class PolicyHostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scratch = tempfile.TemporaryDirectory(prefix='download-policy-host-')
        cls.output = Path(cls.scratch.name)
        jdk = Path(os.environ.get('DOWNLOAD_POLICY_JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
        cls.java = str(jdk / 'bin/java') if jdk.exists() else shutil.which('java')
        javac = str(jdk / 'bin/javac') if jdk.exists() else shutil.which('javac')
        if not cls.java or not javac:
            raise RuntimeError('A host JDK is required for these unit fixtures')
        for name, value in STUBS.items():
            path = cls.output / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
        sources = list((ROOT / 'client').rglob('*.java'))
        sources += list(cls.output.rglob('*.java')) + [ROOT / 'tests/PolicyHostTest.java']
        subprocess.run([javac, '--release', '17', '-d', str(cls.output)] + [str(p) for p in sources], check=True, capture_output=True, text=True, timeout=30)

    @classmethod
    def tearDownClass(cls):
        cls.scratch.cleanup()

    def run_scenario(self, name):
        result = subprocess.run([self.java, '-cp', str(self.output), 'de.diamaneos.downloads.PolicyHostTest', name], capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_default_closed(self): self.run_scenario('defaults')
    def test_all_persistent_values_and_fixed_origins(self): self.run_scenario('roundtrip')
    def test_path_and_origin_injection(self): self.run_scenario('paths')
    def test_fallback_disabled(self): self.run_scenario('fallback_off')
    def test_fallback_once_each_direction_preserves_range(self): self.run_scenario('fallback_once')
    def test_tls_redirect_and_protocol_failures_do_not_fallback(self): self.run_scenario('tls_and_redirect')
    def test_auth_body_and_other_status_errors_not_retried(self): self.run_scenario('response_and_body')
    def test_bounded_read(self): self.run_scenario('bounds')
    def test_atomic_global_write_and_shared_reads(self): self.run_scenario('global_snapshot')
    def test_main_user_guard_blocks_client_write(self): self.run_scenario('main_user')
    def test_existing_secure_write_permission_and_provider_refusal(self): self.run_scenario('secure_permission')
    def test_sdk_false_write_result_propagates(self): self.run_scenario('write_failure')
    def test_unknown_global_and_read_failure_default_closed(self): self.run_scenario('read_failure')
    def test_existing_trusted_platform_writers_retain_control(self): self.run_scenario('platform_writer')
    def test_link_authenticates_platform_settings(self): self.run_scenario('settings_link')

    def test_no_extra_app_provider_or_permissions(self):
        settings = ROOT.parent
        ns = '{http://schemas.android.com/apk/res/android}'
        doc = ET.parse(settings / 'AndroidManifest.xml').getroot()
        self.assertIn('android.permission.WRITE_SECURE_SETTINGS', [x.get(ns+'name') for x in doc.findall('uses-permission')])
        self.assertNotIn('CONFIGURE_DOWNLOAD_SERVERS', (settings / 'AndroidManifest.xml').read_text())
        self.assertNotIn('android_app', (ROOT / 'Android.bp').read_text())
        self.assertFalse((ROOT / 'AndroidManifest.xml').exists())
        self.assertFalse(list((ROOT / 'provider').rglob('*.java')))

    def test_page_and_external_activity_share_fragment(self):
        settings = ROOT.parent
        ns = '{http://schemas.android.com/apk/res/android}'
        doc = ET.parse(settings / 'AndroidManifest.xml').getroot()
        activity = next(x for x in doc.findall('application/activity') if x.get(ns + 'name') == '.Settings$DownloadServersActivity')
        self.assertTrue(any(x.get(ns + 'value') == 'com.android.settings.network.DownloadServersFragment' for x in activity.findall('meta-data')))
        network = ET.parse(settings / 'res/xml/network_provider_internet.xml').getroot()
        self.assertTrue(any(x.get(ns + 'fragment') == 'com.android.settings.network.DownloadServersFragment' for x in network))
        text = (settings / 'res/values/download_servers.xml').read_text()
        self.assertIn('IP address', text)
        for item in ET.fromstring(text):
            if item.tag == 'string': self.assertLessEqual(len((item.text or '').split()), 45)


if __name__ == '__main__':
    unittest.main()

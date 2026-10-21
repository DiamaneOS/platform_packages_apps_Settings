"""Checks of Settings' download servers page. The policy library itself lives in
vendor/diamaneos/download-policy, with its own tests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

SETTINGS = Path(__file__).resolve().parents[1]
NS = '{http://schemas.android.com/apk/res/android}'


class SettingsPageTests(unittest.TestCase):
    def test_no_extra_permissions(self):
        doc = ET.parse(SETTINGS / 'AndroidManifest.xml').getroot()
        self.assertIn('android.permission.WRITE_SECURE_SETTINGS', [x.get(NS + 'name') for x in doc.findall('uses-permission')])
        self.assertNotIn('CONFIGURE_DOWNLOAD_SERVERS', (SETTINGS / 'AndroidManifest.xml').read_text())

    def test_page_and_external_activity_share_fragment(self):
        doc = ET.parse(SETTINGS / 'AndroidManifest.xml').getroot()
        activity = next(x for x in doc.findall('application/activity') if x.get(NS + 'name') == '.Settings$DownloadServersActivity')
        self.assertTrue(any(x.get(NS + 'value') == 'com.android.settings.network.DownloadServersFragment' for x in activity.findall('meta-data')))
        network = ET.parse(SETTINGS / 'res/xml/network_provider_internet.xml').getroot()
        self.assertTrue(any(x.get(NS + 'fragment') == 'com.android.settings.network.DownloadServersFragment' for x in network))
        text = (SETTINGS / 'res/values/download_servers.xml').read_text()
        self.assertIn('IP address', text)
        for item in ET.fromstring(text):
            if item.tag == 'string': self.assertLessEqual(len((item.text or '').split()), 45)


if __name__ == '__main__':
    unittest.main()

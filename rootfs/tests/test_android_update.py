"""离线验证运行时更新的原子包约束与旧版保留。"""

import hashlib
import importlib.util
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / 'overlays/android_update.py'
spec = importlib.util.spec_from_file_location('android_update', SCRIPT)
update = importlib.util.module_from_spec(spec)
spec.loader.exec_module(update)
TEMP_ROOT = Path(__file__).resolve().parents[2] / '.tmp'


class AndroidUpdateTest(unittest.TestCase):
    def setUp(self):
        TEMP_ROOT.mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=TEMP_ROOT)
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'azurpilot'
        self.root.mkdir()
        (self.root / 'config').mkdir()
        (self.root / 'config/alas.json').write_text('{"user":true}')
        self.current = {'azurpilot_commit': 'a' * 40, 'uv_lock_sha256': 'lock',
                        'adapter_sha256': 'adapter', 'python_version': '3.14.6'}
        (self.root / 'BUILD_MANIFEST').write_text(json.dumps(self.current))
        for name, value in [('ROOT', self.root), ('STAGE', self.base / 'azurpilot.next'),
                            ('PREVIOUS', self.base / 'azurpilot.previous'),
                            ('ARCHIVE', self.base / 'azurpilot-runtime.tar.xz')]:
            patcher = mock.patch.object(update, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def bundle(self, marker=b'fingerprint\n'):
        frontend = b'<html>ok</html>'
        manifest = dict(self.current, azurpilot_commit='b' * 40,
                        frontend_sha256=hashlib.sha256(frontend).hexdigest())
        files = {
            'BUILD_MANIFEST': json.dumps(manifest).encode(),
            'frontend/dist/index.html': frontend,
            'frontend/dist/.source-fingerprint': marker,
            'deploy/frontend.py': b'def source_fingerprint(root): return "fingerprint"\n',
        }
        archive = io.BytesIO()
        with tarfile.open(fileobj=archive, mode='w:xz') as tar:
            for name, data in files.items():
                info = tarfile.TarInfo(name)
                info.size = len(data)
                tar.addfile(info, io.BytesIO(data))
        data = archive.getvalue()
        index = dict(self.current, azurpilot_commit='b' * 40,
                     bundle_url='https://example.invalid/runtime.tar.xz',
                     bundle_sha256=hashlib.sha256(data).hexdigest())
        return data, index

    def test_compatible_bundle_switches_and_preserves_user_config(self):
        data, index = self.bundle()
        with mock.patch.object(update, 'get_json', return_value=index), \
             mock.patch.object(update, 'remote_head', return_value='b' * 40), \
             mock.patch.object(update.urllib.request, 'urlopen', return_value=io.BytesIO(data)), \
             mock.patch.object(update.subprocess, 'run'):
            self.assertEqual(update.main(), 0)
        self.assertEqual(json.loads((self.root / 'BUILD_MANIFEST').read_text())['azurpilot_commit'], 'b' * 40)
        self.assertEqual((self.root / 'config/alas.json').read_text(), '{"user":true}')
        self.assertTrue((self.root / '.android_update_pending').is_file())
        self.assertTrue((self.base / 'azurpilot.previous').is_dir())

    def test_incompatible_dependencies_keep_current_version(self):
        _, index = self.bundle()
        index['uv_lock_sha256'] = 'changed'
        with mock.patch.object(update, 'get_json', return_value=index), \
             mock.patch.object(update, 'remote_head', return_value='b' * 40), \
             mock.patch.object(update.urllib.request, 'urlopen') as download:
            self.assertEqual(update.main(), 0)
            download.assert_not_called()
        self.assertEqual(json.loads((self.root / 'BUILD_MANIFEST').read_text()), self.current)

    def test_checksum_failure_keeps_current_version(self):
        data, index = self.bundle()
        index['bundle_sha256'] = '0' * 64
        with mock.patch.object(update, 'get_json', return_value=index), \
             mock.patch.object(update, 'remote_head', return_value='b' * 40), \
             mock.patch.object(update.urllib.request, 'urlopen', return_value=io.BytesIO(data)):
            self.assertEqual(update.main(), 1)
        self.assertEqual(json.loads((self.root / 'BUILD_MANIFEST').read_text()), self.current)
        self.assertFalse((self.base / 'azurpilot.next').exists())

    def test_frontend_mismatch_keeps_current_version(self):
        data, index = self.bundle(marker=b'wrong\n')
        with mock.patch.object(update, 'get_json', return_value=index), \
             mock.patch.object(update, 'remote_head', return_value='b' * 40), \
             mock.patch.object(update.urllib.request, 'urlopen', return_value=io.BytesIO(data)):
            self.assertEqual(update.main(), 1)
        self.assertEqual(json.loads((self.root / 'BUILD_MANIFEST').read_text()), self.current)

    def test_failed_switch_restores_previous_version(self):
        data, index = self.bundle()
        original_rename = Path.rename

        def rename(path, target):
            if path == update.STAGE:
                raise OSError('simulated atomic switch failure')
            return original_rename(path, target)

        with mock.patch.object(update, 'get_json', return_value=index), \
             mock.patch.object(update, 'remote_head', return_value='b' * 40), \
             mock.patch.object(update.urllib.request, 'urlopen', return_value=io.BytesIO(data)), \
             mock.patch.object(update.subprocess, 'run'), \
             mock.patch.object(Path, 'rename', rename):
            self.assertEqual(update.main(), 1)
        self.assertEqual(json.loads((self.root / 'BUILD_MANIFEST').read_text()), self.current)

    def test_offline_keeps_current_version(self):
        with mock.patch.object(update, 'get_json', side_effect=OSError('offline')):
            self.assertEqual(update.main(), 0)
        self.assertEqual(json.loads((self.root / 'BUILD_MANIFEST').read_text()), self.current)


if __name__ == '__main__':
    unittest.main()

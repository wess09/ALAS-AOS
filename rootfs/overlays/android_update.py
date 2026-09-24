#!/usr/bin/env python3
"""在 GUI 启动前安装与 APK 适配层兼容的 AzurPilot 源码/前端包。"""

import hashlib
import json
import os
import shutil
import subprocess
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(os.environ.get('AZURPILOT_ROOT', '/opt/azurpilot'))
INDEX_URL = os.environ.get(
    'AZURPILOT_ANDROID_INDEX_URL',
    'https://github.com/Shinarin/ALAS-AOS/releases/download/azurpilot-runtime/latest.json',
)
SOURCE_REPO = 'https://github.com/wess09/AzurPilot.git'
STAGE = ROOT.with_name('azurpilot.next')
PREVIOUS = ROOT.with_name('azurpilot.previous')
ARCHIVE = ROOT.with_name('azurpilot-runtime.tar.xz')


def sha256(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def get_json(url):
    with urllib.request.urlopen(url, timeout=12) as response:
        return json.load(response)


def remote_head():
    result = subprocess.run(
        ['git', 'ls-remote', SOURCE_REPO, 'refs/heads/dev'],
        check=True, capture_output=True, text=True, timeout=15,
    )
    return result.stdout.split()[0]


def compatible(current, candidate):
    return all(current.get(key) == candidate.get(key) for key in
               ('uv_lock_sha256', 'android_api_version', 'python_version'))


def copy_user_data(source, destination):
    configs = destination / 'config'
    configs.mkdir(exist_ok=True)
    for path in (source / 'config').glob('*.json'):
        if path.stem.startswith('template') or path.name.startswith('deploy'):
            continue
        shutil.copy2(path, configs / path.name)
    if (source / 'log').is_dir():
        shutil.copytree(source / 'log', destination / 'log', dirs_exist_ok=True)


def validate_frontend(stage):
    import importlib.util
    module_path = stage / 'deploy/frontend.py'
    spec = importlib.util.spec_from_file_location('frontend_check', module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    marker = stage / 'frontend/dist/.source-fingerprint'
    return (stage / 'frontend/dist/index.html').is_file() and marker.is_file() \
        and marker.read_text().strip() == module.source_fingerprint(stage / 'frontend')


def main():
    if PREVIOUS.exists() and not ROOT.exists():
        PREVIOUS.rename(ROOT)
    current = json.loads((ROOT / 'BUILD_MANIFEST').read_text(encoding='utf-8'))
    try:
        latest = get_json(INDEX_URL)
        head = remote_head()
    except (OSError, ValueError, subprocess.SubprocessError, IndexError) as exc:
        print(f'UNCHANGED offline: {exc}')
        return 0
    if latest.get('azurpilot_commit') != head:
        print('UNCHANGED runtime bundle has not caught up with dev')
        return 0
    if head == current.get('azurpilot_commit'):
        print(f'UNCHANGED {head}')
        return 0
    if not compatible(current, latest):
        print('DEFERRED AzurPilot requires a compatible APK for new dependencies or Android API')
        return 0
    url = latest.get('bundle_url', '')
    expected = latest.get('bundle_sha256', '')
    if not url.startswith('https://') or len(expected) != 64:
        print('FAILED invalid runtime index')
        return 1
    try:
        with urllib.request.urlopen(url, timeout=30) as response, ARCHIVE.open('wb') as output:
            shutil.copyfileobj(response, output)
        if sha256(ARCHIVE) != expected:
            raise ValueError('runtime bundle checksum mismatch')
        if STAGE.exists():
            shutil.rmtree(STAGE)
        STAGE.mkdir()
        with tarfile.open(ARCHIVE, 'r:xz') as bundle:
            bundle.extractall(STAGE, filter='data')
        manifest = json.loads((STAGE / 'BUILD_MANIFEST').read_text(encoding='utf-8'))
        if not compatible(current, manifest) or manifest.get('azurpilot_commit') != head:
            raise ValueError('runtime bundle manifest mismatch')
        if sha256(STAGE / 'frontend/dist/index.html') != manifest.get('frontend_sha256'):
            raise ValueError('frontend checksum mismatch')
        if not validate_frontend(STAGE):
            raise ValueError('frontend source fingerprint mismatch')
        (STAGE / '.venv').symlink_to('../azurpilot-venv', target_is_directory=True)
        copy_user_data(ROOT, STAGE)
        subprocess.run(
            [str(STAGE / '.venv/bin/python'), '-c',
             'import module.api.app, module.device.device, module.ocr.al_ocr'],
            cwd=STAGE, check=True, timeout=45,
        )
        (STAGE / '.android_update_pending').write_text(head + '\n', encoding='utf-8')
        if PREVIOUS.exists():
            shutil.rmtree(PREVIOUS)
        ROOT.rename(PREVIOUS)
        try:
            STAGE.rename(ROOT)
        except OSError:
            PREVIOUS.rename(ROOT)
            raise
        print(f'UPDATED {head}')
        return 0
    except (OSError, ValueError, tarfile.TarError, subprocess.SubprocessError) as exc:
        print(f'FAILED {exc}')
        return 1
    finally:
        ARCHIVE.unlink(missing_ok=True)


if __name__ == '__main__':
    raise SystemExit(main())

#!/usr/bin/env python3
"""首次部署时创建 Android 虚拟屏实例，不覆盖用户配置。"""

import json
import os
from pathlib import Path


def main():
    root = Path(os.environ.get('AZURPILOT_ROOT', '/opt/azurpilot'))
    source = root / 'config/template.json'
    target = root / 'config/alas.json'
    if target.exists():
        print('AzurPilot instance already exists')
        return
    data = json.loads(source.read_text(encoding='utf-8'))
    emulator = data['Alas']['Emulator']
    emulator.update({
        'Serial': 'azurpilot_android',
        'PackageName': 'com.bilibili.azurlane',
        'ScreenshotMethod': 'azurpilot_android',
        'ControlMethod': 'azurpilot_android',
        'ScreenshotDedithering': False,
    })
    data['Alas']['Optimization'].update({
        'OcrDevice': 'cpu',
        'OcrBackend': 'onnxruntime',
    })
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Seeded AzurPilot Android instance')


if __name__ == '__main__':
    main()

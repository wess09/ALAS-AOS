#!/usr/bin/env python3
"""首次部署时创建 Android 虚拟屏实例，不覆盖用户配置。"""

import json
import os
from pathlib import Path
from typing import Any

# 实例配置文件名：上游 module/config/utils.py 的 DEFAULT_CONFIG_NAME（当前为 'ap'），
# 由 filepath_config() 拼成 ./config/<name>.json。要跟随上游改名，只需改这一行。
INSTANCE_FILE = 'config/ap.json'

# 全局段（Emulator / Optimization / Storage 所在的那一节）在上游 schema 里唯一，
# 按结构定位而不写死段名：上游若改段名，这里自动跟上；结构一旦变动，下面的断言会当场报错。
GLOBAL_SECTION_MARKERS = {'Emulator', 'Optimization'}


def global_section(data: dict[str, Any]) -> dict[str, Any]:
    found = [
        key for key, value in data.items()
        if isinstance(value, dict) and GLOBAL_SECTION_MARKERS <= set(value)
    ]
    if len(found) != 1:
        raise SystemExit(f'上游 schema 变动：全局段不唯一 {found}')
    return data[found[0]]


def main() -> None:
    root = Path(os.environ.get('AZURPILOT_ROOT', '/opt/azurpilot'))
    source = root / 'config/template.json'
    target = root / INSTANCE_FILE
    if target.exists():
        print('AzurPilot instance already exists')
        return
    data = json.loads(source.read_text(encoding='utf-8'))
    section = global_section(data)
    section['Emulator'].update({
        'Serial': 'azurpilot_android',
        'PackageName': 'com.bilibili.azurlane',
        'ScreenshotMethod': 'azurpilot_android',
        'ControlMethod': 'azurpilot_android',
        'ScreenshotDedithering': False,
    })
    section['Optimization'].update({
        'OcrDevice': 'cpu',
        'OcrBackend': 'onnxruntime',
    })
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Seeded AzurPilot Android instance')


if __name__ == '__main__':
    main()

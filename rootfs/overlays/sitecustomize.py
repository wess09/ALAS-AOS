"""仅在 AzurPilot Android 运行时启用进程枚举兼容层。"""

import os

if os.environ.get("AZURPILOT_ANDROID") == "1":
    from android_process_compat import install

    install()

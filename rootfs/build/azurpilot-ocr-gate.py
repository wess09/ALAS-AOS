#!/usr/bin/env python3
"""对 AzurPilot 使用的 ARM64 ONNX OCR 模型做实际 CPU 推理。"""

from pathlib import Path

import numpy as np
import onnxruntime as ort


ROOT = Path(__file__).resolve().parent
MODELS = (
    'bin/ocr_models/ppocr-v6/PP-OCRv6_tiny_rec.onnx',
    'bin/ocr_models/azur_lane/alocr-en-us-v2.6.nvc.onnx',
    'bin/ocr_models/zh-CN/alocr-zh-cn-v3.dtk.onnx',
)


def main():
    for relative in MODELS:
        model = ROOT / relative
        if not model.is_file():
            raise FileNotFoundError(model)
        options = ort.SessionOptions()
        options.log_severity_level = 3
        session = ort.InferenceSession(str(model), sess_options=options,
                                       providers=['CPUExecutionProvider'])
        source = session.get_inputs()[0]
        shape = tuple(d if isinstance(d, int) else (320 if index == 3 else 1)
                      for index, d in enumerate(source.shape))
        inputs = np.zeros(shape, dtype=np.float32)
        output = session.run(None, {source.name: inputs})
        if not output or not all(np.isfinite(value).all() and value.size for value in output):
            raise RuntimeError(f'OCR model inference invalid: {relative}')
        print(f'OCR_OK {relative}: {[value.shape for value in output]}')


if __name__ == '__main__':
    main()

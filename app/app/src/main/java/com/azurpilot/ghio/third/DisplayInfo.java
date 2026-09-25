package com.azurpilot.ghio.third;


public record DisplayInfo(int displayId, Size size, int rotation, int layerStack, int flags,
                          int dpi, String uniqueId) {
    public static final int FLAG_SUPPORTS_PROTECTED_BUFFERS = 0x00000001;
}

#pragma once

namespace vibelight {
// Explicit per-host overrides. Other settings continue to inherit global defaults.
struct HostProfile {
    int width = 1920;
    int height = 1080;
    int fps = 60;
    int bitrateKbps = 20000;
    bool adaptiveBitrate = false;

    bool valid() const {
        return width >= 256 && width <= 8192 && height >= 256 && height <= 8192 &&
               fps >= 10 && fps <= 9999 && bitrateKbps >= 500 && bitrateKbps <= 150000;
    }
};
}

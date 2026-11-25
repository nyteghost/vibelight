#pragma once
#include "streamcheckmetrics.h"
#include <algorithm>
namespace vibelight {
struct NetworkBitrateAdvice { int kbps = 0; const char* reason = "Insufficient samples; play a moving game and retry"; };
inline NetworkBitrateAdvice networkBitrateAdvice(const CheckMetrics& m, unsigned elapsed, int kbps, int fps, bool stable) {
    if (!stable || kbps < 500 || kbps > 500000 || fps <= 0 || elapsed < 18000 ||
        !m.countersValid || !m.sufficientVideo || !m.decodeAvailable ||
        !std::isfinite(m.observedFps) || !std::isfinite(m.decodeMs) ||
        !std::isfinite(m.networkDropPercent) || !std::isfinite(m.pacerDropPercent) ||
        m.observedFps < fps * 0.8) return {};
    if (m.decodeMs >= 1000.0 / fps || m.pacerDropPercent >= 2.0)
        return {0, "Decoder or frame pacing pressure; bitrate alone may not help"};
    if (m.networkDropPercent >= 1.0)
        return {std::max(500, (kbps * 3 / 4 / 500) * 500), "Network frame loss: try a lower target, then retest"};
    if (m.networkDropPercent >= 0.2)
        return {std::max(500, (kbps * 9 / 10 / 500) * 500), "Some network frame loss: try a modest reduction, then retest"};
    return {kbps, "Current target looks usable in this sample; maximum bandwidth is unknown"};
}
}

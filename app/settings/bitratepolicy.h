#pragma once

namespace vibelight {
// A global lock has precedence over both global and per-host adaptation.
constexpr bool allowAdaptiveBitrate(bool requested, bool locked) {
    return requested && !locked;
}
}

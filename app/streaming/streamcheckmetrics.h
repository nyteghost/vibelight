#pragma once
#include <cstdint>
#include <cmath>

namespace vibelight {
struct CheckCounters {
    std::uint32_t frames = 0, networkDropped = 0, decoded = 0, pacerDropped = 0;
    std::uint64_t decodeUs = 0;
};
struct CheckMetrics {
    bool countersValid = false, sufficientVideo = false, decodeAvailable = false;
    double observedFps = 0, networkDropPercent = 0, decodeMs = 0, pacerDropPercent = 0;
};
inline CheckMetrics checkMetrics(CheckCounters start, CheckCounters end, std::uint32_t elapsedMs) {
    CheckMetrics out;
    if (!elapsedMs || end.frames < start.frames || end.networkDropped < start.networkDropped ||
        end.decoded < start.decoded || end.pacerDropped < start.pacerDropped || end.decodeUs < start.decodeUs) return out;
    const auto frames = end.frames - start.frames, dropped = end.networkDropped - start.networkDropped;
    const auto decoded = end.decoded - start.decoded, late = end.pacerDropped - start.pacerDropped;
    if (dropped > frames || late > decoded) return out;
    out.countersValid = true;
    out.sufficientVideo = elapsedMs >= 8000 && frames >= 120;
    out.observedFps = 1000.0 * frames / elapsedMs;
    out.networkDropPercent = frames ? 100.0 * dropped / frames : 0;
    out.decodeAvailable = decoded > 0;
    if (decoded) {
        out.decodeMs = (end.decodeUs - start.decodeUs) / 1000.0 / decoded;
        out.pacerDropPercent = 100.0 * late / decoded;
    }
    return out;
}
}

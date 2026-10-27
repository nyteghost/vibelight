#pragma once

// Small pieces of Vibelight logic with no Qt or SDL dependencies, so they can be unit tested
// on their own (see tests/logic_tests.cpp, run by CI).

#include <algorithm>
#include <cmath>
#include <string>

namespace vibelight {

// Maps one ASCII character to the Windows virtual-key code that types it on a US keyboard.
// Returns false for characters that have no key (accents, emoji, other control characters).
inline bool asciiToKey(char c, unsigned short& vk, bool& shift)
{
    shift = false;

    if (c >= 'a' && c <= 'z') { vk = (unsigned short)(0x41 + (c - 'a')); return true; }
    if (c >= 'A' && c <= 'Z') { vk = (unsigned short)(0x41 + (c - 'A')); shift = true; return true; }
    if (c >= '0' && c <= '9') { vk = (unsigned short)(0x30 + (c - '0')); return true; }

    static const char kShiftedDigits[] = ")!@#$%^&*(";
    for (int i = 0; i < 10; i++) {
        if (c == kShiftedDigits[i]) { vk = (unsigned short)(0x30 + i); shift = true; return true; }
    }

    static const struct { char plain; char shifted; unsigned short vk; } kPunct[] = {
        {'-', '_', 0xBD}, {'=', '+', 0xBB}, {'[', '{', 0xDB}, {']', '}', 0xDD},
        {'\\', '|', 0xDC}, {';', ':', 0xBA}, {'\'', '"', 0xDE}, {',', '<', 0xBC},
        {'.', '>', 0xBE}, {'/', '?', 0xBF}, {'`', '~', 0xC0},
    };
    for (const auto& p : kPunct) {
        if (c == p.plain) { vk = p.vk; return true; }
        if (c == p.shifted) { vk = p.vk; shift = true; return true; }
    }

    if (c == ' ') { vk = 0x20; return true; }
    if (c == '\n' || c == '\r') { vk = 0x0D; return true; }
    if (c == '\t') { vk = 0x09; return true; }

    return false;
}

// The bitrate (in whole Mbps) to suggest after a connection check.
//   dropPercent  - share of frames the network dropped during the check
//   rttMs        - round-trip time, 0 if unknown
//   receivedMbps - video bitrate the stream actually received during the check
inline double suggestBitrateMbps(double currentMbps, double dropPercent, unsigned int rttMs, double receivedMbps)
{
    double suggested = currentMbps;

    if (dropPercent >= 3.0) {
        suggested = currentMbps * 0.6;
    }
    else if (dropPercent >= 1.0) {
        suggested = currentMbps * 0.8;
    }
    else if (dropPercent < 0.3 && (rttMs == 0 || rttMs < 30) && receivedMbps >= currentMbps * 0.8) {
        // Clean, and the stream is actually using what it was given: there may be room to grow
        suggested = std::min(currentMbps * 1.25, 150.0);
    }

    return std::max(5.0, (double)(int)(suggested + 0.5));
}

// What a connection check found to be the limiting factor
enum class Bottleneck { None, Network, Jitter, Decoder };

// Everything the connection check measured over its window
struct ConnectionSample {
    double currentMbps = 0;        // configured bitrate
    double receivedMbps = 0;       // bitrate actually received
    double dropPercent = 0;        // frames lost on the network
    unsigned int rttMs = 0;        // 0 if unknown
    unsigned int rttVarianceMs = 0;
    double avgDecodeMs = 0;        // 0 if unknown
    double pacerDropPercent = 0;   // frames decoded but dropped before display
    double fps = 60;               // stream frame rate
};

struct BitrateAdvice {
    double mbps = 0;               // bitrate to suggest (whole Mbps)
    Bottleneck bottleneck = Bottleneck::None;
};

// Like suggestBitrateMbps, but also tells a slow network from a slow PC. Lowering the bitrate
// helps with the first and does little for the second, so a decoder-limited stream is left at
// its bitrate and the caller suggests a lighter resolution, frame rate or codec instead.
inline BitrateAdvice analyzeConnection(const ConnectionSample& s)
{
    BitrateAdvice advice;
    advice.mbps = suggestBitrateMbps(s.currentMbps, s.dropPercent, s.rttMs, s.receivedMbps);

    const double frameMs = 1000.0 / (s.fps > 1.0 ? s.fps : 60.0);
    const bool networkLossy = s.dropPercent >= 1.0;
    // Decoding takes most of the frame time, or frames reach the PC but are dropped before display
    const bool decoderBound = (s.avgDecodeMs > frameMs * 0.8) ||
                              (s.pacerDropPercent >= 2.0 && !networkLossy);
    // A jumpy connection: round-trip variance is large next to the round-trip itself
    const bool jittery = s.rttVarianceMs >= 15 && s.rttVarianceMs * 2 >= s.rttMs;

    if (decoderBound && !networkLossy) {
        advice.bottleneck = Bottleneck::Decoder;
        advice.mbps = std::max(5.0, (double)(int)(s.currentMbps + 0.5));
    }
    else if (networkLossy) {
        advice.bottleneck = Bottleneck::Network;
    }
    else if (jittery) {
        // No loss yet, but the path is unsteady: don't grow, shave a little for headroom
        advice.bottleneck = Bottleneck::Jitter;
        advice.mbps = std::max(5.0, (double)(int)(s.currentMbps * 0.9 + 0.5));
    }

    return advice;
}

// Letters that can be the last key of the shortcuts menu hotkey (Ctrl+Alt+Shift+<letter>). The
// others are taken by Vibelight/Moonlight actions: Q Z X S M C D V L E K.
inline const char* menuHotkeyChoices()
{
    return "ABFGHIJNOPRTWY";
}

// Turns a saved setting into a valid hotkey letter, falling back to H.
inline char sanitizeMenuHotkey(const std::string& saved)
{
    if (saved.size() == 1) {
        char c = saved[0];
        if (c >= 'a' && c <= 'z') {
            c = (char)(c - 'a' + 'A');
        }
        for (const char* p = menuHotkeyChoices(); *p != '\0'; p++) {
            if (*p == c) {
                return c;
            }
        }
    }
    return 'H';
}

} // namespace vibelight

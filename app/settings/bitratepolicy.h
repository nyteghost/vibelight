#pragma once

namespace vibelight {
constexpr bool allowAdaptiveBitrate(bool requested, bool locked) {
    return requested && !locked;
}

// A profile selects the settings of the ONE client controller, never another
// controller. Snapshot this policy for the session (including reconnects).
struct BitratePolicy {
    bool requested = false;
    bool locked = false;
    bool hostProfile = false;

    bool adaptive() const { return allowAdaptiveBitrate(requested, locked); }
    const char* settingsSource() const { return hostProfile ? "host_profile" : "global_or_cli"; }
};

constexpr BitratePolicy resolveBitratePolicy(bool globalAdaptive, bool globalLock,
                                            bool hostLock, bool validProfile, bool profileAdaptive) {
    return {validProfile ? profileAdaptive : globalAdaptive,
            globalLock || hostLock, validProfile};
}
}

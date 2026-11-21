#pragma once
#include <atomic>

namespace vibelight {
// Process-local: a queued check must never survive application restart.
inline std::atomic<bool>& pendingNetworkTest() {
    static std::atomic<bool> pending{false};
    return pending;
}
inline void queueNetworkTest(bool queued) { pendingNetworkTest().store(queued); }
inline bool networkTestQueued() { return pendingNetworkTest().load(); }
inline bool takeNetworkTest() { return pendingNetworkTest().exchange(false); }
}

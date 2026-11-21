#include "streaming/sessionbitrate.h"
#include <cassert>
#include <cstdio>
#include <type_traits>

using namespace vibelight;
static_assert(!std::is_copy_constructible<SessionBitrateController>::value,
              "A session must not duplicate its live owner/cooldown timeline");

constexpr auto automatic = BitrateProposalSource::ClientAutomatic;

static void selection() {
    // All combinations: the profile overrides (including explicit OFF), while
    // either lock wins. It cannot create a second live controller.
    for (bool global : {false, true}) for (bool profile : {false, true})
    for (bool valid : {false, true}) for (bool globalLock : {false, true})
    for (bool hostLock : {false, true}) {
        const auto p = resolveBitratePolicy(global, globalLock, hostLock, valid, profile);
        assert(p.requested == (valid ? profile : global));
        assert(p.hostProfile == valid);
        assert(p.adaptive() == ((valid ? profile : global) && !globalLock && !hostLock));
        SessionBitrateController stream(50000, p, false, false);
        assert(stream.active() == p.adaptive());
        assert((stream.request(automatic, 10000, 5, false) > 0) == p.adaptive());
    }
    auto saved = resolveBitratePolicy(true, false, false, false, false);
    SessionBitrateController snapshot(50000, saved, false, false);
    saved.locked = true;
    saved.requested = false;
    assert(snapshot.request(automatic, 10000, 5, false) == 30000);
    for (bool secondary : {false, true}) for (bool nvidia : {false, true}) {
        SessionBitrateController stream(50000, {true, false, false}, secondary, nvidia);
        assert(stream.active() == (!secondary && !nvidia));
    }
    SessionBitrateController invalid(0, {true, false, false}, false, false);
    assert(!invalid.active());
}

static void singleOwnerAndCooldowns() {
    SessionBitrateController stream(50000, {true, false, true}, false, false);
    // Advisory proposals have no live mutation authority, even with ABR on.
    assert(stream.request(BitrateProposalSource::HostAdvisor, 10000, 9, false) == 0);
    assert(stream.request(BitrateProposalSource::NetworkCheck, 10000, 9, false) == 0);
    assert(stream.complete(9000) == BitrateCompletion::Ignored);
    assert(stream.confirmedKbps() == 50000);
    assert(stream.request(automatic, 10000, 5, false) == 30000);
    assert(stream.pending());
    assert(stream.confirmedKbps() == 50000); // Tentative, not acknowledged.
    assert(stream.request(automatic, 15000, 5, false) == 0); // No second worker.
    assert(stream.complete(25000) == BitrateCompletion::Applied); // Host cap.
    assert(!stream.pending());
    assert(stream.complete(70000) == BitrateCompletion::Ignored); // Duplicate/stale result.
    assert(stream.confirmedKbps() == 25000);
    // Non-owner input must not bypass OR extend the owner's existing 8s cooldown.
    assert(stream.request(BitrateProposalSource::HostAdvisor, 17000, 10, false) == 0);
    assert(stream.request(automatic, 17999, 5, false) == 0);
    assert(stream.request(automatic, 18000, 5, false) == 15000);
    assert(stream.complete(-1) == BitrateCompletion::Failed);
    assert(stream.confirmedKbps() == 25000);
    assert(stream.request(automatic, 25000, 5, false) == 0); // Failure keeps cooldown.
    assert(stream.request(automatic, 26000, 5, false) == 15000);
    assert(stream.complete(-1) == BitrateCompletion::Paused);
    assert(!stream.active());
    assert(stream.request(automatic, 90000, 5, false) == 0);
    // A new session starts with independent settings, failures and timers.
    SessionBitrateController reconnect(40000, {true, false, false}, false, false);
    assert(reconnect.request(automatic, 10000, 5, false) == 24000);
}

static void preserveExistingSingleController() {
    // Compare requests against the pre-existing algorithm over long deterministic
    // traces: cut/hold/raise/backoff, decoder pressure, clamps, success and failure.
    for (bool profile : {false, true}) for (int ceiling : {10000, 20000, 50000, 150000}) {
        AutoBitrateController legacy(ceiling / 1000.0);
        SessionBitrateController stream(ceiling, {true, false, profile}, false, false);
        int confirmed = ceiling, decisions = 0;
        for (unsigned long long now = 10000; now <= 1200000; now += 5000) {
            const auto phase = now % 240000;
            const double loss = phase < 25000 ? 5.0 : phase < 40000 ? 1.7 : 0.0;
            const bool decoder = phase > 180000 && phase < 200000;
            const int expected = static_cast<int>(legacy.update(now, loss, decoder) * 1000);
            // Read-only advice between samples cannot reset the shared timeline.
            assert(stream.request(BitrateProposalSource::NetworkCheck, now, 20, false) == 0);
            assert(stream.request(automatic, now, loss, decoder) == expected);
            if (!expected) continue;
            ++decisions;
            const int applied = decisions % 4 == 0 ? -1 : (std::max)(500, expected - 500);
            if (applied > 0) confirmed = applied;
            legacy.syncAppliedMbps(confirmed / 1000.0);
            assert(stream.complete(applied) == (applied > 0 ? BitrateCompletion::Applied : BitrateCompletion::Failed));
            assert(stream.confirmedKbps() == confirmed);
        }
        assert(decisions > 5);
    }
}

int main() {
    selection();
    singleOwnerAndCooldowns();
    preserveExistingSingleController();
    std::puts("Bitrate ownership, shared cooldown and legacy-equivalence tests passed");
}

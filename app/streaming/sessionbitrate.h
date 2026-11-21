#pragma once

#include "settings/bitratepolicy.h"
#include "vibelight_logic.h"

namespace vibelight {

enum class BitrateProposalSource { ClientAutomatic, HostAdvisor, NetworkCheck };
enum class BitrateCompletion { Ignored, Applied, Failed, Paused };

// SDL event-loop owned. All live client decisions share this controller's
// cooldown/backoff state and one pending request. Worker threads only return
// results; they do not select an owner, mutate preferences or drive the policy.
// Advisors remain read-only; explicit network-check Apply affects NEXT session.
class SessionBitrateController {
public:
    SessionBitrateController(int ceilingKbps, BitratePolicy policy,
                            bool secondaryWindow, bool nvidiaHost)
        : m_Policy(policy),
          m_Enabled(policy.adaptive() && !secondaryWindow && !nvidiaHost && ceilingKbps > 0),
          m_Automatic(ceilingKbps / 1000.0), m_ConfirmedKbps(ceilingKbps) {}

    SessionBitrateController(const SessionBitrateController&) = delete;
    SessionBitrateController& operator=(const SessionBitrateController&) = delete;

    bool active() const { return m_Enabled && m_Failures < 2; }
    bool pending() const { return m_Pending; }
    int confirmedKbps() const { return m_ConfirmedKbps; }
    const char* owner() const { return m_Enabled ? "client_automatic" : "fixed"; }
    const char* settingsSource() const { return m_Policy.settingsSource(); }

    int request(BitrateProposalSource source, unsigned long long nowMs,
                double frameLossPercent, bool decoderBound) {
        // Do not even feed rejected proposals into the hysteresis/cooldown state.
        if (!active() || m_Pending || source != BitrateProposalSource::ClientAutomatic)
            return 0;
        const double next = m_Automatic.update(nowMs, frameLossPercent, decoderBound);
        if (next <= 0) return 0;
        m_Pending = true;
        return static_cast<int>(next * 1000);
    }

    BitrateCompletion complete(int appliedKbps) {
        if (!m_Pending) return BitrateCompletion::Ignored;
        m_Pending = false;
        if (appliedKbps <= 0) {
            // Failure rolls back the tentative rate, NOT its cooldown/backoff.
            m_Automatic.syncAppliedMbps(m_ConfirmedKbps / 1000.0);
            return ++m_Failures >= 2 ? BitrateCompletion::Paused : BitrateCompletion::Failed;
        }
        m_Failures = 0;
        m_ConfirmedKbps = appliedKbps;
        m_Automatic.syncAppliedMbps(appliedKbps / 1000.0);
        return BitrateCompletion::Applied;
    }

private:
    const BitratePolicy m_Policy;
    const bool m_Enabled;
    AutoBitrateController m_Automatic; // Sole cooldown and hysteresis timeline.
    int m_ConfirmedKbps;
    int m_Failures = 0;
    bool m_Pending = false;
};
}

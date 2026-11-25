#pragma once

namespace vibelight {

// Desktop mode forwards absolute coordinates without confining the local cursor.
// Keep the normal mode separately so disabling the override restores it.
class MouseCapturePolicy {
public:
    MouseCapturePolicy(bool absoluteMode, bool keepUnlocked)
        : m_AbsoluteMode(absoluteMode), m_KeepUnlocked(keepUnlocked) {}

    bool absoluteMode() const { return m_KeepUnlocked || m_AbsoluteMode; }
    bool keepUnlocked() const { return m_KeepUnlocked; }
    void setKeepUnlocked(bool enabled) { m_KeepUnlocked = enabled; }
    bool confinePointer(bool inputActive, bool regionLock) const {
        return inputActive && regionLock && !m_KeepUnlocked;
    }

    // Explicitly requesting game mouse mode also exits the saved override.
    void toggleMouseMode() {
        m_AbsoluteMode = !absoluteMode();
        m_KeepUnlocked = false;
    }

private:
    bool m_AbsoluteMode;
    bool m_KeepUnlocked;
};

}

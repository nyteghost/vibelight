#include "settings/mousecapturepolicy.h"

#include <cassert>
#include <cstdio>
#include <initializer_list>

int main()
{
    using vibelight::MouseCapturePolicy;
    for (bool normalAbsolute : {false, true}) {
        MouseCapturePolicy policy(normalAbsolute, false);
        assert(policy.absoluteMode() == normalAbsolute);
        for (bool active : {false, true}) {
            for (bool requestedLock : {false, true}) {
                assert(policy.confinePointer(active, requestedLock) == (active && requestedLock));
            }
        }

        policy.setKeepUnlocked(true);
        // Resume clicks/menu dismissal, focus changes and resize/fullscreen
        // recomputation all preserve the override, including requested locks.
        for (bool active : {false, true}) {
            for (bool requestedLock : {false, true}) {
                assert(policy.absoluteMode());
                assert(!policy.confinePointer(active, requestedLock));
                assert(policy.keepUnlocked());
            }
        }
        policy.setKeepUnlocked(true); // Idempotent: don't lose the normal mode.
        policy.setKeepUnlocked(false);
        assert(policy.absoluteMode() == normalAbsolute);
        assert(policy.confinePointer(true, true));

        policy.setKeepUnlocked(true);
        policy.toggleMouseMode(); // Explicit game-mode hotkey exits the override.
        assert(!policy.keepUnlocked());
        assert(!policy.absoluteMode());
        policy.toggleMouseMode();
        assert(policy.absoluteMode());
        assert(!policy.keepUnlocked());
    }

    // Starting a later stream directly in the saved mode needs no release key.
    MouseCapturePolicy nextStream(false, true);
    assert(nextStream.absoluteMode());
    assert(!nextStream.confinePointer(true, true));
    std::puts("Persistent mouse capture policy tests passed");
}

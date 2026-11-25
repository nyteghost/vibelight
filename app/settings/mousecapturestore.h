#pragma once

#include <QSettings>

namespace vibelight {

inline bool readKeepMouseUnlocked(QSettings& settings) {
    return settings.value("vibelight/keepMouseUnlocked", false).toBool();
}

inline void saveKeepMouseUnlocked(QSettings& settings, bool enabled) {
    settings.setValue("vibelight/keepMouseUnlocked", enabled);
}

}

#include "settings/mousecapturepolicy.h"
#include "settings/mousecapturestore.h"

#include <QTemporaryDir>
#include <cassert>
#include <cstdio>

int main()
{
    QTemporaryDir directory;
    assert(directory.isValid());
    const auto file = directory.filePath("settings.ini");
    {
        QSettings settings(file, QSettings::IniFormat);
        assert(!vibelight::readKeepMouseUnlocked(settings)); // Existing installs unchanged.
        settings.setValue("mouseacceleration", false);
        settings.setValue("bitrate", 50000);
        vibelight::saveKeepMouseUnlocked(settings, true);
        settings.sync();
        assert(settings.status() == QSettings::NoError);
    }
    {
        QSettings reopened(file, QSettings::IniFormat);
        vibelight::MouseCapturePolicy nextStream(reopened.value("mouseacceleration").toBool(),
                                                vibelight::readKeepMouseUnlocked(reopened));
        assert(nextStream.keepUnlocked());
        assert(nextStream.absoluteMode());
        assert(!nextStream.confinePointer(true, true));
        assert(!reopened.value("mouseacceleration").toBool()); // Override preserves base mode.
        assert(reopened.value("bitrate").toInt() == 50000);
        nextStream.toggleMouseMode();
        vibelight::saveKeepMouseUnlocked(reopened, nextStream.keepUnlocked());
        reopened.sync();
        assert(reopened.status() == QSettings::NoError);
    }
    QSettings disabled(file, QSettings::IniFormat);
    assert(!vibelight::readKeepMouseUnlocked(disabled));
    assert(disabled.value("bitrate").toInt() == 50000);
    std::puts("Persistent mouse setting round-trip tests passed");
}

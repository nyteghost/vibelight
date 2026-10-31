#include "settings/hostprofilestore.h"
#include <QCoreApplication>
#include <QTemporaryDir>
#include <QFile>
#include <cstdio>
#include <cstdlib>

#define CHECK(test) do { if (!(test)) { std::fprintf(stderr, "FAIL %d: %s\n", __LINE__, #test); std::exit(1); } } while (false)
int main(int argc, char** argv) {
    QCoreApplication app(argc, argv);
    QTemporaryDir temporary;
    CHECK(temporary.isValid());
    const auto filename = temporary.filePath("profiles.ini");
    const QString hostA = "opaque/host A", hostB = "opaque/host B";
    QSettings settings(filename, QSettings::IniFormat);
    settings.setValue("width", 3840);
    settings.setValue("vibelight/monitor/" + hostA, "physical-display");
    vibelight::HostProfile profile{1920, 1080, 120, 40000, true};
    CHECK(!vibelight::readHostProfile(settings, hostA).exists);
    CHECK(vibelight::writeHostProfile(settings, hostA, profile, true));
    CHECK(!vibelight::readHostProfile(settings, hostB).exists);
    {
        QSettings reopened(filename, QSettings::IniFormat);
        const auto loaded = vibelight::readHostProfile(reopened, hostA);
        CHECK(loaded.exists && loaded.valid);
        CHECK(loaded.profile.fps == 120 && loaded.profile.bitrateKbps == 40000 && loaded.profile.adaptiveBitrate);
        CHECK(reopened.value("width").toInt() == 3840);
        CHECK(reopened.value("vibelight/monitor/" + hostA).toString() == "physical-display");
    }
    CHECK(vibelight::writeHostProfile(settings, hostB, {1280, 720, 30, 8000, false}, true));
    CHECK(vibelight::writeHostProfile(settings, hostA, profile, false));
    CHECK(!vibelight::readHostProfile(settings, hostA).exists);
    CHECK(vibelight::readHostProfile(settings, hostB).profile.width == 1280);
    CHECK(settings.value("vibelight/monitor/" + hostA).toString() == "physical-display");
    const auto previous = settings.value(vibelight::hostProfileKey(hostB));
    CHECK(!vibelight::writeHostProfile(settings, hostB, {1, 1080, 60, 10000, false}, true));
    CHECK(settings.value(vibelight::hostProfileKey(hostB)) == previous);
    for (const QByteArray& invalid : {QByteArray("broken"), QByteArray("[]"),
            QByteArray("{\"version\":2}"),
            QByteArray("{\"version\":1,\"width\":1920.5,\"height\":1080,\"fps\":60,\"bitrateKbps\":20000,\"adaptiveBitrate\":true}"),
            QByteArray("{\"version\":1,\"width\":1920,\"height\":1080,\"fps\":60,\"bitrateKbps\":20000,\"adaptiveBitrate\":\"true\"}")}) {
        settings.setValue(vibelight::hostProfileKey(hostA), invalid);
        const auto loaded = vibelight::readHostProfile(settings, hostA);
        CHECK(loaded.exists && !loaded.valid);
        CHECK(settings.value(vibelight::hostProfileKey(hostA)).toByteArray() == invalid);
    }
    CHECK(vibelight::hostProfileKey(hostA) != vibelight::hostProfileKey(hostB));
    CHECK(vibelight::hostProfileKey("").isEmpty());
    CHECK(vibelight::hostProfileKey(QString(257, 'a')).isEmpty());
    CHECK(!vibelight::writeHostProfile(settings, "", profile, true));
    QFile blocker(temporary.filePath("not-a-directory"));
    CHECK(blocker.open(QIODevice::WriteOnly));
    blocker.close();
    QSettings blocked(blocker.fileName() + "/profiles.ini", QSettings::IniFormat);
    CHECK(!vibelight::writeHostProfile(blocked, hostA, profile, true));
    CHECK(!blocked.contains(vibelight::hostProfileKey(hostA)));
    std::puts("All host-profile persistence tests passed");
}

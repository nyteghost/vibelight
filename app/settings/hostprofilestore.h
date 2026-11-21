#pragma once
#include "hostprofile.h"
#include <QJsonDocument>
#include <QJsonObject>
#include <QSettings>

namespace vibelight {
struct StoredHostProfile {
    bool exists = false;
    bool valid = false;
    HostProfile profile;
};
inline QString hostProfileKey(const QString& uuid) {
    // The opaque discovery ID, not an address, display name or list row.
    if (uuid.isEmpty() || uuid.size() > 256) return {};
    return QStringLiteral("vibelight/hostProfiles/v1/") + QString::fromLatin1(uuid.toUtf8().toHex());
}
inline StoredHostProfile readHostProfile(QSettings& settings, const QString& uuid) {
    StoredHostProfile result;
    const auto key = hostProfileKey(uuid);
    if (key.isEmpty() || !settings.contains(key)) return result;
    result.exists = true;
    const auto bytes = settings.value(key).toByteArray();
    if (bytes.size() > 4096) return result;
    const auto document = QJsonDocument::fromJson(bytes);
    if (!document.isObject()) return result;
    const auto object = document.object();
    if (object.value("version").toInt(-1) != 1 || !object.value("adaptiveBitrate").isBool()) return result;
    const auto integer = [&object](const char* name) {
        const auto value = object.value(QLatin1String(name));
        return value.isDouble() ? value.toInt(-1) : -1;
    };
    result.profile = {integer("width"), integer("height"), integer("fps"), integer("bitrateKbps"), object.value("adaptiveBitrate").toBool()};
    result.valid = result.profile.valid();
    return result;
}
inline bool writeHostProfile(QSettings& settings, const QString& uuid, const HostProfile& profile, bool enabled) {
    const auto key = hostProfileKey(uuid);
    if (key.isEmpty() || (enabled && !profile.valid())) return false;
    const bool existed = settings.contains(key);
    const auto previous = settings.value(key);
    if (enabled) {
        QJsonObject object{{"version", 1}, {"width", profile.width}, {"height", profile.height},
                           {"fps", profile.fps}, {"bitrateKbps", profile.bitrateKbps}, {"adaptiveBitrate", profile.adaptiveBitrate}};
        settings.setValue(key, QJsonDocument(object).toJson(QJsonDocument::Compact));
    } else {
        settings.remove(key);
    }
    settings.sync();
    if (settings.status() == QSettings::NoError) return true;
    // Restore the process-local value too when persistence is unavailable.
    if (existed) settings.setValue(key, previous); else settings.remove(key);
    settings.sync();
    return false;
}
}

#pragma once
#include <QJsonObject>
#include <QJsonArray>
#include <QRegularExpression>
#include <QMutex>
#include <QString>
#include <memory>
#include <cmath>

namespace vibelight {
struct CheckExportState {
    QMutex lock;
    bool done = false;
    QString path;
};

inline QJsonObject sanitizeCheckHost(const QJsonObject& source) {
    QJsonObject out{{"status", "unavailable"}};
    if (source.value("schema").toInt() != 1 || source.value("status").toString() != "available") return out;
    out = {{"schema", 1}, {"status", "available"}};
    const auto version = source.value("host_version").toString();
    if (QRegularExpression("^[A-Za-z0-9.+_-]{1,96}$").match(version).hasMatch()) out["host_version"] = version;
    const auto commit = source.value("host_commit").toString();
    if (QRegularExpression("^[0-9a-fA-F]{7,40}$").match(commit).hasMatch()) out["host_commit"] = commit;
    QJsonObject capture{{"status", "unavailable"}};
    const auto raw = source.value("capture").toObject();
    const auto path = raw.value("path").toString();
    if (raw.value("status").toString() == "observed" && (path == "linear_cpu_upload" || path == "egl_gpu_import")) {
        capture = {{"status", "observed"}, {"path", path}, {"scope", "host-wide managed capture; not client-specific"}};
        for (const auto* key : {"samples", "cpu_submission_mean_us", "cpu_submission_max_us", "age_seconds"}) {
            const auto value = raw.value(key);
            if (value.isDouble() && std::isfinite(value.toDouble()) && value.toDouble() >= 0 && value.toDouble() <= 1e12) capture[key] = value;
        }
    }
    out["capture"] = capture;
    // Raw host logs, unexpected JSON fields and error strings are never copied.
    return out;
}

inline QString checkPrompt() {
    return QStringLiteral("Please investigate the attached Vibelight streaming-check ZIP. Correlate the client measurements with the host capture observations. Treat unavailable metrics as unknown; do not infer controller-to-screen latency from software timing. Keep fixes on beta and preserve production/rollback.\n\nConnection: work-to-home / home LAN / other: ___\nCursor visible and aligned: ___\nAudio audible and synchronized: ___\nAfter disconnect, original physical display layout restored: ___\nAfter disconnect, original default audio output restored: ___\nProblem and approximate time: ___\n");
}
}

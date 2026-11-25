#include "streaming/streamcheckreport.h"
#include <QJsonDocument>
#include <cassert>
#include <limits>

int main() {
    using namespace vibelight;
    assert(sanitizeCheckHost({}).value("status") == "unavailable");
    assert(sanitizeCheckHost({{"schema", 2}, {"status", "available"}}).value("status") == "unavailable");
    QJsonObject raw{{"schema", 1}, {"status", "available"}, {"host_version", "2.0.1-beta"},
        {"raw_log", "token=SECRET 192.0.2.1"}, {"hostname", "PRIVATE-HOME"},
        {"capture", QJsonObject{{"status", "observed"}, {"path", "linear_cpu_upload"},
            {"samples", 300}, {"cpu_submission_mean_us", 142.5}, {"age_seconds", -2},
            {"scope", "PRIVATE-UUID"}, {"error", "SECRET"}}}};
    const auto safe = sanitizeCheckHost(raw);
    const auto text = QJsonDocument(safe).toJson();
    assert(!text.contains("SECRET") && !text.contains("PRIVATE") && !text.contains("192.0.2.1"));
    assert(safe.value("capture").toObject().value("cpu_submission_mean_us").toDouble() == 142.5);
    assert(!safe.value("capture").toObject().contains("age_seconds"));
    raw["capture"] = QJsonObject{{"status", "observed"}, {"path", "https://PRIVATE-HOME/secret"}};
    assert(sanitizeCheckHost(raw).value("capture").toObject().value("status") == "unavailable");
    assert(!checkPrompt().contains("192."));
}

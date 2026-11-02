#include "streaming/streamcheckmetrics.h"
#include "streaming/diagnosticzip.h"
#include <cassert>
#include <fstream>

int main(int argc, char** argv) {
    using namespace vibelight;
    const CheckCounters start {100, 2, 90, 1, 90000};
    const CheckCounters end {700, 8, 684, 4, 684000};
    const auto good = checkMetrics(start, end, 10000);
    assert(good.countersValid && good.sufficientVideo && good.decodeAvailable);
    assert(good.observedFps == 60 && good.networkDropPercent == 1 && good.decodeMs == 1);
    assert(!checkMetrics(end, start, 10000).countersValid);
    assert(!checkMetrics(start, end, 0).countersValid);
    assert(!checkMetrics({}, {10, 11, 0, 0, 0}, 10000).countersValid);
    assert(!checkMetrics({}, {100, 0, 10, 11, 0}, 10000).countersValid);
    const auto idle = checkMetrics({}, {}, 10000);
    assert(idle.countersValid && !idle.sufficientVideo && !idle.decodeAvailable);
    assert(!checkMetrics(start, end, 1000).sufficientVideo);
    assert(diagnosticZip(std::string(1024 * 1024 + 1, 'x'), "").empty());
    const auto zip = diagnosticZip("{\"schema\":1,\"status\":\"inconclusive\"}\n", "No secrets. Missing metrics are unknown.\n");
    assert(!zip.empty());
    if (argc == 2) {
        std::ofstream output(argv[1], std::ios::binary);
        output.write(zip.data(), zip.size());
        if (!output) return 1;
    }
}

#pragma once
#include <cstdint>
#include <string>
#include <utility>
#include <vector>

namespace vibelight {
// Stored ZIP, two fixed UTF-8 report files. No dependency on private Qt APIs,
// archive extraction, shell commands, user-controlled filenames or raw logs.
inline std::string diagnosticZip(const std::string& json, const std::string& text) {
    if (json.size() > 1024 * 1024 || text.size() > 1024 * 1024) return {};
    std::string out, directory;
    auto word = [](std::string& s, std::uint32_t value, int bytes) {
        for (int i = 0; i < bytes; ++i) s.push_back(static_cast<char>(value >> (8 * i)));
    };
    for (const auto& file : std::vector<std::pair<std::string, std::string>>{{"report.json", json}, {"README.txt", text}}) {
        std::uint32_t crc = 0xFFFFFFFFu;
        for (unsigned char ch : file.second) {
            crc ^= ch;
            for (int bit = 0; bit < 8; ++bit) crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
        }
        crc ^= 0xFFFFFFFFu;
        const auto offset = static_cast<std::uint32_t>(out.size());
        word(out, 0x04034b50, 4); word(out, 20, 2); word(out, 0x800, 2);
        word(out, 0, 2); word(out, 0, 2); word(out, 33, 2); word(out, crc, 4);
        word(out, file.second.size(), 4); word(out, file.second.size(), 4);
        word(out, file.first.size(), 2); word(out, 0, 2); out += file.first; out += file.second;
        word(directory, 0x02014b50, 4); word(directory, 20, 2); word(directory, 20, 2);
        word(directory, 0x800, 2); word(directory, 0, 2); word(directory, 0, 2); word(directory, 33, 2);
        word(directory, crc, 4); word(directory, file.second.size(), 4); word(directory, file.second.size(), 4);
        word(directory, file.first.size(), 2); word(directory, 0, 2); word(directory, 0, 2);
        word(directory, 0, 2); word(directory, 0, 2); word(directory, 0, 4); word(directory, offset, 4);
        directory += file.first;
    }
    const auto offset = out.size(); out += directory;
    word(out, 0x06054b50, 4); word(out, 0, 2); word(out, 0, 2);
    word(out, 2, 2); word(out, 2, 2); word(out, directory.size(), 4); word(out, offset, 4); word(out, 0, 2);
    return out;
}
}

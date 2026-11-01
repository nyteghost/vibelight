# Vibelight TV — Shield beta

A separate Android-native client in the Vibelight repository. Windows builds do
not depend on this directory. Development targets `beta/next-features-20261028`.

**First functional beta, not a Shield-validated release.** It combines a native
Vibelight TV dashboard with a pinned Moonlight Android streaming foundation.

- [Install, pair, use controllers, update and troubleshoot](docs/SHIELD_GUIDE.md)
- [Architecture, compatibility and upstream attribution](docs/ARCHITECTURE.md)
- [Roadmap and exact validation boundaries](docs/ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Obsidian-ready project note](docs/OBSIDIAN.md)
- [APK builds](https://github.com/nyteghost/vibelight/actions/workflows/android-tv.yml)

## Build from source

Requires Java 17, Android SDK 34 / build-tools 34.0.0, NDK 23.2.8568313,
Python 3.9+ and Git. `prepare.py` actually requires Python 3.9+ (`is_relative_to`).

```sh
git clone https://github.com/nyteghost/vibelight.git
cd vibelight
git switch beta/next-features-20261028
git clone https://github.com/moonlight-stream/moonlight-android.git ../moonlight-android
# Exact dependency commit; never build from an unpinned moving branch.
git -C ../moonlight-android checkout 1b1b100e63e8ffcf1496e324ff7b57ee9f2db2a9
git -C ../moonlight-android submodule update --init --recursive
python3 android-tv/prepare.py --upstream ../moonlight-android --output ../vibelight-tv-build --version-code 1
python3 android-tv/tests/test_prepare.py ../vibelight-tv-build
cd ../vibelight-tv-build
./gradlew assembleNonRootDebug lintNonRootDebug
```

Version codes must be higher than your installed build to upgrade. CI allocates
`10000 + workflow run number × 100 + attempt` and embeds the source revision in
the version name. Use a new run for a newer build; rerunning an old run does not
supersede later version codes. Android ID: `io.github.nyteghost.vibelight.tv.beta`.

`upstream.json` is the dependency lock. `prepare.py` verifies its commits, copies
matching sources (including native dependencies), overlays TV Java code, and makes
small checked modifications. Upstream files and licenses remain in the generated
source bundle. The companion APK artifact includes matching source, SHA256 sums,
build provenance, package metadata and the signing certificate fingerprint.

## Tests

```sh
mkdir -p /tmp/vibe-tv-tests
javac -d /tmp/vibe-tv-tests android-tv/overlay/java/com/limelight/tv/TvPolicy.java android-tv/tests/PolicyTest.java
java -cp /tmp/vibe-tv-tests PolicyTest
```

CI additionally compiles both Shield arm64 and emulator x86 APKs, runs Android lint,
checks packaging boundaries and post-pair navigation callbacks, and installs/launches the x86 APK on an Android TV
emulator for D-pad/Back navigation. These checks do not verify Shield MediaCodec,
a real Vibebeam pairing, audio/input transport, HDR or streaming performance.

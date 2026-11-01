#!/usr/bin/env python3
"""Prepare an isolated, pinned Android source tree. Never alters desktop sources."""
import argparse, json, os, pathlib, re, shutil, subprocess, xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parent
LOCK = json.loads((ROOT / 'upstream.json').read_text())

def replace_once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f'Pinned source mismatch: {path.name}: {old[:75]!r}')
    path.write_text(text.replace(old, new))

def prepare(upstream, output, number):
    if not 1 <= number <= 2000000000:
        raise ValueError('versionCode out of range')
    actual = subprocess.check_output(['git', '-C', str(upstream), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != LOCK['commit']:
        raise ValueError('Upstream HEAD does not match upstream.json')
    common = upstream / 'app/src/main/jni/moonlight-core/moonlight-common-c'
    common_sha = subprocess.check_output(['git','-C',str(common),'rev-parse','HEAD'],text=True).strip()
    if common_sha != LOCK['common_c_commit'] or not (common/'enet/include/enet/enet.h').is_file():
        raise ValueError('Initialize the pinned recursive native submodules first')
    if output.exists():
        raise ValueError('Output must be a new directory; refusing to overwrite')
    if output.resolve().is_relative_to(upstream.resolve()):
        raise ValueError('Output must be outside upstream')
    shutil.copytree(upstream, output, ignore=shutil.ignore_patterns('.git','.gradle','.cxx','build','local.properties'))
    for source in (ROOT / 'overlay').rglob('*'):
        if source.is_file():
            destination = output / 'app/src/main' / source.relative_to(ROOT / 'overlay')
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
    sha = os.environ.get('GITHUB_SHA', 'local')[:8]
    version = f'0.1.0-beta.{number}+{sha}'
    gradle = output / 'app/build.gradle'
    replace_once(gradle, 'minSdk 16', 'minSdk 26')
    replace_once(gradle, 'versionName "12.1"', f'versionName "{version}"')
    replace_once(gradle, 'versionCode = 314', f'versionCode = {number}')
    replace_once(gradle, 'applicationId "com.limelight"', 'applicationId "io.github.nyteghost.vibelight.tv"')
    replace_once(gradle, 'applicationIdSuffix ".debug"', 'applicationIdSuffix ".beta"')
    replace_once(gradle, '"Moonlight (Debug)"', '"Vibelight TV Beta"')
    # Shield Pro 2019 runs arm64; x86 is built separately for emulator smoke tests.
    abi = os.environ.get('VIBELIGHT_TV_ABI', 'arm64-v8a')
    if abi not in ('arm64-v8a', 'x86'):
        raise ValueError('Unexpected ABI')
    replace_once(gradle, 'minSdk 26', f'minSdk 26\n        ndk {{ abiFilters "{abi}" }}')
    manifest = output / 'app/src/main/AndroidManifest.xml'
    ns = 'http://schemas.android.com/apk/res/android'; ET.register_namespace('android', ns)
    tree = ET.parse(manifest); root = tree.getroot(); application = root.find('application')
    application.set(f'{{{ns}}}allowBackup', 'false')
    for feature in root.findall('uses-feature'):
        if feature.get(f'{{{ns}}}name') == 'android.software.leanback': feature.set(f'{{{ns}}}required','true')
    pc = next(a for a in application.findall('activity') if a.get(f'{{{ns}}}name') == '.PcView')
    for intent in list(pc.findall('intent-filter')): pc.remove(intent)
    activity = ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.TvHomeActivity', f'{{{ns}}}exported':'true', f'{{{ns}}}screenOrientation':'landscape'})
    intent = ET.SubElement(activity,'intent-filter')
    ET.SubElement(intent,'action',{f'{{{ns}}}name':'android.intent.action.MAIN'})
    for category in ['android.intent.category.LAUNCHER','android.intent.category.LEANBACK_LAUNCHER']:
        ET.SubElement(intent,'category',{f'{{{ns}}}name':category})
    tree.write(manifest,encoding='utf-8',xml_declaration=True)
    # Keep the upstream stream surface/theme and input lifecycle intact.
    replace_once(output/'app/src/main/java/com/limelight/Game.java',
                 'super.onCreate(savedInstanceState);',
                 'super.onCreate(savedInstanceState);\n        com.limelight.tv.TvTelemetry.reset();')
    replace_once(output/'app/src/main/java/com/limelight/nvstream/http/NvHTTP.java', 'private static boolean verbose = BuildConfig.DEBUG;', 'private static boolean verbose = false;')
    decoder = output/'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java'
    replace_once(decoder, 'long rttInfo = MoonBridge.getEstimatedRttInfo();',
                 'long rttInfo = MoonBridge.getEstimatedRttInfo();\n                com.limelight.tv.TvTelemetry.sample(context, prefs.fps, (double)lastTwo.framesLost / lastTwo.totalFrames * 100, decodeTimeMs);')
    # Visual identity on inherited pairing/settings/library pages; stream surface stays transparent.
    styles = output / 'app/src/main/res/values-v21/styles.xml'
    replace_once(styles,'<style name="AppBaseTheme" parent="android:Theme.Material">', '''<style name="AppBaseTheme" parent="android:Theme.Material">
        <item name="android:colorAccent">#818CF8</item>
        <item name="android:windowBackground">#060918</item>
        <item name="android:textColorPrimary">#E2E8F0</item>
        <item name="android:textColorSecondary">#94A3B8</item>''')
    resources = output / 'app/src/main/res/drawable'
    (resources/'vibelight_banner.xml').write_text('''<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><shape><solid android:color="#060918"/><size android:width="320dp" android:height="180dp"/></shape></item><item android:width="100dp" android:height="100dp" android:gravity="center"><shape android:shape="oval"><solid android:color="#818CF8"/></shape></item><item android:width="76dp" android:height="76dp" android:gravity="center" android:left="30dp"><shape android:shape="oval"><solid android:color="#060918"/></shape></item></layer-list>''')
    # Launcher expects a 320x180 bitmap banner: generated from repo SVG-inspired geometry via stdlib.
    import struct, zlib
    def png(width,height):
        rows=[]
        for y in range(height):
            row=bytearray()
            for x in range(width):
                moon=(x-160)**2+(y-84)**2<44**2 and (x-177)**2+(y-73)**2>=36**2
                row.extend((165,180,252) if moon else (6,9,24))
            rows.append(b'\0'+row)
        def chunk(t,d):return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
        return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
    (resources/'vibelight_tv_banner.png').write_bytes(png(320,180))
    application.set(f'{{{ns}}}banner','@drawable/vibelight_tv_banner')
    application.set(f'{{{ns}}}icon','@drawable/vibelight_tv_banner')
    tree.write(manifest,encoding='utf-8',xml_declaration=True)
    (output/'vibelight-build.json').write_text(json.dumps({'version':version,'versionCode':number,'applicationId':'io.github.nyteghost.vibelight.tv.beta','abi':abi,'upstream':LOCK},indent=2)+'\n')
    print(f'Prepared {version} ({abi}) in {output}')

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--upstream',type=pathlib.Path,required=True);parser.add_argument('--output',type=pathlib.Path,required=True);parser.add_argument('--version-code',type=int,required=True)
    args=parser.parse_args();prepare(args.upstream,args.output,args.version_code)

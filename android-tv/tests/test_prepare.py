#!/usr/bin/env python3
"""Assert packaging/security boundaries before compiling the generated Android tree."""
import json, pathlib, sys, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1]);ns={'a':'http://schemas.android.com/apk/res/android'};a='{'+ns['a']+'}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot();app=manifest.find('application')
assert app.get(a+'allowBackup')=='false'
launchers=[activity for activity in app.findall('activity') if any(c.get(a+'name')=='android.intent.category.LEANBACK_LAUNCHER' for c in activity.findall('intent-filter/category'))]
assert len(launchers)==1 and launchers[0].get(a+'name')=='.tv.TvHomeActivity'
assert any(x.get(a+'name')=='android.hardware.touchscreen' and x.get(a+'required')=='false' for x in manifest.findall('uses-feature'))
assert any(x.get(a+'name')=='android.software.leanback' and x.get(a+'required')=='true' for x in manifest.findall('uses-feature'))
assert app.find("activity[@a:name='.Game']",ns) is not None
assert app.find("activity[@a:name='.PcView']",ns) is not None
assert app.find("activity[@a:name='.preferences.AddComputerManually']",ns) is not None
metadata=json.loads((root/'vibelight-build.json').read_text())
assert metadata['applicationId']=='io.github.nyteghost.vibelight.tv.beta'
assert metadata['abi'] in ['arm64-v8a','x86']
assert metadata['versionCode']>0
assert metadata['sourceRevision']
assert 'VIBELIGHT_TV_TEST_KEYSTORE' in (root/'app/build.gradle').read_text()
assert 'private static boolean verbose = false;' in (root/'app/src/main/java/com/limelight/nvstream/http/NvHTTP.java').read_text()
assert (root/'app/src/main/jni/moonlight-core/moonlight-common-c/enet/include/enet/enet.h').is_file()
assert (root/'app/src/main/res/drawable/vibelight_tv_banner.png').read_bytes().startswith(b'\x89PNG')
assert (root/'LICENSE.txt').exists() or (root/'LICENSE').exists()
print('Android source/package boundary checks passed')

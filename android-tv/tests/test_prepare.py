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
pairing=app.find("activity[@a:name='.tv.TvPairingActivity']",ns)
assert pairing is not None and pairing.get(a+'exported')=='false'
assert (root/'app/src/debug/java/com/limelight/tv/TvNavigationProbe.java').is_file()
assert not (root/'app/src/main/java/com/limelight/tv/TvNavigationProbe.java').exists()
assert 'returnToDashboard(computer.uuid)' in (root/'app/src/main/java/com/limelight/PcView.java').read_text()
assert app.find("activity[@a:name='.preferences.AddComputerManually']",ns) is not None
metadata=json.loads((root/'vibelight-build.json').read_text())
assert metadata['applicationId']=='io.github.nyteghost.vibelight.tv.beta'
assert metadata['abi'] in ['arm64-v8a','x86']
assert metadata['versionCode']>0
assert metadata['sourceRevision']
assert any(x.get(a+'name')=='android.permission.REQUEST_INSTALL_PACKAGES' for x in manifest.findall('uses-permission'))
provider=app.find("provider[@a:name='.tv.TvUpdateFileProvider']",ns)
assert provider is not None and provider.get(a+'exported')=='false' and provider.get(a+'grantUriPermissions')=='true'
paths=ET.parse(root/'app/src/main/res/xml/tv_update_paths.xml').getroot()
assert len(paths)==1 and paths[0].tag=='cache-path' and paths[0].get('path')=='tv-updates/'
assert app.find("activity[@a:name='.tv.TvUpdateActivity']",ns).get(a+'exported')=='false'
assert 'VIBELIGHT_TV_TEST_KEYSTORE' in (root/'app/build.gradle').read_text()
assert 'private static boolean verbose = false;' in (root/'app/src/main/java/com/limelight/nvstream/http/NvHTTP.java').read_text()
assert (root/'app/src/main/jni/moonlight-core/moonlight-common-c/enet/include/enet/enet.h').is_file()
assert (root/'app/src/main/res/drawable/vibelight_tv_banner.png').read_bytes().startswith(b'\x89PNG')
assert (root/'LICENSE.txt').exists() or (root/'LICENSE').exists()
print('Android source/package boundary checks passed')

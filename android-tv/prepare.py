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

CHANNELS = {'beta': ('.beta', 'Vibelight TV Beta'), 'dev': ('.dev', 'Vibelight TV Dev')}

def prepare(upstream, output, number, channel='beta', compose=False):
    if not 1 <= number <= 2000000000:
        raise ValueError('versionCode out of range')
    if channel not in CHANNELS:
        raise ValueError('Unknown channel')
    suffix, label = CHANNELS[channel]
    application_id = 'io.github.nyteghost.vibelight.tv' + suffix
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
    version = f'0.3.0-{channel}.{number}+{sha}'
    gradle = output / 'app/build.gradle'
    replace_once(gradle, 'android {', '''android {
    signingConfigs {
        debug {
            storeFile file(System.getenv("VIBELIGHT_TV_TEST_KEYSTORE") ?: "${System.properties['user.home']}/.android/debug.keystore")
            storePassword System.getenv("VIBELIGHT_TV_KEYSTORE_PASSWORD") ?: "android"
            keyAlias System.getenv("VIBELIGHT_TV_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword System.getenv("VIBELIGHT_TV_KEYSTORE_PASSWORD") ?: "android"
        }
    }''')
    replace_once(gradle, 'minSdk 16', 'minSdk 26')
    replace_once(gradle, 'versionName "12.1"', f'versionName "{version}"')
    replace_once(gradle, 'versionCode = 314', f'versionCode = {number}')
    replace_once(gradle, 'applicationId "com.limelight"', 'applicationId "io.github.nyteghost.vibelight.tv"')
    replace_once(gradle, 'applicationIdSuffix ".debug"', f'applicationIdSuffix "{suffix}"')
    replace_once(gradle, '"Moonlight (Debug)"', f'"{label}"')
    # Shield Pro 2019 runs arm64; x86 is built separately for emulator smoke tests.
    abi = os.environ.get('VIBELIGHT_TV_ABI', 'arm64-v8a')
    if abi not in ('arm64-v8a', 'x86'):
        raise ValueError('Unexpected ABI')
    replace_once(gradle, 'minSdk 26', f'minSdk 26\n        ndk {{ abiFilters "{abi}" }}')
    replace_once(gradle, 'dependencies {', "dependencies {\n    implementation 'androidx.core:core:1.12.0'")
    with (output/'gradle.properties').open('a') as f:f.write('\nandroid.useAndroidX=true\n')
    manifest = output / 'app/src/main/AndroidManifest.xml'
    ns = 'http://schemas.android.com/apk/res/android'; ET.register_namespace('android', ns)
    tree = ET.parse(manifest); root = tree.getroot(); application = root.find('application')
    application.set(f'{{{ns}}}allowBackup', 'false')
    for feature in root.findall('uses-feature'):
        if feature.get(f'{{{ns}}}name') == 'android.software.leanback': feature.set(f'{{{ns}}}required','true')
    ET.SubElement(root,'uses-permission',{f'{{{ns}}}name':'android.permission.REQUEST_INSTALL_PACKAGES'})
    ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.TvUpdateActivity',f'{{{ns}}}exported':'false',f'{{{ns}}}screenOrientation':'landscape'})
    provider=ET.SubElement(application,'provider',{f'{{{ns}}}name':'.tv.TvUpdateFileProvider',f'{{{ns}}}authorities':'${applicationId}.tv-updates',f'{{{ns}}}exported':'false',f'{{{ns}}}grantUriPermissions':'true'})
    ET.SubElement(provider,'meta-data',{f'{{{ns}}}name':'android.support.FILE_PROVIDER_PATHS',f'{{{ns}}}resource':'@xml/tv_update_paths'})
    pc = next(a for a in application.findall('activity') if a.get(f'{{{ns}}}name') == '.PcView')
    for intent in list(pc.findall('intent-filter')): pc.remove(intent)
    # The modern PC picker is the launcher. The previous dashboard stays as "Classic"
    # (reachable from the picker and Settings) for recovery until physical acceptance.
    activity = ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.TvLauncherActivity', f'{{{ns}}}exported':'true', f'{{{ns}}}screenOrientation':'landscape', f'{{{ns}}}launchMode':'singleTop'})
    ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.TvHomeActivity', f'{{{ns}}}exported':'true', f'{{{ns}}}screenOrientation':'landscape'})
    ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.TvPairingActivity', f'{{{ns}}}exported':'false', f'{{{ns}}}screenOrientation':'landscape'})
    for modern in ('TvLibraryActivity','TvPairActivity','TvSettingsActivity','TvAddPcActivity'):
        ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.'+modern, f'{{{ns}}}exported':'false', f'{{{ns}}}screenOrientation':'landscape', f'{{{ns}}}windowSoftInputMode':'adjustPan'})
    intent = ET.SubElement(activity,'intent-filter')
    ET.SubElement(intent,'action',{f'{{{ns}}}name':'android.intent.action.MAIN'})
    for category in ['android.intent.category.LAUNCHER','android.intent.category.LEANBACK_LAUNCHER']:
        ET.SubElement(intent,'category',{f'{{{ns}}}name':category})
    tree.write(manifest,encoding='utf-8',xml_declaration=True)
    # Only the TV wrapper returns selections/success to its parent. Ordinary core PC UI is unchanged.
    replace_once(output/'app/src/main/java/com/limelight/PcView.java',
                 '        Intent i = new Intent(this, AppView.class);',
                 '''        if (this instanceof com.limelight.tv.TvPairingActivity) {
            ((com.limelight.tv.TvPairingActivity)this).returnToDashboard(computer.uuid);
            return;
        }
        Intent i = new Intent(this, AppView.class);''')
    if compose:
        # Feasibility spike: Kotlin + Compose for TV pinned to versions that support the
        # pinned AGP 8.2.2 / compileSdk 34 toolchain. Only the spike activity uses it.
        replace_once(output/'build.gradle', "classpath 'com.android.tools.build:gradle:8.2.2'",
                     "classpath 'com.android.tools.build:gradle:8.2.2'\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25'")
        replace_once(gradle, "apply plugin: 'com.android.application'", "apply plugin: 'com.android.application'\napply plugin: 'org.jetbrains.kotlin.android'")
        replace_once(gradle, 'buildConfig = true', 'buildConfig = true\n        compose = true')
        replace_once(gradle, '''        targetCompatibility JavaVersion.VERSION_11
    }''', '''        targetCompatibility JavaVersion.VERSION_11
    }
    composeOptions { kotlinCompilerExtensionVersion '1.5.15' }
    kotlinOptions { jvmTarget = '11' }''')
        replace_once(gradle, "implementation 'androidx.core:core:1.12.0'", '''implementation 'androidx.core:core:1.12.0'
    implementation platform('androidx.compose:compose-bom:2024.09.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.foundation:foundation'
    implementation 'androidx.activity:activity-compose:1.9.2'
    implementation 'androidx.tv:tv-material:1.0.0'\n''')
        for source in (ROOT / 'overlay-compose').rglob('*'):
            if source.is_file():
                destination = output / 'app/src/main' / source.relative_to(ROOT / 'overlay-compose')
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
        spike = ET.SubElement(application,'activity',{f'{{{ns}}}name':'.tv.compose.TvComposeSpikeActivity', f'{{{ns}}}exported':'true', f'{{{ns}}}screenOrientation':'landscape', f'{{{ns}}}theme':'@android:style/Theme.Material.NoActionBar.Fullscreen'})
        tree.write(manifest,encoding='utf-8',xml_declaration=True)
    # Debug-only navigation instrumentation; no simulated PIN/crypto or host networking.
    shutil.copytree(ROOT/'test-support',output/'app/src/debug',dirs_exist_ok=True)
    # Keep the upstream stream surface/theme and input lifecycle intact.
    replace_once(output/'app/src/main/java/com/limelight/Game.java',
                 'super.onCreate(savedInstanceState);',
                 'super.onCreate(savedInstanceState);\n        com.limelight.tv.TvTelemetry.reset();\n        com.limelight.tv.TvSessionState.enter();')
    replace_once(output/'app/src/main/java/com/limelight/Game.java', 'super.onDestroy();', 'super.onDestroy();\n        com.limelight.tv.TvSessionState.leave();\n        com.limelight.tv.TvStreamUi.detach(this);')
    # Modern connection screen and in-stream panel (Views only). Hooks are anchored on
    # exact upstream text so a moved anchor fails the build instead of drifting.
    game = output/'app/src/main/java/com/limelight/Game.java'
    replace_once(game, '''        spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.conn_establishing_msg), true);''',
                 '''        com.limelight.tv.TvStreamUi.attach(this);''')
    replace_once(game, '''    public void stageStarting(final String stage) {''',
                 '''    public void stageStarting(final String stage) {
        com.limelight.tv.TvStreamUi.stageStarting(this, stage);''')
    replace_once(game, '''    public void stageComplete(String stage) {''',
                 '''    public void stageComplete(String stage) {
        com.limelight.tv.TvStreamUi.stageComplete(this, stage);''')
    replace_once(game, '''    public void connectionStarted() {''',
                 '''    public void connectionStarted() {
        com.limelight.tv.TvStreamUi.connectionStarted(this);''')
    replace_once(game, '''                    Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_error_title), dialogText, true);''',
                 '''                    if (!com.limelight.tv.TvStreamUi.failed(Game.this, getResources().getString(R.string.conn_error_title), dialogText, stage, errorCode))
                        Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_error_title), dialogText, true);''')
    replace_once(game, '''                        Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_terminated_title),
                                message, true);''',
                 '''                        if (!com.limelight.tv.TvStreamUi.failed(Game.this, getResources().getString(R.string.conn_terminated_title), message, null, errorCode))
                            Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_terminated_title),
                                message, true);''')
    replace_once(game, '''    public boolean handleKeyDown(KeyEvent event) {''',
                 '''    public boolean handleKeyDown(KeyEvent event) {
        if (com.limelight.tv.TvStreamUi.onKey(this, event)) {
            return true;
        }''')
    replace_once(game, '''    public boolean handleKeyUp(KeyEvent event) {''',
                 '''    public boolean handleKeyUp(KeyEvent event) {
        if (com.limelight.tv.TvStreamUi.onKey(this, event)) {
            return true;
        }''')
    replace_once(game, '''    private boolean handleMotionEvent(View view, MotionEvent event) {''',
                 '''    private boolean handleMotionEvent(View view, MotionEvent event) {
        if (com.limelight.tv.TvStreamUi.onMotion(this, event)) {
            return true;
        }''')
    replace_once(game, '''    @Override
    public void onPerfUpdate(final String text) {''',
                 '''    /** Vibelight in-stream panel: live statistics overlay toggle. */
    public void vibeSetPerfOverlay(boolean enabled) {
        prefConfig.enablePerfOverlay = enabled;
        performanceOverlayView.setVisibility(enabled && !isHidingOverlays ? View.VISIBLE : View.GONE);
    }

    /** Vibelight in-stream panel: release every forwarded controller and mouse button state. */
    public void vibeReleaseInput() {
        if (controllerHandler != null) {
            controllerHandler.vibeReleaseAll();
        }
        if (conn != null && connected) {
            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
        }
    }

    public View vibeStreamView() {
        return streamView;
    }

    @Override
    public void onPerfUpdate(final String text) {''')
    controller = output/'app/src/main/java/com/limelight/binding/input/ControllerHandler.java'
    replace_once(controller, '''            // All buttons from the quit combo are lifted. Finish the activity now.
            activityContext.finish();''',
                 '''            // All buttons from the quit combo are lifted. Vibelight opens its panel (which
            // offers Disconnect) unless the user turned that off; otherwise finish as before.
            context.pendingExit = false;
            if (!com.limelight.tv.TvStreamUi.onQuitCombo(activityContext)) {
                activityContext.finish();
            }''')
    replace_once(controller, '''    private void sendControllerInputPacket(GenericControllerContext originalContext) {''',
                 '''    private void sendControllerInputPacket(GenericControllerContext originalContext) {
        if (com.limelight.tv.TvStreamUi.inputBlocked()) {
            return;
        }''')
    replace_once(controller, '''    public void destroy() {
        if (!stopped) {''',
                 '''    /** Vibelight: send a released state for every controller before the panel takes input. */
    public void vibeReleaseAll() {
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            vibeRelease(inputDeviceContexts.valueAt(i));
        }
        for (int i = 0; i < usbDeviceContexts.size(); i++) {
            vibeRelease(usbDeviceContexts.valueAt(i));
        }
    }

    private void vibeRelease(GenericControllerContext context) {
        if (!context.assignedControllerNumber) {
            return;
        }
        context.inputMap = 0;
        context.leftTrigger = context.rightTrigger = 0;
        context.leftStickX = context.leftStickY = context.rightStickX = context.rightStickY = 0;
        sendControllerInputPacket(context);
    }

    public void destroy() {
        if (!stopped) {''')
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
    # Launcher expects a 320x180 bitmap banner: the Vibelight triangle mark, generated with the stdlib.
    import struct, zlib
    def png(width,height):
        # Vibelight triangular play mark on deep navy, 4x supersampled for smooth edges.
        def inside(px,py,cx,cy,size):
            ax,ay=cx-size*0.37,cy-size*0.42; bx,by=cx+size*0.42,cy; qx,qy=cx-size*0.37,cy+size*0.42
            def side(x1,y1,x2,y2):return (x2-x1)*(py-y1)-(y2-y1)*(px-x1)
            d1,d2,d3=side(ax,ay,bx,by),side(bx,by,qx,qy),side(qx,qy,ax,ay)
            return (d1>=0 and d2>=0 and d3>=0) or (d1<=0 and d2<=0 and d3<=0)
        bg=(6,9,24);c1=(129,140,248);c2=(165,180,252)
        cx,cy,size=width/2,height/2,min(width,height)*0.62
        rows=[]
        for y in range(height):
            row=bytearray()
            for x in range(width):
                hits=0
                for sy in range(4):
                    for sx in range(4):
                        if inside(x+(sx+.5)/4,y+(sy+.5)/4,cx,cy,size):hits+=1
                t=max(0.0,min(1.0,((x-cx)/size+(y-cy)/size)/2+.5));tri=[c1[i]+(c2[i]-c1[i])*t for i in range(3)]
                a=hits/16
                row.extend(int(bg[i]*(1-a)+tri[i]*a) for i in range(3))
            rows.append(b'\0'+row)
        def chunk(t,d):return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
        return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
    (resources/'vibelight_tv_banner.png').write_bytes(png(320,180))
    application.set(f'{{{ns}}}banner','@drawable/vibelight_tv_banner')
    application.set(f'{{{ns}}}icon','@drawable/vibelight_tv_banner')
    tree.write(manifest,encoding='utf-8',xml_declaration=True)
    (output/'vibelight-build.json').write_text(json.dumps({'sourceRevision':os.environ.get('GITHUB_SHA','local'),'version':version,'versionCode':number,'applicationId':application_id,'channel':channel,'compose':compose,'abi':abi,'upstream':LOCK},indent=2)+'\n')
    print(f'Prepared {version} ({abi}) in {output}')

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--upstream',type=pathlib.Path,required=True);parser.add_argument('--output',type=pathlib.Path,required=True);parser.add_argument('--version-code',type=int,required=True)
    parser.add_argument('--channel',choices=sorted(CHANNELS),default=os.environ.get('VIBELIGHT_TV_CHANNEL','beta'))
    parser.add_argument('--compose',action='store_true',default=os.environ.get('VIBELIGHT_TV_COMPOSE')=='1')
    args=parser.parse_args();prepare(args.upstream,args.output,args.version_code,args.channel,args.compose)

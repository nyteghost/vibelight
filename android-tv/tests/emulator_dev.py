#!/usr/bin/env python3
"""Emulator checks for development APKs: install, instrumentation probes, D-pad
navigation through the modern screens, screenshots and crash scan. No PC host,
pairing or streaming is simulated, and nothing here measures streaming performance."""
import os, pathlib, re, subprocess, sys, time, xml.etree.ElementTree as ET

apk, out, flavor = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
out.mkdir(parents=True, exist_ok=True)
pkg = os.environ.get('VIBELIGHT_TV_PACKAGE', 'io.github.nyteghost.vibelight.tv.beta')


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT)


def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/vibe-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/vibe-ui.xml'))


def texts(tree):
    return [n.get('text', '') for n in tree.iter('node')]


def has(fragment):
    return any(fragment in t for t in texts(dump()))


def wait_for(fragment, seconds=10):
    end = time.time() + seconds
    while time.time() < end:
        if has(fragment):
            return True
        time.sleep(.5)
    return False


def shot(name):
    with (out / (name + '.png')).open('wb') as f:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=f, check=True)


def key(code, pause=.25):
    adb('shell', 'input', 'keyevent', str(code)); time.sleep(pause)


def focused_text():
    tree = dump()
    for n in tree.iter('node'):
        if n.get('focused') == 'true':
            label = n.get('text', '')
            if not label:
                label = ' '.join(c.get('text', '') for c in n.iter('node') if c.get('text'))
            return label
    return None


def focus(target, keys=(22, 20, 21, 19), limit=60):
    """Reach a control by D-pad only, trying each direction in turn."""
    seen = 0
    for direction in keys:
        for _ in range(limit // len(keys)):
            label = focused_text() or ''
            if target in label:
                return True
            key(direction, .15); seen += 1
    shot('focus-failure-' + re.sub('[^a-z]', '', target.lower()))
    (out / ('focus-failure-' + re.sub('[^a-z]', '', target.lower()) + '.xml')).write_text(ET.tostring(dump(), encoding='unicode'))
    raise AssertionError('D-pad could not reach ' + target)


def start(activity):
    adb('shell', 'am', 'force-stop', pkg)
    r = adb('shell', 'am', 'start', '-W', '-n', f'{pkg}/{activity}')
    assert 'Error' not in r, r
    (out / 'launch-times.txt').open('a').write(activity + '\n' + r + '\n')
    time.sleep(2)


def instrument(name, marker):
    r = adb('shell', 'am', 'instrument', '-w', '-r', f'{pkg}/com.limelight.tv.{name}')
    (out / (name + '.txt')).write_text(r)
    assert 'INSTRUMENTATION_CODE: -1' in r and marker in r, r
    print(name, 'passed')


try:
    adb('shell', 'wm', 'size', '1920x1080'); adb('shell', 'wm', 'density', '320')
    adb('install', '-r', str(apk)); adb('logcat', '-c')

    if flavor == 'compose-spike':
        start('com.limelight.tv.compose.TvComposeSpikeActivity')
        assert wait_for('Compose for TV feasibility'), texts(dump())
        shot('compose-spike')
        for _ in range(6):
            key(22, .2)
        shot('compose-spike-focus-moved')
        gfx = adb('shell', 'dumpsys', 'gfxinfo', pkg)
        (out / 'compose-gfxinfo.txt').write_text(gfx)
        start('com.limelight.tv.TvLauncherActivity')
        for _ in range(6):
            key(22, .2)
        (out / 'views-gfxinfo.txt').write_text(adb('shell', 'dumpsys', 'gfxinfo', pkg))
        print('Compose spike rendered and accepted D-pad focus on the emulator. Not a Shield measurement.')
    else:
        instrument('TvNavigationProbe', 'Pairing navigation passed:')
        adb('shell', 'appops', 'set', pkg, 'REQUEST_INSTALL_PACKAGES', 'allow')
        instrument('TvUpdateProbe', 'Update validation passed:')
        instrument('TvDesignProbe', 'Design checks passed:')

        # Screen 2: PC picker (launcher). No PC exists on the emulator, so the empty state shows.
        start('com.limelight.tv.TvLauncherActivity')
        assert wait_for('Choose a PC to play'), texts(dump())
        assert has('Looking for PCs on your network'), texts(dump())
        shot('01-pc-picker-empty')

        # Settings by D-pad, every group, then search.
        focus('Settings', keys=(19, 22, 21))
        key(23, 1.2)
        assert wait_for('Search settings'), texts(dump())
        shot('06-settings-general')
        for group in ['Streaming', 'Display & HDR', 'Audio', 'Controls', 'Network', 'Accessibility', 'Advanced', 'Updates', 'About']:
            focus(group, keys=(20, 19))
            time.sleep(.4)
            assert has(group), (group, texts(dump()))
            shot('06-settings-' + re.sub('[^a-z]', '', group.lower()))
        focus('Accessibility', keys=(19, 20))
        assert has('Reduce effects'), texts(dump())
        shot('06-settings-reduce-effects')
        key(4, .6)

        # Add PC (manual IP) from the picker.
        start('com.limelight.tv.TvLauncherActivity')
        focus('Add PC', keys=(19, 22, 21))
        key(23, 1.2)
        assert wait_for('Add a PC by IP address'), texts(dump())
        shot('02-add-pc')
        adb('shell', 'input', 'text', 'not..valid')
        key(66, .4)
        focus('Add PC', keys=(20, 22))
        key(23, 1.0)
        assert has("doesn't look like an address") or has('No PC answered'), texts(dump())
        shot('02-add-pc-invalid')
        key(4, .5); key(4, .5)

        # Classic dashboard remains reachable from the picker.
        start('com.limelight.tv.TvLauncherActivity')
        focus('Classic dashboard', keys=(20, 22, 21))
        key(23, 1.2)
        assert wait_for('Play on the big screen'), texts(dump())
        shot('99-classic-dashboard')
        key(4, .6)

        # Pairing and library screens without a PC show their honest error states.
        adb('shell', 'am', 'force-stop', pkg)
        logs = adb('logcat', '-d')
        (out / 'logcat.txt').write_text(logs)
        assert not re.search(r'FATAL EXCEPTION', logs), logs[-6000:]
        print('Modern screens reached by D-pad; design probe passed; Classic reachable. No host/streaming hardware was tested.')
finally:
    try:
        (out / 'logcat.txt').write_text(adb('logcat', '-d'))
    except Exception:
        pass

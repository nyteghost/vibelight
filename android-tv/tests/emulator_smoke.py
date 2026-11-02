#!/usr/bin/env python3
"""Real APK/emulator smoke checks. No host simulation and no streaming-performance claims."""
import pathlib,re,subprocess,sys,time,xml.etree.ElementTree as ET
apk,out=pathlib.Path(sys.argv[1]),pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
pkg='io.github.nyteghost.vibelight.tv.beta'
def adb(*args):return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT)
def dump():
    adb('shell','uiautomator','dump','/sdcard/vibe-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/vibe-ui.xml'))
def labels(tree):return [n.get('text','') for n in tree.iter('node')]
def screenshot(name):
    with (out/(name+'.png')).open('wb') as f: subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
def focus_and_activate(text):
    # Traverse focus only with D-pad. Text identifies the target but is never tapped.
    for _ in range(50):
        tree=dump();focused=next((n for n in tree.iter('node') if n.get('focused')=='true'),None)
        if focused is not None and focused.get('text')==text:
            adb('shell','input','keyevent','23');time.sleep(.5);return
        adb('shell','input','keyevent','20');time.sleep(.1)
    screenshot('focus-failure')
    (out/'focus-failure.xml').write_text(ET.tostring(dump(),encoding='unicode'))
    raise AssertionError('D-pad could not reach '+text)
try:
    adb('shell','wm','size','1920x1080');adb('shell','wm','density','320')
    adb('install','-r',str(apk));adb('logcat','-c')
    navigation=adb('shell','am','instrument','-w','-r',pkg+'/com.limelight.tv.TvNavigationProbe')
    (out/'pairing-navigation.txt').write_text(navigation)
    assert 'INSTRUMENTATION_CODE: -1' in navigation and 'Pairing navigation passed:' in navigation,navigation

    adb('shell','appops','set',pkg,'REQUEST_INSTALL_PACKAGES','allow')
    updates=adb('shell','am','instrument','-w','-r',pkg+'/com.limelight.tv.TvUpdateProbe')
    (out/'update-validation.txt').write_text(updates)
    assert 'INSTRUMENTATION_CODE: -1' in updates and 'Update validation passed:' in updates,updates

    result=adb('shell','am','start','-W','-n',pkg+'/com.limelight.tv.TvHomeActivity')
    assert 'Error' not in result,result
    time.sleep(3)
    assert 'Play on the big screen' in labels(dump())
    screenshot('home')
    for page,expected in [('Profiles','Streaming profiles'),('Performance','Performance & Smart Local'),('Controllers','Controllers & remote'),('Diagnostics','Local device checks'),('Settings','Settings'),('About','Vibelight TV Beta')]:
        # Relaunch to reset focus to the navigation rail, then traverse it by D-pad.
        adb('shell','am','force-stop',pkg);adb('shell','am','start','-W','-n',pkg+'/com.limelight.tv.TvHomeActivity');time.sleep(1)
        focus_and_activate(page)
        assert expected in labels(dump()),(page,labels(dump()))
        screenshot(page.lower())
        adb('shell','input','keyevent','4');time.sleep(.3)
        assert 'Play on the big screen' in labels(dump()),'Back did not return to Home'
    adb('shell','am','force-stop',pkg);adb('shell','am','start','-W','-n',pkg+'/com.limelight.tv.TvHomeActivity');time.sleep(1)
    focus_and_activate('Updates')
    assert 'Beta updates' in labels(dump()) and 'Check for beta updates' in labels(dump())
    screenshot('updates')
    adb('shell','input','keyevent','4');time.sleep(.3)
    assert 'Play on the big screen' in labels(dump())
    logs=adb('logcat','-d');(out/'logcat.txt').write_text(logs)
    assert not re.search(r'FATAL EXCEPTION',logs),logs[-5000:]
    print('APK installed/launched; six TV pages reached by D-pad; Back navigation passed. No streaming hardware was tested.')
finally:
    try:(out/'logcat.txt').write_text(adb('logcat','-d'))
    except Exception:pass

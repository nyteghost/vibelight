#!/usr/bin/env python3
"""Validate a tested arm64 artifact and create the credential-free updater manifest."""
import argparse, hashlib, json, pathlib, re, zipfile

def create(directory, revision):
    meta=json.loads((directory/'vibelight-build.json').read_text())
    if meta['sourceRevision']!=revision or not re.fullmatch('[a-f0-9]{40}',revision):raise ValueError('Source revision mismatch')
    code=meta['versionCode']
    if type(code) is not int or not 1<=code<=2000000000:raise ValueError('Invalid version code')
    package='io.github.nyteghost.vibelight.tv.beta'
    if meta['abi']!='arm64-v8a' or meta['applicationId']!=package or 'beta' not in meta['version']:raise ValueError('Not a TV beta arm64 package')
    apk=directory/'Vibelight-TV-arm64-v8a-beta.apk'
    data=apk.read_bytes();sha=hashlib.sha256(data).hexdigest()
    if not 0<len(data)<=120*1024*1024:raise ValueError('APK size out of range')
    badging=(directory/'apk-badging.txt').read_text()
    if f"name='{package}'" not in badging or f"versionCode='{code}'" not in badging:raise ValueError('APK metadata mismatch')
    sdk=re.search(r"sdkVersion:'([0-9]+)'",badging)
    if not sdk or int(sdk[1])<26:raise ValueError('Minimum SDK missing')
    with zipfile.ZipFile(apk) as z:
        libs=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
        if 'lib/arm64-v8a/libmoonlight-core.so' not in libs or any(not n.startswith('lib/arm64-v8a/') for n in libs):raise ValueError('Wrong native ABI')
    for line in (directory/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split(maxsplit=1);name=name.removeprefix('./')
        if pathlib.PurePath(name).name!=name:raise ValueError('Unsafe checksum filename')
        if hashlib.sha256((directory/name).read_bytes()).hexdigest()!=digest:raise ValueError('Artifact hash mismatch')
    if not (directory/'matching-android-source.tar.gz').is_file():raise ValueError('Matching source missing')
    certificate=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})',(directory/'signing-certificate.txt').read_text())
    if not certificate:raise ValueError('Signing certificate missing')
    manifest={'schema':1,'repository':'nyteghost/vibelight','branch':'beta/next-features-20261028','channel':'beta',
        'packageName':package,'abi':'arm64-v8a','minSdk':int(sdk[1]),'versionCode':code,'versionName':meta['version'],
        'sourceRevision':revision,'size':len(data),'sha256':sha,'signingCertificateSha256':certificate[1]}
    (directory/'vibelight-tv-beta.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (directory/'RELEASE_NOTES.md').write_text(f'''Vibelight TV **{meta['version']}** — Android Shield arm64 beta.

Source: `{revision}` on `beta/next-features-20261028`.
APK compilation, lint, update validation and TV emulator navigation passed before publication.
Physical Shield streaming/HDR/controller/cleanup validation remains separate.

Download the APK directly for first installation, or use **Updates → Check for beta updates** in Vibelight TV 0.2.0 or newer.
Android requires permission and user approval to install. The app verifies package identity, version, SHA256 and the installed signing certificate. Pairing/settings stay in the same application.

Matching source, SHA256 sums, build provenance and signing fingerprint are attached.
This is an unofficial Moonlight-derived, debug-signed testing build. No host update or main-branch promotion is required.
''')
    return 'tv-beta-'+str(code)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('directory',type=pathlib.Path);p.add_argument('revision');args=p.parse_args();print(create(args.directory,args.revision))

#!/usr/bin/env python3
import importlib.util,json,pathlib,tempfile,zipfile,hashlib,unittest
spec=importlib.util.spec_from_file_location('release',pathlib.Path(__file__).parents[1]/'make_release.py');release=importlib.util.module_from_spec(spec);spec.loader.exec_module(release)
class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name);self.sha='a'*40
        self.meta={'sourceRevision':self.sha,'versionCode':10701,'version':'0.2.0-beta.10701+aaaaaaaa','abi':'arm64-v8a','applicationId':'io.github.nyteghost.vibelight.tv.beta'}
        self.write_meta()
        with zipfile.ZipFile(self.root/'Vibelight-TV-arm64-v8a-beta.apk','w') as z:z.writestr('lib/arm64-v8a/libmoonlight-core.so',b'fixture')
        (self.root/'matching-android-source.tar.gz').write_bytes(b'source fixture')
        (self.root/'apk-badging.txt').write_text("package: name='io.github.nyteghost.vibelight.tv.beta' versionCode='10701'\nsdkVersion:'26'\n")
        (self.root/'signing-certificate.txt').write_text('Signer #1 certificate SHA-256 digest: '+'b'*64+'\n')
        self.sums()
    def write_meta(self):(self.root/'vibelight-build.json').write_text(json.dumps(self.meta))
    def sums(self):
        names=['Vibelight-TV-arm64-v8a-beta.apk','matching-android-source.tar.gz']
        (self.root/'SHA256SUMS').write_text(''.join(hashlib.sha256((self.root/n).read_bytes()).hexdigest()+'  ./'+n+'\n' for n in names))
    def test_manifest(self):
        self.assertEqual(release.create(self.root,self.sha),'tv-beta-10701')
        m=json.loads((self.root/'vibelight-tv-beta.json').read_text());self.assertEqual(m['channel'],'beta');self.assertEqual(m['versionCode'],10701)
        self.assertEqual(m['sha256'],hashlib.sha256((self.root/'Vibelight-TV-arm64-v8a-beta.apk').read_bytes()).hexdigest())
    def test_wrong_source(self):
        with self.assertRaises(ValueError):release.create(self.root,'c'*40)
    def test_wrong_package(self):
        self.meta['applicationId']='com.limelight';self.write_meta()
        with self.assertRaises(ValueError):release.create(self.root,self.sha)
    def test_wrong_abi(self):
        self.meta['abi']='x86';self.write_meta()
        with self.assertRaises(ValueError):release.create(self.root,self.sha)
    def test_corrupted_checksum(self):
        (self.root/'matching-android-source.tar.gz').write_bytes(b'changed')
        with self.assertRaises(ValueError):release.create(self.root,self.sha)
    def test_missing_signing_identity(self):
        (self.root/'signing-certificate.txt').write_text('')
        with self.assertRaises(ValueError):release.create(self.root,self.sha)
if __name__=='__main__':unittest.main()

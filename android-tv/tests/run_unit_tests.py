#!/usr/bin/env python3
"""Run every Android-independent Vibelight TV test on a plain JVM (no SDK needed)."""
import pathlib, subprocess, sys, tempfile

root = pathlib.Path(__file__).resolve().parents[1]
overlay = root / 'overlay/java/com/limelight/tv'
tests = root / 'tests'


def run(sources, main, out):
    subprocess.check_call(['javac', '-encoding', 'UTF-8', '-d', out, *map(str, sources)])
    subprocess.check_call(['java', '-cp', out, main])


with tempfile.TemporaryDirectory() as out:
    run([overlay / 'TvPolicy.java', tests / 'PolicyTest.java'], 'PolicyTest', out)
    run([overlay / 'TvUpdatePolicy.java', overlay / 'TvSessionState.java', tests / 'UpdatePolicyTest.java'], 'UpdatePolicyTest', out)
    run([*sorted((overlay / 'logic').glob('*.java')), tests / 'java/LogicTest.java'], 'LogicTest', out)
subprocess.check_call([sys.executable, str(tests / 'test_release.py')])
print('All native-free Vibelight TV tests passed')

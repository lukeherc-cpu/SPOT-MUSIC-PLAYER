"""Securely retain an existing Android key in GitHub Actions secrets; never commit the key."""
import argparse
import base64
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--alias', default='androiddebugkey')
parser.add_argument('--repo', default='lukeherc-cpu/SPOT-MUSIC-PLAYER')
args = parser.parse_args()
gh = os.environ.get('GITHUB_CLI') or shutil.which('gh')
if not gh:
    raise SystemExit('Install GitHub CLI and run gh auth login first.')
if subprocess.run([gh, 'auth', 'status'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
    raise SystemExit('GitHub CLI needs login: run gh auth login. The connected GitHub app cannot write repository secrets.')
keytool = shutil.which('keytool') or str(Path(os.environ.get('JAVA_HOME', '')) / 'bin/keytool')
env = dict(os.environ)
env['SPOT_STORE_PASSWORD'] = os.environ.get('SPOT_STORE_PASSWORD', 'android')
env['SPOT_KEY_PASSWORD'] = os.environ.get('SPOT_KEY_PASSWORD', env['SPOT_STORE_PASSWORD'])
certificate = subprocess.check_output([keytool, '-list', '-v', '-keystore', str(args.keystore), '-alias', args.alias, '-storepass:env', 'SPOT_STORE_PASSWORD'], env=env, text=True)
expected = (Path(__file__).resolve().parents[1] / '.github/android-signing.sha256').read_text().strip()
if expected not in certificate.lower().replace(':', ''):
    raise SystemExit('This key does not match the retained APK identity. No secrets were changed.')
values = {
    'SPOT_ANDROID_KEYSTORE_BASE64': base64.b64encode(args.keystore.read_bytes()),
    'SPOT_ANDROID_KEYSTORE_PASSWORD': env['SPOT_STORE_PASSWORD'].encode(),
    'SPOT_ANDROID_KEY_ALIAS': args.alias.encode(),
    'SPOT_ANDROID_KEY_PASSWORD': env['SPOT_KEY_PASSWORD'].encode(),
}
for name, value in values.items():
    subprocess.run([gh, 'secret', 'set', name, '--repo', args.repo], input=value, check=True)
print('Four signing secrets installed. The private key was never added to repository files or logs.')

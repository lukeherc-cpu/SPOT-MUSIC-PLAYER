"""Fetch verified offline Whisper Tiny assets and build a native desktop recognizer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/speech-bootstrap'
SOURCE_SHA = 'ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720'
MODEL_SHA = '818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7'
MODEL_REVISION = '5359861c739e955e79d9a303bcbc70fb988958b1'
FFMPEG = {
    'linux-x64': 'e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99',
    'darwin-x64': 'ebdddc936f61e14049a2d4b549a412b8a40deeff6540e58a9f2a2da9e6b18894',
    'darwin-arm64': 'a90e3db6a3fd35f6074b013f948b1aa45b31c6375489d39e572bea3f18336584',
    'win32-x64': '04e1307997530f9cf2fe35cba2ca7e8875ca91da02f89d6c7243df819c94ad00',
}
FFMPEG_NOTICES = {
    'linux-x64': ('8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903', '72f4b1b06d419d22ace6e7cc75f06826f90737345aa0b1736158929f4aacc537'),
    'win32-x64': ('8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903', 'a636a7183c58006351acbaf35303c0ed85c6e1320fd4e80de453ba6157de6311'),
    'darwin-x64': ('2e1d16c72fd74e12063776371da757322f8b77589386532f4fd8634bde7de1af', 'e88a0325f8e5b75210355e37341824f074d3cd82def2125be54c914b62848a36'),
    'darwin-arm64': ('cb48bf09a11f5fb576cddb0431c8f5ed0a60157a9ec942adffc13907cbe083f2', '05ba4b92c96605434b1aaae3eedf5a2c280c9607bf78ffca9a5b536d9af2dc6a'),
}

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def download(name, url, expected):
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / name
    if target.is_file() and sha(target) == expected:
        return target
    temporary = target.with_suffix(target.suffix + '.part')
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Cadence-offline-speech-build'})
            with urllib.request.urlopen(request, timeout=180) as response, temporary.open('wb') as output:
                shutil.copyfileobj(response, output)
            if sha(temporary) != expected:
                raise ValueError(f'Checksum mismatch for {name}')
            temporary.replace(target)
            return target
        except Exception:
            temporary.unlink(missing_ok=True)
            if attempt == 2:
                raise

def copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists() or sha(source) != sha(destination):
        shutil.copyfile(source, destination)

def prepare(android=False, desktop=False, target_platform=None, arch=None):
    archive = download('whisper.tar.gz', 'https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.9.5', SOURCE_SHA)
    source = CACHE / 'whisper.cpp-1.9.5'
    if not (source / 'include/whisper.h').is_file():
        with tarfile.open(archive) as bundle:
            bundle.extractall(CACHE, filter='data')
    model = download('ggml-tiny-q5_1.bin', f'https://huggingface.co/ggerganov/whisper.cpp/resolve/{MODEL_REVISION}/ggml-tiny-q5_1.bin', MODEL_SHA)
    if android:
        copy(model, ROOT / 'app/build/generated/speechAssets/models/ggml-tiny-q5_1.bin')
    if not desktop:
        return
    target_platform = target_platform or {'Darwin': 'darwin', 'Windows': 'win32', 'Linux': 'linux'}[platform.system()]
    arch = arch or ('arm64' if platform.machine().lower() in ('arm64', 'aarch64') else 'x64')
    key = f'{target_platform}-{arch}'
    resources = ROOT / 'desktop/resources/speech'
    resources.mkdir(parents=True, exist_ok=True)
    copy(model, resources / model.name)
    ffmpeg = download(f'ffmpeg-{key}', f'https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/ffmpeg-{key}', FFMPEG[key])
    copy(ffmpeg, resources / ('ffmpeg.exe' if target_platform == 'win32' else 'ffmpeg'))
    for extension, expected in zip(('LICENSE', 'README'), FFMPEG_NOTICES[key]):
        notice = download(f'{key}.{extension}', f'https://github.com/eugeneware/ffmpeg-static/releases/download/b6.1.1/{key}.{extension}', expected)
        copy(notice, resources / f'FFmpeg-{extension}.txt')
    cmake = shutil.which('cmake') or str(Path(os.environ.get('ANDROID_HOME', '/var/home/living_room_pc/Android/Sdk')) / 'cmake/3.22.1/bin/cmake')
    build = ROOT / f'build/speech-native-{key}'
    flags = ['-DCMAKE_BUILD_TYPE=Release', '-DBUILD_SHARED_LIBS=OFF', '-DGGML_NATIVE=OFF', '-DGGML_OPENMP=OFF', '-DGGML_METAL=OFF', '-DGGML_BLAS=OFF', '-DGGML_CUDA=OFF', '-DWHISPER_BUILD_TESTS=OFF', '-DWHISPER_BUILD_EXAMPLES=ON', '-DWHISPER_CURL=OFF', '-DGGML_AVX=OFF', '-DGGML_AVX2=OFF', '-DGGML_FMA=OFF', '-DGGML_F16C=OFF']
    if target_platform == 'darwin':
        flags += [f'-DCMAKE_OSX_ARCHITECTURES={"arm64" if arch == "arm64" else "x86_64"}', '-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0']
    if target_platform == 'win32':
        flags += ['-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded$<$<CONFIG:Debug>:Debug>']
    subprocess.run([cmake, '-S', str(source), '-B', str(build), *flags], check=True)
    subprocess.run([cmake, '--build', str(build), '--config', 'Release', '--target', 'whisper-cli', '-j', str(min(4, os.cpu_count() or 2))], check=True)
    executable = 'whisper-cli.exe' if target_platform == 'win32' else 'whisper-cli'
    binary = next(p for p in [build / 'bin/Release' / executable, build / 'bin' / executable] if p.is_file())
    copy(binary, resources / executable)
    for name in ('ffmpeg', 'whisper-cli'):
        if (resources / name).exists():
            (resources / name).chmod(0o755)
    (resources / 'VERSION.json').write_text(json.dumps({'engine': 'whisper.cpp v1.9.5', 'sourceCommit': 'd1be6fde11ac6e0407606b4e42fe72d34add8037', 'model': model.name, 'modelSha256': MODEL_SHA, 'whisperCliSha256': sha(binary), 'ffmpeg': 'b6.1.1', 'ffmpegSha256': sha(ffmpeg), 'platform': key}, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--android', action='store_true')
    parser.add_argument('--desktop', action='store_true')
    parser.add_argument('--platform')
    parser.add_argument('--arch')
    args = parser.parse_args()
    prepare(args.android, args.desktop, args.platform, args.arch)

#!/usr/bin/env bash
# 构建 AzurPilot Android rootfs。目标架构由 AZURPILOT_ABI 决定：
#   arm64-v8a（默认）→ Ubuntu arm64 base，需原生 ARM64 runner（ubuntu-24.04-arm）
#   x86_64           → Ubuntu amd64 base，需原生 x86_64 runner（ubuntu-24.04）
# proot 不做指令翻译，rootfs 与设备 ABI 必须一一对应；CI 按矩阵各出一份并分别发布。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${WORK_DIR:-$REPO_ROOT/.tmp/azurpilot-build}"
ROOTFS_DIR="$WORK_DIR/rootfs"
DIST_DIR="${DIST_DIR:-$REPO_ROOT/dist}"
SOURCE_REPO="${AZURPILOT_REPO:-https://github.com/wess09/AzurPilot.git}"
SOURCE_REF="${AZURPILOT_REF:-1841cb1941751a81ab70668b4d2383c369c4506e}"
TARGET_ABI="${AZURPILOT_ABI:-arm64-v8a}"

case "$TARGET_ABI" in
    arm64-v8a) UBUNTU_ARCH=arm64;  HOST_ARCH=aarch64 ;;
    x86_64)    UBUNTU_ARCH=amd64;  HOST_ARCH=x86_64 ;;
    *) echo "AZURPILOT_ABI 仅支持 arm64-v8a / x86_64，收到：$TARGET_ABI" >&2; exit 1 ;;
esac
BASE_URL="${UBUNTU_BASE:-https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-$UBUNTU_ARCH.tar.gz}"

if [[ $(id -u) -ne 0 ]]; then
    # setup-node/setup-uv 在 runner 的工具目录注入 PATH；sudo 默认 secure_path 会丢掉它们。
    exec sudo -E env "PATH=$PATH" "AZURPILOT_ABI=$TARGET_ABI" bash "$0" "$@"
fi
[[ $(uname -m) == "$HOST_ARCH" ]] || { echo "构建 $TARGET_ABI rootfs 需要原生 $HOST_ARCH runner，当前 $(uname -m)" >&2; exit 1; }
command -v uv >/dev/null && command -v npm >/dev/null || {
    echo '构建环境需要 uv 和 Node.js/npm' >&2; exit 1;
}

mkdir -p "$WORK_DIR" "$DIST_DIR"
BASE_ARCHIVE="$WORK_DIR/ubuntu-base.tar.gz"
if [[ ! -s $BASE_ARCHIVE ]]; then
    curl -fL --retry 3 "$BASE_URL" -o "$BASE_ARCHIVE"
fi
rm -rf -- "${ROOTFS_DIR:?}/"
mkdir -p "$ROOTFS_DIR"
tar -xzf "$BASE_ARCHIVE" -C "$ROOTFS_DIR"

MOUNTS=()
bind_mount() {
    if [[ -d $1 ]]; then mkdir -p "$2"; else mkdir -p "$(dirname "$2")"; touch "$2"; fi
    mount --bind "$1" "$2"
    MOUNTS+=("$2")
}
unmount_all() {
    local i
    for ((i=${#MOUNTS[@]}-1;i>=0;i--)); do umount -lf "${MOUNTS[i]}" || true; done
    MOUNTS=()
}
trap unmount_all EXIT

rm -f "$ROOTFS_DIR/etc/resolv.conf"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$WORK_DIR/resolv.conf"
touch "$ROOTFS_DIR/etc/resolv.conf"
bind_mount "$WORK_DIR/resolv.conf" "$ROOTFS_DIR/etc/resolv.conf"
for d in dev dev/pts proc sys; do bind_mount "/$d" "$ROOTFS_DIR/$d"; done

# uv 的下载缓存外移到宿主：内容寻址，CI 可跨提交复用，省掉每次重下平台 wheel。
# 只能外移 uv-cache（打包前本来就要删）；UV_PYTHON_INSTALL_DIR 要随 rootfs 出厂，动不得。
mkdir -p "$WORK_DIR/uv-cache"
bind_mount "$WORK_DIR/uv-cache" "$ROOTFS_DIR/opt/uv-cache"

guest() {
    chroot "$ROOTFS_DIR" /usr/bin/env -i HOME=/root LANG=C.UTF-8 LC_ALL=C.UTF-8 \
        DEBIAN_FRONTEND=noninteractive GIT_TERMINAL_PROMPT=0 \
        UV_PYTHON_INSTALL_DIR=/opt/uv-python UV_CACHE_DIR=/opt/uv-cache \
        UV_PYTHON_PREFERENCE=only-managed UV_NO_PROGRESS=1 \
        PATH=/usr/local/bin:/usr/bin:/bin "$@"
}

guest apt-get update
guest apt-get install -y --no-install-recommends \
    ca-certificates curl git xz-utils libglib2.0-0t64 libgomp1 libgl1 \
    libstdc++6 libatomic1 libsm6 libxext6 libsndfile1 libvulkan1 python3
cp -L "$(command -v uv)" "$ROOTFS_DIR/usr/local/bin/uv"
guest uv python install 3.14.6

mkdir -p "$ROOTFS_DIR/opt/azurpilot"
git -C "$ROOTFS_DIR/opt/azurpilot" init -q
git -C "$ROOTFS_DIR/opt/azurpilot" remote add origin "$SOURCE_REPO"
git -C "$ROOTFS_DIR/opt/azurpilot" fetch --depth 1 origin "$SOURCE_REF"
git -C "$ROOTFS_DIR/opt/azurpilot" checkout -q --detach FETCH_HEAD
SOURCE_COMMIT="$(git -C "$ROOTFS_DIR/opt/azurpilot" rev-parse HEAD)"
install -m 0644 "$REPO_ROOT/rootfs/seeds/deploy-azurpilot.yaml" \
    "$ROOTFS_DIR/opt/azurpilot/config/deploy.yaml"
install -m 0755 "$REPO_ROOT/rootfs/seeds/seed_azurpilot.py" \
    "$ROOTFS_DIR/opt/azurpilot/seed_azurpilot.py"
install -m 0755 "$REPO_ROOT/rootfs/overlays/android_host.py" \
    "$ROOTFS_DIR/opt/azurpilot/android_host.py"
install -m 0755 "$REPO_ROOT/rootfs/build/azurpilot-ocr-gate.py" \
    "$ROOTFS_DIR/opt/azurpilot/azurpilot-ocr-gate.py"

guest /bin/sh -c 'cd /opt/azurpilot && uv sync --frozen --no-dev --python 3.14.6'
VENV_SITE_PACKAGES="$(find "$ROOTFS_DIR/opt/azurpilot/.venv/lib" -maxdepth 2 -type d -name site-packages -print -quit)"
[[ -n "$VENV_SITE_PACKAGES" ]] || { echo 'AzurPilot venv site-packages missing' >&2; exit 1; }
install -m 0644 "$REPO_ROOT/rootfs/overlays/android_process_compat.py" "$VENV_SITE_PACKAGES/android_process_compat.py"
install -m 0644 "$REPO_ROOT/rootfs/overlays/sitecustomize.py" "$VENV_SITE_PACKAGES/sitecustomize.py"
guest /bin/sh -c 'cd /opt/azurpilot && .venv/bin/python -m module.config.config_updater'

FRONTEND="$ROOTFS_DIR/opt/azurpilot/frontend"
NPM_CONFIG_CACHE="$WORK_DIR/npm-cache" npm ci --prefix "$FRONTEND" --no-audit --no-fund
NPM_CONFIG_CACHE="$WORK_DIR/npm-cache" npm run build --prefix "$FRONTEND"
AZURPILOT_SOURCE="$ROOTFS_DIR/opt/azurpilot" python3 - <<'PY'
import importlib.util, os, pathlib
root = pathlib.Path(os.environ['AZURPILOT_SOURCE'])
spec = importlib.util.spec_from_file_location('azurpilot_frontend', root / 'deploy/frontend.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
(root / 'frontend/dist/.source-fingerprint').write_text(mod.source_fingerprint(root / 'frontend') + '\n')
PY
rm -rf "$FRONTEND/node_modules"

guest /bin/sh -c 'cd /opt/azurpilot && AZURPILOT_ANDROID=1 .venv/bin/python -c "import cv2,numpy,scipy,onnxruntime,rapidocr,ncnn,psutil; import module.api.app, module.device.device, module.ocr.al_ocr; assert psutil.Process.children.__module__ == \"android_process_compat\"; print(\"IMPORTS_OK\")"'
mkdir -p "$ROOTFS_DIR/opt/azurpilot/log"

SOURCE_COMMIT="$SOURCE_COMMIT" SOURCE_REPO="$SOURCE_REPO" REPO_ROOT="$REPO_ROOT" \
    TARGET_ABI="$TARGET_ABI" \
    ROOTFS_DIR="$ROOTFS_DIR" python3 - <<'PY'
import datetime, hashlib, json, os, pathlib, subprocess
root = pathlib.Path(os.environ['ROOTFS_DIR']) / 'opt/azurpilot'
host_commit = subprocess.check_output(['git', '-C', os.environ['REPO_ROOT'], 'rev-parse', 'HEAD'], text=True).strip()
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
repo_root = pathlib.Path(os.environ['REPO_ROOT'])
inputs = repo_root / 'rootfs'
content_hash = hashlib.sha256()
tracked = subprocess.check_output(
    ['git', 'ls-files', '-z', '--', 'rootfs/build', 'rootfs/overlays', 'rootfs/seeds'],
    cwd=repo_root,
)
for name in filter(None, tracked.split(b'\0')):
    path = repo_root / os.fsdecode(name)
    content_hash.update(str(path.relative_to(inputs)).encode())
    content_hash.update(path.read_bytes())
manifest = {
    'rootfs_version': os.environ['SOURCE_COMMIT'][:12] + '-' + content_hash.hexdigest()[:10],
    'runtime': 'azurpilot-android',
    'android_host_commit': host_commit,
    'azurpilot_repo': os.environ['SOURCE_REPO'],
    'azurpilot_commit': os.environ['SOURCE_COMMIT'],
    'rootfs_arch': os.environ['TARGET_ABI'],
    'android_api_version': 1,
    'uv_lock_sha256': sha(root / 'uv.lock'),
    'frontend_sha256': sha(root / 'frontend/dist/index.html'),
    'python_version': '3.14.6',
    'built_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
}
(root / 'BUILD_MANIFEST').write_text(json.dumps(manifest, indent=2) + '\n')
PY
cp "$ROOTFS_DIR/opt/azurpilot/BUILD_MANIFEST" "$DIST_DIR/BUILD_MANIFEST"

# Python 依赖位于版本目录外，源码更新时继续复用已验证的锁定环境。
mv "$ROOTFS_DIR/opt/azurpilot/.venv" "$ROOTFS_DIR/opt/azurpilot-venv"
ln -s ../azurpilot-venv "$ROOTFS_DIR/opt/azurpilot/.venv"

unmount_all
for d in dev dev/pts proc sys etc/resolv.conf opt/uv-cache; do
    if mountpoint -q "$ROOTFS_DIR/$d"; then echo "挂载未清理: $d" >&2; exit 1; fi
done
# 缓存是 root 写的，这里交还 runner 用户：post-step 的 actions/cache 以 runner 身份打包上传
if [[ -n ${SUDO_UID:-} ]]; then
    chown -R "$SUDO_UID:${SUDO_GID:-$SUDO_UID}" \
        "$WORK_DIR/uv-cache" "$WORK_DIR/npm-cache" "$WORK_DIR/ubuntu-base.tar.gz" ||
        echo '缓存目录 chown 失败（不影响构建，只可能影响缓存保存）' >&2
fi
# opt/uv-cache 是宿主缓存挂进来的，此刻已卸挂，下面删掉的只是空挂载点
rm -rf "$ROOTFS_DIR/opt/uv-cache" "$ROOTFS_DIR/root/.cache" "$ROOTFS_DIR/var/lib/apt/lists"/*
rm -rf "$ROOTFS_DIR/opt/azurpilot/.git"
find "$ROOTFS_DIR" -type d -name __pycache__ -prune -exec rm -rf {} +
XZ_OPT=-T0 tar --one-file-system -C "$ROOTFS_DIR" -cJf "$DIST_DIR/rootfs.tar.xz" .
sha256sum "$DIST_DIR/rootfs.tar.xz"

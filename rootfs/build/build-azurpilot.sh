#!/usr/bin/env bash
# 在 GitHub Actions ubuntu-24.04-arm 上构建 AzurPilot Android rootfs。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${WORK_DIR:-$REPO_ROOT/.tmp/azurpilot-build}"
ROOTFS_DIR="$WORK_DIR/rootfs"
DIST_DIR="${DIST_DIR:-$REPO_ROOT/dist}"
SOURCE_REPO="${AZURPILOT_REPO:-https://github.com/wess09/AzurPilot.git}"
SOURCE_REF="${AZURPILOT_REF:-19ac61ace0b6111fcd859deef81cd96c4f643eec}"
BASE_URL="${UBUNTU_BASE:-https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz}"

if [[ $(id -u) -ne 0 ]]; then
    # setup-node/setup-uv 在 runner 的工具目录注入 PATH；sudo 默认 secure_path 会丢掉它们。
    exec sudo -E env "PATH=$PATH" bash "$0" "$@"
fi
[[ $(uname -m) == aarch64 ]] || { echo '需要原生 ARM64 runner' >&2; exit 1; }
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

guest /bin/sh -c 'cd /opt/azurpilot && AZURPILOT_ANDROID=1 .venv/bin/python -c "import cv2,numpy,scipy,onnxruntime,rapidocr,ncnn; import module.api.app, module.device.device, module.ocr.al_ocr; print(\"IMPORTS_OK\")"'
mkdir -p "$ROOTFS_DIR/opt/azurpilot/log"
guest /bin/sh -c 'cd /opt/azurpilot && AZURPILOT_ANDROID=1 .venv/bin/python -m dev_tools.import_smoke_test'
guest /bin/sh -c 'cd /opt/azurpilot && .venv/bin/python azurpilot-ocr-gate.py'

SOURCE_COMMIT="$SOURCE_COMMIT" SOURCE_REPO="$SOURCE_REPO" REPO_ROOT="$REPO_ROOT" \
    ROOTFS_DIR="$ROOTFS_DIR" python3 - <<'PY'
import datetime, hashlib, json, os, pathlib, subprocess
root = pathlib.Path(os.environ['ROOTFS_DIR']) / 'opt/azurpilot'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifest = {
    'rootfs_version': os.environ['SOURCE_COMMIT'][:12],
    'runtime': 'azurpilot-android',
    'azurpilot_repo': os.environ['SOURCE_REPO'],
    'azurpilot_commit': os.environ['SOURCE_COMMIT'],
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
for d in dev dev/pts proc sys etc/resolv.conf; do
    if mountpoint -q "$ROOTFS_DIR/$d"; then echo "挂载未清理: $d" >&2; exit 1; fi
done
rm -rf "$ROOTFS_DIR/opt/uv-cache" "$ROOTFS_DIR/root/.cache" "$ROOTFS_DIR/var/lib/apt/lists"/*
rm -rf "$ROOTFS_DIR/opt/azurpilot/.git"
find "$ROOTFS_DIR" -type d -name __pycache__ -prune -exec rm -rf {} +
XZ_OPT=-T0 tar --one-file-system -C "$ROOTFS_DIR" -cJf "$DIST_DIR/rootfs.tar.xz" .
sha256sum "$DIST_DIR/rootfs.tar.xz"

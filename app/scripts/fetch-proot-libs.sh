#!/usr/bin/env bash
# 生成 x86_64 的 proot 原生库九件套，输出到 app/src/main/prootLibs/x86_64/。
#
# 来源与 arm64-v8a 一致（Spike A 实证配方，见 spike/a-proot-exec/REPORT.md §1.2/§7）：
# Termux 包改名为 lib*.so 并用同长字符串改写 DT_NEEDED。本脚本每次构建自动拉取，
# 版本与 SHA256 钉死，仓库内不提交生成产物。arm64-v8a 仍是仓内已验证的钉版产物。
#
# 依赖关系（AGP 打包要求 lib*.so 命名，安装后落在 nativeLibraryDir）：
#   libproot.so        <- Termux proot: usr/bin/proot（改写 NEEDED libtalloc.so.2 -> libtalloc.so）
#   libproot-loader.so <- Termux proot: usr/libexec/proot/loader（静态 ELF，proot exec 它拉起客户机）
#   libtalloc.so       <- libtalloc: usr/lib/libtalloc.so.2.4.3
#   libandroid-shmem.so / libandroid-selinux.so / libpcre2-8.so   <- 同名包，直接改名
#   libbusybox.so      <- busybox: usr/bin/busybox 4KB stub（改写 NEEDED -> libbusybox_app.so）
#   libbusybox_app.so  <- busybox: usr/lib/libbusybox.so.1.38.0（真正的 applet 载荷，SONAME 保持原样）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$REPO_ROOT/app/app/src/main/prootLibs/x86_64"
TMP_DIR="${TMPDIR:-/tmp}/fetch-proot-libs-x86_64"
POOL="https://packages.termux.dev/apt/termux-main/pool/main"

# 包名_版本 -> data.tar 里相对 data/data/com.termux/files/usr 的路径；SHA256 为当日快照钉死值
PKGS=(
    "proot_5.1.107.95_x86_64.deb|f63ce9bd0d38715eae0163a3772f3395913587444c7ce7232091c6d359afe3c3"
    "libtalloc_2.4.3_x86_64.deb|7ca2eaae2e53b28228a01301bc410b62845403d6317c25b8e0a7f40681de0628"
    "libandroid-shmem_0.7_x86_64.deb|ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797"
    "libandroid-selinux_14.0.0.11-1_x86_64.deb|99cf96556683ddb53f7d645ca1720e10523c4796ce5b41c583da9f89a47679ce"
    "pcre2_10.47_x86_64.deb|8e4fb14ba014f9b2d5e07b6ed9c519b31d00a6e7ddeb5804c3b076a6c841c2fb"
    "busybox_1.38.0-1_x86_64.deb|519b57623dd076b4d6cf6d389ed976dd222410e3a0b9b9b58c14d8535b6eef48"
)

# Windows 的 python3 可能是商店占位 stub，必须实际能跑才算数
PY=""
for cand in python3 python; do
    if command -v "$cand" >/dev/null 2>&1 && "$cand" -c 'import sys' 2>/dev/null; then
        PY="$cand"
        break
    fi
done
[[ -n $PY ]] || { echo '需要 python3 或 python' >&2; exit 1; }
command -v curl >/dev/null || { echo '需要 curl' >&2; exit 1; }

mkdir -p "$TMP_DIR" "$OUT_DIR"

for entry in "${PKGS[@]}"; do
    deb="${entry%%|*}"
    sha="${entry##*|}"
    target="$TMP_DIR/$deb"
    if [[ -s $target && "$(sha256sum "$target" | awk '{print $1}')" == "$sha" ]]; then
        echo "cached ok: $deb"
    else
        echo "downloading $deb"
        name="${deb%%_*}"
        # Debian pool 目录约定：lib 开头取前 4 字符（liba/…），其余取首字符（p/…）
        prefix_dir="${name:0:4}"
        [[ $name == lib* ]] || prefix_dir="${name:0:1}"
        curl -fsL --retry 3 "$POOL/$prefix_dir/$name/$deb" -o "$target"
        actual="$(sha256sum "$target" | awk '{print $1}')"
        [[ "$actual" == "$sha" ]] || { echo "$deb SHA256 mismatch: $actual" >&2; exit 1; }
    fi
done

# 解包 + 改名 + DT_NEEDED 同长改写 + ELF 校验，全在 python 里一次完成
"$PY" - "$TMP_DIR" "$OUT_DIR" <<'PY'
import io, lzma, os, shutil, struct, sys, tarfile

tmp_dir, out_dir = sys.argv[1], sys.argv[2]
PREFIX = './data/data/com.termux/files/usr/'

# (deb, 源路径, 输出名, [(旧 NEEDED 名, 新 NEEDED 名)])
PLAN = [
    ('proot_5.1.107.95_x86_64.deb', 'bin/proot', 'libproot.so',
     [('libtalloc.so.2', 'libtalloc.so')]),
    ('proot_5.1.107.95_x86_64.deb', 'libexec/proot/loader', 'libproot-loader.so', []),
    ('libtalloc_2.4.3_x86_64.deb', 'lib/libtalloc.so.2.4.3', 'libtalloc.so', []),
    ('libandroid-shmem_0.7_x86_64.deb', 'lib/libandroid-shmem.so', 'libandroid-shmem.so', []),
    ('libandroid-selinux_14.0.0.11-1_x86_64.deb', 'lib/libandroid-selinux.so', 'libandroid-selinux.so', []),
    ('pcre2_10.47_x86_64.deb', 'lib/libpcre2-8.so', 'libpcre2-8.so', []),
    ('busybox_1.38.0-1_x86_64.deb', 'bin/busybox', 'libbusybox.so',
     [('libbusybox.so.1.38.0', 'libbusybox_app.so')]),
    ('busybox_1.38.0-1_x86_64.deb', 'lib/libbusybox.so.1.38.0', 'libbusybox_app.so', []),
]


def data_tar(deb_path):
    with open(deb_path, 'rb') as f:
        blob = f.read()
    assert blob[:8] == b'!<arch>\n', f'{deb_path} 不是 deb(ar) 包'
    off = 8
    while off + 60 <= len(blob):
        hdr = blob[off:off + 60]
        name = hdr[0:16].decode().strip().rstrip('/')
        size = int(hdr[48:58].decode().strip())
        body = blob[off + 60:off + 60 + size]
        if name.startswith('data.tar.'):
            raw = lzma.decompress(body) if name.endswith('.xz') else body
            return tarfile.open(fileobj=io.BytesIO(raw), mode='r:')
        off += 60 + size + (size % 2)
    raise AssertionError(f'{deb_path} 缺少 data.tar')


tars = {}
for deb, src, dest, patches in PLAN:
    if deb not in tars:
        tars[deb] = data_tar(os.path.join(tmp_dir, deb))
    member = next((m for m in tars[deb].getmembers()
                   if m.isfile() and m.name == PREFIX + src), None)
    if member is None:
        raise AssertionError(f'{deb} 里找不到 {src}')
    blob = tars[deb].extractfile(member).read()
    for old, new in patches:
        old_b, new_b = old.encode() + b'\0', new.encode()
        assert len(new_b) <= len(old_b) - 1, f'{dest}: {new} 放不进 {old} 的位置'
        assert blob.count(old_b) == 1, f'{dest}: 期望恰好 1 处 {old}'
        blob = blob.replace(old_b, new_b + b'\0' * (len(old_b) - len(new_b)))
    # 全部应为 64 位小端 ELF；proot 二进制与 loader 是 ET_EXEC/ET_DYN 均可
    assert blob[:4] == b'\x7fELF' and blob[4] == 2 and blob[5] == 1, f'{dest} 不是 64 位 ELF'
    machine = struct.unpack_from('<H', blob, 18)[0]
    assert machine == 62, f'{dest} machine={machine}，期望 x86_64(62)'
    with open(os.path.join(out_dir, dest), 'wb') as g:
        g.write(blob)
    print(f'  {dest}  {len(blob)} bytes')

# 改写后的引用必须落位
proot = open(os.path.join(out_dir, 'libproot.so'), 'rb').read()
assert b'libtalloc.so\0' in proot and b'libtalloc.so.2\0' not in proot
stub = open(os.path.join(out_dir, 'libbusybox.so'), 'rb').read()
assert b'libbusybox_app.so\0' in stub and b'libbusybox.so.1.38.0\0' not in stub

# 清掉上次可能残留的旧产物（prootLibs 下只放本脚本管理的这批）
for stale in os.listdir(out_dir):
    if stale not in {dest for _, _, dest, _ in PLAN}:
        os.remove(os.path.join(out_dir, stale))
        print(f'  removed stale {stale}')
print('OK: prootLibs/x86_64 就绪')
PY

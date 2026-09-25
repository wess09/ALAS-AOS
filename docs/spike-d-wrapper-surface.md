# Spike D 探查报告：AzurPilot 进程管理 import 面（2026-09-15）

> 本文是路线图 v3 阶段〇 Spike D 的交付物，供 wrapper.py（rootfs 内）设计使用。探查方式为只读源码分析。

已完成探查（只读，未改动 `.tmp/azurpilot` 任何文件）。**基线**：`.tmp/azurpilot` = LmeSzinc/AzurLaneAutoScript `d816310324bf686dff3252b37efbb997d9a328a4`（2026-08-18，master，partial clone `blob:none`，工作树干净）。以下所有 `文件:行号` 均相对 `D:\VSCodeCache\azurpilot-azurpilot\.tmp\azurpilot`。

---

## 1. WebUI 架构

**启动链**（无 flask，是 **uvicorn + pywebio(FastAPI/Starlette 适配)**）：

```
python gui.py                        → gui.py:109 func(None)
  （EnableReload: true 时先起 reload 循环）gui.py:88-107
  → uvicorn.run("module.webui.app:app", factory=True)   gui.py:83-85
  → module/webui/app.py:1475 app()
      · argparse: -k/--key, --cdn, --run          app.py:1477-1496
      · 两个 pywebio 页面 index / manage          app.py:1516-1531
      · asgi_app(...) → Starlette + pywebio routes  module/webui/fastapi.py:27-60
      · on_startup = [startup, ProcessManager.restart_processes]   app.py:1533-1544
      · on_shutdown = [clearup]                   app.py:1545（clearup 定义 app.py:1459-1472）
```

- `startup()`：`State.init()`（multiprocessing.Manager）、lang、`updater.event = State.manager.Event()`、按 `CheckUpdateInterval` 挂 check_update、挂 `schedule_update()`、起 `task_handler`、可选 Discord/OCR server/RemoteAccess（app.py:1440-1454）。
- `--run` / `deploy.Run` 的自动启动就走 `ProcessManager.restart_processes(instances, ev)`（app.py:1498-1504、1542-1544）。
- Windows 图形安装器入口是 `deploy/launcher/AzurPilot.bat:20`（`python -m deploy.installer`），与 gui.py 无关。
- `module/webui/__init__.py` 只做一件事：把 `deploy.logger.logger` 指向 AzurPilot logger（第 1-5 行）。

**端口 22267 的配置链**：
- 优先级：CLI `-p/--port` > deploy 配置 > 硬编码兜底 → `gui.py:57-58`。
- deploy 键：`Deploy: Webui: WebuiPort`（`config/deploy.template.yaml:126-127`；类默认 `deploy/config.py:54-55`）。
- 用户文件是 `./config/deploy.yaml`（`deploy/utils.py:9`），**YAML 是嵌套的，但解析器 `poor_yaml_read` 按行扁平化**——读取时键名就是 `WebuiPort`，没有 `Deploy.Webui.` 前缀（`deploy/utils.py:58-89`）；写入用正则替换模板行（`poor_yaml_write`，`deploy/utils.py:92-111`）。
- 另有 `remote_access.py:44 local_port=22267`（ssh 反代默认端口，与监听端口独立）。

---

## 2. 进程管理面

唯一实现：`module/webui/process_manager.py`（233 行）。

| 成员 | 位置 | 语义 |
|---|---|---|
| `ProcessManager.__init__(config_name="azurpilot")` | 28-36 | 每个实例一个对象，日志队列 `State.manager.Queue()` |
| `start(func=None, ev=None)` | 38-52 | `multiprocessing.Process(target=ProcessManager.run_process, args=(config_name, func, queue, ev))` **进程内 fork/spawn，不是 subprocess、没有命令行**；`func=None` 时用 `get_config_mod(name)`（`module/submodule/utils.py:79-89`，普通实例恒为 `'azurpilot'`） |
| `stop()` | 65-84 | **`self._process.kill()` → SIGKILL**（POSIX）；无 terminate/信号链；无 graceful |
| `alive` | 97-102 | `Process.is_alive()` |
| `state` | 104-122 | **靠最后一条日志文本判定**：1=运行中，2=停止（logs 空 / 结尾 `Reason: Manual stop` / `Reason: Finish`），4=更新（`Reason: Update`），3=异常 |
| `get_manager(name)` | 124-131 | 类级单例表 `_processes` |
| `running_instances()` | 185-191 | |
| `restart_processes(instances, ev)` | 193-233 | 还会读 上游重载哨兵（218-223）补实例，启动后删除该文件（229-232） |
| `run_process(config_name, func, q, e)` | 133-183 | 子进程真正入口 |

**运行体分派**（process_manager.py:158-181）：
- `func == "azurpilot"` → `AzurLaneAutoScript(config_name).loop()` → **调度器进程**（azurpilot.py:549-618）。
- `func in get_available_func()`（Daemon / OpsiDaemon / EventStory / IslandProductionPlanner / AzurLaneUncensored / Benchmark / GameManager，`module/submodule/utils.py:22-31`）→ `AzurLaneAutoScript(config_name).run(inflection.underscore(func), skip_first_screenshot=True)` → **单任务进程**。
- `func in get_available_mod()`（azurpilot/fpy 子模块）→ `load_mod(func).loop(config_name)`。
- 结束统一打 `[{name}] exited. Reason: Finish`（181）。

**与 WebUI 之间的控制通道只有两条**，没有 HTTP/CLI 给调度器：
1. 共享 `manager.Event()`：`updater.event`（app.py:1443）→ `AzurLaneAutoScript.stop_event`（process_manager.py:158、164-166）→ `loop()` 检查点（azurpilot.py:555-559）与 `wait_until`（azurpilot.py:481-485）；置位即"优雅停"（日志 `Reason: Update`，azurpilot.py:484）。
2. 配置文件 mtime 热重载（`config/watcher.py:23-33` ← azurpilot.py:489）。
   按钮绑定见 app.py:453-461（总开关，`onclick_on=self.azurpilot.stop()` / `onclick_off=self.azurpilot.start(None, updater.event)`）与 app.py:685-689（单任务）。
3. 附带进程：OCR server 独立 `multiprocessing.Process`（`module/ocr/rpc.py:245-252`，由 `StartOcrServer` 触发，app.py:1448-1450）。
4. 未显式设置 start method（全仓无 `set_start_method`），故 fork（Linux ≤3.13）/forkserver（3.14）/spawn 视 Python 版本；`module/device/device.py:26-36` 的堆栈样例即 spawn 路径。

---

## 3. 日志面

**文件路径**：`./log/{YYYY-MM-DD}_{config_name}.txt`（`module/logger.py:171-177`，`set_file_logger(name)`）。
- 导入 `module.logger` 时就会 `os.chdir(<AzurPilot 根>)`（logger.py:165），并在 import 期先建一份 `{date}_{sys.argv[0] 基名}.txt`（logger.py:168、337）。
- 进程启动时**只调用一次** `set_file_logger`（调度器在 `loop()` 里还会再调一次，azurpilot.py:550；WebUI 子进程在 process_manager.py:145 调），文件以 append 打开、句柄不重开 → **日期不随午夜滚动**，wrapper tail 时路径固定为"启动日"。
- 错误快照另存 `./log/error/{time_ms}/`（azurpilot.py:135-163）。

**WebUI 不读文件**：
`set_func_logger(func=q.put)`（process_manager.py:151；实现 logger.py:204-229）把 rich renderable 塞进 `multiprocessing` 队列 `_renderable_queue`（process_manager.py:30）→ 子进程日志经 `_thread_log_queue_handler` 收进 `renderables` 列表（86-95）→ 前端 `RichLog.put_log()` 生成器按 **0.25s 轮询**取增量并经 pywebio `run_js` 注入 DOM（app.py:499、743；`module/webui/widgets.py:189-215`）。**不是文件 tail，也不是 websocket**。

**wrapper 最省方案**：`tail -F ./log/<启动日>_<config_name>.txt`（read 侧只需处理"文件在进程启动后出现"与"整天不换名"两种情况）；若 wrapper 改为 in-process 跑 AzurPilot，可直接 `logger.set_func_logger(my_cb)` 拿到结构化 renderable，省掉 tail。

---

## 4. 配置面

**实例配置**：`./config/{name}.json`（`module/config/utils.py:58-64`；读写 `config_updater.py:773-801`）。
- 结构：`{Task: {Group: {Arg: value}}}`，顶层 68 个 key，含 57 个带 `Scheduler` 组的任务（实测 `config/template.json`）。
- 缺 key 自动补默认：schema 来自 `module/config/argument/args.json`（`config_update`，`config_updater.py:617-683`；**未被 schema 覆盖的键在 save 时会被丢弃**）。
- 首次实例化会**主动写盘**：`override()` 把 Commission/Research/Reward 的 `Scheduler.Enable=True` 记入 `modified`（config.py:283-326），`init_task` 末尾 `save()` 落盘整份 `self.data`（config.py:110-131、263-274）。→ 空配置也能跑，但**这三个任务必然被打开**（`args.json` 中其 `Scheduler.Enable.type == "lock"`，config_update 会强制回默认 true，config_updater.py:632-640）。

**任务开关 / 调度计划**：
- `{Task}.Scheduler.Enable`(checkbox/lock)、`.NextRun`(type `datetime`，字符串格式 `YYYY-MM-DD HH:MM:SS`)、`.Command`(hide，任务名)、`.SuccessInterval/.FailureInterval/.ServerUpdate`(hide)（`module/config/argument/argument.yaml:7-21`；`module/config/config_generated.py:13-15`；`config.py:24-43` Function 类）。
- 计算流程：`get_next_task()`（config.py:203-235）→ `pending_task`/`waiting_task`；执行顺序由 `SCHEDULER_PRIORITY`（`config_manual.py:11-36`）过滤；`get_next()` 无任务时抛 `RequestHumanTakeover`（config.py:236-261）→ GUI 里 `exit(1)`。

**"写配置 → 无头启动调度器"完全可行**，官方自己就有（`python azurpilot.py`，见 Q5）：
```
cwd 必须是 AzurPilot 根（所有路径都是 './...' 相对路径）
1) 原子写 ./config/<name>.json：{Task}.Scheduler.Enable=true, NextRun='YYYY-MM-DD HH:MM:SS'
   （Commission/Research/Reward 无法关闭；Restart 之类还需注意 priority）
2) 起进程：python -c "from azurpilot import AzurLaneAutoScript as A; A('<name>').loop()"  （或等价 runner）
```
需要触碰的内部函数（若 1 步想复用 AzurPilot 自身的校验/补全）：
- `module.config.utils.filepath_config/read_file/write_file`（utils.py:58-115，`atomic_write` → tmp + `os.replace`，`deploy/atomic.py:261-278`、`109-152`）；
- `module.config.config_updater.ConfigUpdater().update_file(name)`（config_updater.py:803-810，做 schema 补全+回写）；
- `module.config.config.AzurLaneConfig(name)`（会读文件、按 schema 补全、绑定参数、强制三任务开启并回写）。
- **无锁文件机制**（`deploy/atomic.py` 全文无 lock），但 AzurPilot `save()` 每次都写整份 `data`（config.py:271-274）→ **运行中外部改配置可能被 AzurPilot 的下一次 save 覆盖**；改配置最稳的时机是停着改，或只改 `NextRun` 并接受竞态。
- 运行时热生效：`ConfigWatcher` mtime（watcher.py:23-33）只在 `wait_until` 检查点命中（azurpilot.py:489）。
- 注意：`module/config/config.py:6` 在模块级 `import pywebio` 并猴补丁（config.py:742-743）→ 即使无头跑，**pywebio 必须可 import**（但不需要起服务器）。

---

## 5. 入口面

| 入口 | 位置 | 作用 |
|---|---|---|
| `azurpilot.py` | azurpilot.py:621-623 | `AzurLaneAutoScript().loop()` —— **官方无头调度器跑法**（config_name 固定 `'azurpilot'`） |
| `gui.py` | gui.py:1-109 | WebUI 启动器（uvicorn + reload 循环） |
| `module/webui/app.py` | app.py:1475-1546 | ASGI app 工厂（pywebio 页面 + 生命周期） |
| `deploy/installer.py` | installer.py:13-24 | `python -m deploy.installer`：git_install→process_kill→pip_install→app_update→adb_install（Windows 图形安装器的实际动作） |
| `deploy/set.py` | set.py:24-33 | `python -m deploy.set Key=Value` 写 deploy.yaml |
| Docker | `deploy/docker/Dockerfile:23`、`dev_tools/arm64/Dockerfile:25` | `CMD python gui.py` |

- **有没有官方"纯调度器"支持**：只有 `python azurpilot.py` + `deploy/headless/requirements.txt`（无 pywebio/无 GUI 依赖的 headless 依赖清单）+ linux 模板把 `RequirementsFile` 指到它（`config_updater.py:490-499`）。没有 CLI 参数化的无头入口，也没有 systemd/service 示例；多实例无头必须自己写一行 `-c`。
- **Python 版本断言**：**全仓没有版本检查/断言**。相关痕迹只有：
  - `config/deploy.template*.yaml:30` 注释 "should be 3.7.6 64bit"；
  - `requirements.txt:1-8` 是 pip-compile with Python 3.7 生成，钉了一堆 3.14 上装不了的老包（numpy 1.16.6、mxnet 1.6.0、cnocr 1.2.2、pywebio 1.6.2、uvicorn 0.17.6、rich 11.2.0、pydantic 1.10.2…）；
  - `module/webui/patch.py:71-81`：仅 `win32 and 3.7` 才生效的 subprocess 猴补丁；
  - `deploy/azurpilot.py:43`：`sys.version_info` 只用于 win32com gen_py 目录名。
- **m0 补丁与版本/依赖差异的对应**（`m0-archive/termux/patches/` + `apply_patches.sh`、`setup_env.sh`）：
  - **抹平版本差异**：`module/webui/utils.py`（pywebio `TaskHandler.stop` 在 `_thread=None` 时 join 崩，apply_patches.sh 明说 3.14 暴露）、`module/map_detection/utils.py`（numpy≥2 拒绝 `np.vstack(generator)`）、`module/webui/patch.py`（3.12+ `asyncio.get_event_loop` 无 loop 抛 RuntimeError）。
  - **抹平依赖差异**：`m0-archive/termux/shims/jellyfish.py`（jellyfish 现代版 Rust/maturin，Termux 装不了；AzurPilot 只用 `levenshtein_distance`，见 `module/commission/project.py`）、`setup_env.sh`（native 层走 `pkg` 的 numpy/scipy/opencv/PIL/lxml，pip 只装纯 Python 且 `pydantic<2`，`apt-mark hold python-scipy`）。
  - **与版本无关、属桥接/环境**：`module/device/method/azurpilot.py`+`utils.py`+`minitouch.py`+`connection.py`/`screenshot.py`/`control.py`/`app_control.py`（azurpilot 设备通道）、`module/ocr/rpc.py`（OCR 走 TCP 代理替换 zerorpc）、`module/base/base.py`（桥接模式预热不 import cnocr）、`module/config/argument/{argument.yaml,args.json}`（新增 Screenshot/ControlMethod 选项）、`patches/assets/*.png` + `assets_fix.py`（真机素材校准）。

---

## 6. 更新器

**deploy 键的真实语义**（类默认 `deploy/config.py:12-67`；模板 `deploy/template:1-162`；解析 `deploy/config.py:93-113`）：

| 键 | 代码位置 | 语义 |
|---|---|---|
| `Repository`/`Branch` | `deploy/git.py:97-103`、`deploy/config.py:118-142` | 被 `config_redirect()` 重写（gitee/`cn`/`global` 别名 → `git://git.lyoko.io/...`）；`Repository==git.lyoko.io && Branch==master` ⇒ `GitOverCdn=True`（deploy/config.py:135-138） |
| `GitProxy`/`SSLVerify` | `deploy/git.py:35-46` | 直接 `git config --local http.proxy/http.sslVerify` |
| `AutoUpdate` | `deploy/git.py:89-91`（git）、`deploy/app.py:49-51`（app.asar） | false ⇒ `git_install()`/`app_update()` 直接 return |
| `PypiMirror` | `deploy/pip.py:136-144` | 仅影响 pip 参数 |
| `InstallDependencies` | `deploy/pip.py:122-124` | false ⇒ `pip_install()` 直接 return |
| `RequirementsFile` | `deploy/pip.py:56-61` | linux 模板默认指向 `./deploy/headless/requirements.txt`（`config_updater.py:498`） |

**git pull 的执行路径**（全部在 `deploy/`，不在 module/updater）：
`GitManager.git_install()`（git.py:86-103）→（AutoUpdate true 时）GOC 或 `git_repository_init()`（git.py:25-69）：`git init`/proxy/ssl → `remote set-url` → **`git fetch origin <branch>`（53）** → 删 `.git/*.lock`（57-64）→ **`git reset --hard origin/<branch>`（65）** → **`git pull --ff-only origin <branch>`（66）**。
GOC 分支：`deploy/git_over_cdn/client.py:249-...`，同样以 `git reset --hard origin/master`（client.py:215-226）收尾。
调用方：`module/webui/updater.py:195-202 update()` = `git_install()+pip_install()`；入口只有两处 —— Develop 页按钮（app.py:859、876 → `updater.run_update`）与每日计划（`schedule_update()`，updater.py:278-298）；`check_update` 只 `fetch`（updater.py:86-88），不 pull。

**要满足"只拉源码、永不升级依赖、锁 commit"，应设**（与 m0 种子 `m0-archive/termux/seeds/deploy.yaml` 一致）：
```yaml
AutoUpdate: false            # git.py:89-91, app.py:49-51
InstallDependencies: false   # pip.py:122-124
EnableReload: false          # gui.py:89（避免派生子进程 + 触发 上游重载哨兵 机制）
CheckUpdateInterval: 0       # updater.py:27-29 + app.py:1444-1445（连 fetch 都不做）
AutoRestartTime: null        # updater.py:32-38, 281-283（每日自动更新任务自删）
ReplaceAdb: false / AutoConnect: false / InstallUiautomator2: false   # adb.py:42-47
```
**会违背该设定的暗路径**（需逐条排除/盯住）：
1. `deploy/pip.py:153` 是全仓唯一 `pip install -r` 执行点（Python 解释器取 `sys.executable`，pip.py:44-54）——被 `InstallDependencies` 单键挡住；但一旦误开就会按 `RequirementsFile`（linux 默认 headless 清单）重装/升依赖。
2. `git reset --hard`（git.py:65、client.py:222）会**连带丢掉本地补丁分支改动**；`AutoUpdate: false` 是唯一闸门（`updater.update()` 在 AutoUpdate false 时静默"成功"返回 True，updater.py:195-202 ← git.py:89-91 直接 return）。
3. GOC 路径（CDN 拉包 + reset）只在 `Repository==git.lyoko.io && Branch==master` 时激活，同样被 AutoUpdate 挡。
4. `module/webui/app.py:1446` 无条件挂 `updater.schedule_update()`；必须 `AutoRestartTime: null` 才会自删（updater.py:281-283），否则每天定点检查+可能更新。
5. `deploy/adb.py:49-51` 是 `if False:` —— `InstallUiautomator2` 在当前 master **是死键**；而且 `adb_install()` 只被 `deploy/installer.py:22` 调用，gui.py 路径根本不跑 adb 管理（所以 `AdbExecutable` 桥接下也不会被调用）。
6. **`KeepLocalChanges` 键在本 master 不存在**（全仓 grep 无命中）——v3 方案里不要依赖它；"保留本地改动"只能靠 `AutoUpdate: false` + 自己受控的 git 操作。
7. 无运行时 config 再生成：`ConfigGenerator().generate()` 只在 `config_updater.py:830-838` 的 `__main__`（和 dev_tools）里跑 → 自动更新不会污染 `args.json`；但**任何自建 pull 都必须把 `module/config/argument/args.json` 与 `argument.yaml` 保持同 commit**，否则 `config_update` 会按旧 schema 丢弃用户配置键（config_updater.py:617-683）。
8. 其它 pip/adb 文案：`deploy/adb.py:17-25` 只 `logger.info` 打印修复指引，不执行。

---

## 7. wrapper.py 建议接口（最小 import 面 + 兜底）

### 方案 A：最小 import 面（推荐；不要碰 WebUI 模块）
```python
# cwd 必须 = AzurPilot 根；sys.path 含 AzurPilot 根
from module.logger import logger      # 副作用：os.chdir(AzurPilot 根) + 建 ./log/{date}_{argv0}.txt
                                      #   logger.py:165,168,171-177,337
from module.config.config import AzurLaneConfig   # 需要 pywebio 可 import：config.py:6,742-743
from azurpilot import AzurLaneAutoScript   # azurpilot.py:18

azurpilot = AzurLaneAutoScript(config_name=NAME)   # azurpilot.py:21-28
azurpilot.loop()                                   # 阻塞；azurpilot.py:549-618（内部再 set_file_logger(NAME)）

# 优雅停：启动前 al 注入（同一进程内）
import threading; ev = threading.Event()
AzurLaneAutoScript.stop_event = ev            # azurpilot.py:19,481-485,555-559；ev.set() 后 loop 退出
```
- **状态**：进程存活 = 自己持有的 pid/进程对象；任务级展示复用
  `cfg = AzurLaneConfig(NAME); cfg.load(); cfg.get_next_task()` → `cfg.pending_task / cfg.waiting_task`（config.py:203-235、Function 字段 config.py:24-43；WebUI 同样这么做：app.py:585-601）。
- **日志**：`logger` 的 `./log/{date}_{NAME}.txt`（logger.py:171-177），或 in-process 用 `logger.set_func_logger(cb)`（logger.py:204-229）。
- **配置**：写 `./config/{NAME}.json`（见 Q4），或调 `ConfigUpdater().update_file(NAME)`（config_updater.py:803-810）。
- **建议把 A 放进一个极薄的 `runner.py` 子进程**（`start_new_session=True`），由 wrapper 管进程组——这样 wrapper 本体**完全不 import AzurPilot**，只有 runner 依赖上游内部 API。

### 漂移风险点（上游最可能改）
| 风险 | 位置 | 等级 |
|---|---|---|
| `AzurLaneAutoScript.__init__/loop()/run()/stop_event`、`exit(1)` 行为 | azurpilot.py:18-28、65-133、466-618 | 中（v3 主线，改动等于换架构） |
| `AzurLaneConfig` 绑定模型（`load/config_override/bind/override`、args.json schema、Scheduler 三键、锁定任务） | config.py:60-330、config_updater.py:617-683 | 中 |
| 日志路径与 `os.chdir` 副作用 | logger.py:165-201 | 中（一旦改路径，tail 逻辑失效） |
| deploy 键名与扁平 YAML 解析（`poor_yaml_read`） | deploy/utils.py:58-111、deploy/config.py:12-67 | 中低 |
| `ProcessManager` 全部 API + `state` 依赖日志文本 | process_manager.py 全文（state:104-122） | **高**（若 wrapper 走 WebUI 路线） |
| Python/依赖面（3.14 + numpy2 + pywebio 1.6 + 老 pydantic） | requirements.txt、module/webui/patch.py:81 | **高**（m0 已用 3 个 patch + pkg 层依赖 + jellyfish shim 顶住） |
| `run_process` 是 `multiprocessing` 目标 → 子进程必须能 re-import；Python 3.14 默认改 forkserver | process_manager.py:42-50；全仓无 `set_start_method` | 中 |

### 兜底方案（完全不 import AzurPilot）：可行，所需信息如下
- **准确命令行**（均需 `cwd = AzurPilot 根`，Python 需能 import pywebio）：
  - 默认实例：`python azurpilot.py`（azurpilot.py:621-623 → `AzurLaneAutoScript(config_name='azurpilot').loop()`）；
  - 任意实例名：`python -c "from azurpilot import AzurLaneAutoScript as A; A('azurpilot2').loop()"`（等价 WebUI 的 `run_process` azurpilot 分支，process_manager.py:161-166）；日志名由 `loop()` 内的 `set_file_logger(self.config_name)` 决定（azurpilot.py:550）。
- **进程管理**：`start_new_session=True` 建进程组，停止用 `os.killpg(SIGKILL)`（AzurPilot **没有 SIGTERM handler**，`ProcessManager.stop()` 本身就是 `kill()`，process_manager.py:74）；退出码语义：`loop()` 正常 break/crash 均有 `exit(1)` 分支（azurpilot.py:35-40、104、116、124、133、607）。
- **状态**：pid 存活 + 退出码 + `./config/{name}.json` 的 `{Task}.Scheduler.NextRun`；日志尾部文本兜底（"Start scheduler loop:" azurpilot.py:551、"Scheduler: Start task `X`" azurpilot.py:583、"exited. Reason: Finish/Update" process_manager.py:181 / azurpilot.py:484）。
- **配置**：`os.replace` 原子写 `./config/{name}.json`（schema/锁定任务见 Q4），停着改最安全。
- 需要预先准备的信息只有三条，且都已确认：① AzurPilot 根路径必须作 cwd（所有路径相对）；② 需 pywebio 可 import（仅库，不需服务）；③ 无头只能跑调度器，单任务/Daemon 只能走 Q4 的 `run(task)` 或改 `Scheduler.Command`。

**未找到/不存在的项（明确说明）**：`KeepLocalChanges` 键（全仓无）;任何 Python 版本断言；任何 "锁 commit/pin commit" 内置机制；任何供外部程序控制调度器的 HTTP/CLI 接口（除 pywebio 页面本身）；除 `deploy/Windows/*`（旧版副本，运行时不被引用）外的第二套进程管理实现。
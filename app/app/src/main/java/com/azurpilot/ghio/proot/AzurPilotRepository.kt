package com.azurpilot.ghio.proot

import android.graphics.BitmapFactory
import android.util.Base64
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.i18n.AppLocales
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * 网关状态在 App 侧的唯一归属
 *
 * WebUI 前端是「一条 WS + 一个全局 store」，这里照同样的分工：**订阅只有它一处发出**
 * （`events.subscribe` 是整体替换语义，两个地方各订一次会互相顶掉），事件也只有它一处消费，
 * 再按主题拆成一组 StateFlow 给界面用。
 *
 * 与 [AzurPilotRunController] 的分工：那个走 `/android/…` 薄接口，管「起停进程 + 日志尾」，
 * 悬浮窗和主页控制面板要用；这里补上「总览 / 配置 / 统计 / 设置」这些真正的内容面。
 */
class AzurPilotRepository(
    private val scope: CoroutineScope,
    private val gateway: AzurPilotGateway,
    private val store: AzurPilotPreferenceStore,
) {

    // ── 连接 ─────────────────────────────────────────────────────────────

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** 网关要求先登录（本机直连时为 false）；为真时界面出登录页 */
    private val _authRequired = MutableStateFlow(false)
    val authRequired: StateFlow<Boolean> = _authRequired.asStateFlow()

    // ── 选择 ─────────────────────────────────────────────────────────────

    private val _selectedInstance = MutableStateFlow(store.selectedInstance)
    val selectedInstance: StateFlow<String?> = _selectedInstance.asStateFlow()

    // ── 骨架 ─────────────────────────────────────────────────────────────

    private val _schema = MutableStateFlow<AzurPilotSchema?>(null)
    val schema: StateFlow<AzurPilotSchema?> = _schema.asStateFlow()

    private val _schemaError = MutableStateFlow<String?>(null)
    val schemaError: StateFlow<String?> = _schemaError.asStateFlow()

    private var schemaLanguage: String? = null

    // ── 数据 ─────────────────────────────────────────────────────────────

    private val _instances = MutableStateFlow<List<AzurPilotInstance>>(emptyList())
    val instances: StateFlow<List<AzurPilotInstance>> = _instances.asStateFlow()

    private val _overview = MutableStateFlow<AzurPilotOverview?>(null)
    val overview: StateFlow<AzurPilotOverview?> = _overview.asStateFlow()

    private val _startup = MutableStateFlow<AzurPilotStartup?>(null)
    val startup: StateFlow<AzurPilotStartup?> = _startup.asStateFlow()

    private val _logs = MutableStateFlow<List<AzurPilotLogEntry>>(emptyList())
    val logs: StateFlow<List<AzurPilotLogEntry>> = _logs.asStateFlow()

    private val _preview = MutableStateFlow<AzurPilotPreview?>(null)
    val preview: StateFlow<AzurPilotPreview?> = _preview.asStateFlow()

    private val _deploy = MutableStateFlow<AzurPilotDeploySettings?>(null)
    val deploySettings: StateFlow<AzurPilotDeploySettings?> = _deploy.asStateFlow()

    private val _updater = MutableStateFlow<AzurPilotUpdateStatus?>(null)
    val updater: StateFlow<AzurPilotUpdateStatus?> = _updater.asStateFlow()

    private val _announcement = MutableStateFlow<AzurPilotAnnouncement?>(null)
    val announcement: StateFlow<AzurPilotAnnouncement?> = _announcement.asStateFlow()

    private val _meowfficer = MutableStateFlow<AzurPilotMeowfficerReport?>(null)
    val meowfficer: StateFlow<AzurPilotMeowfficerReport?> = _meowfficer.asStateFlow()

    /** 统计数据有变（网关只在指纹变化时推）；界面收到就重取当前分类 */
    private val _statisticsTick = MutableStateFlow(0L)
    val statisticsTick: StateFlow<Long> = _statisticsTick.asStateFlow()

    /** 界面上的瞬时提示（运行/停止/保存失败等），一次性消费 */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /**
     * 调度器启停正在进行
     *
     * stop 在服务端要等进程真正退出（最长 30s）才回话，这期间按钮必须自己禁用——
     * 重复点会连发好几条 stop，而且界面上分不出哪一次是「新的」。
     */
    private val _schedulerBusy = MutableStateFlow(false)
    val schedulerBusy: StateFlow<Boolean> = _schedulerBusy.asStateFlow()

    val configEditor = AzurPilotConfigEditor(scope, gateway)

    private var eventsJob: Job? = null
    private var pollJob: Job? = null
    private var updaterJob: Job? = null
    private val refreshMutex = Mutex()

    /** 最近一次日志游标；`logs.get` 增量拉取与事件合并共用它 */
    private var logCursor = 0L

    fun start() {
        if (eventsJob == null) {
            eventsJob = scope.launch(AppDispatchers.IO) {
                gateway.events.collect(::onEvent)
            }
        }
        if (pollJob == null) {
            pollJob = scope.launch(AppDispatchers.IO) {
                while (true) {
                    val connected = gateway.connected.value
                    if (connected != _connected.value) {
                        _connected.value = connected
                        if (connected) onConnected()
                    }
                    _authRequired.value = gateway.authRequired.value
                    if (connected) refreshMutex.withLock { refreshLocked() }
                    delay(POLL_MS)
                }
            }
        }
    }

    private fun onConnected() {
        scope.launch(AppDispatchers.IO) {
            ensureSchema()
            syncSubscription(force = true)
        }
    }

    // ── 订阅 ─────────────────────────────────────────────────────────────

    /**
     * 把订阅对齐到「当前实例 + 全部主题」
     *
     * 四个主题一起订：日志与截图的推送只在订阅期间产生，等到打开对应页面再订会先空一段时间；
     * 而它们本身是按需产出的，常订不产生额外负载。
     */
    private suspend fun syncSubscription(force: Boolean = false) {
        if (!gateway.connected.value) return
        val instance = _selectedInstance.value
        val topics = wantedTopics(instance)
        if (!force && topics == lastSubscription?.second && instance == lastSubscription?.first) return
        lastSubscription = instance to topics
        when (val reply = gateway.subscribe(instance, topics)) {
            is AzurPilotGateway.Reply.Ok -> {
                logCursor = 0L
                if (instance != null) loadLogsLocked(instance, after = 0)
            }

            is AzurPilotGateway.Reply.Err -> Timber.w("subscribe failed: %s", reply.message)
            AzurPilotGateway.Reply.Offline -> Unit
        }
    }

    private var lastSubscription: Pair<String?, List<String>>? = null

    /**
     * 该订哪些主题
     *
     * 界面上看不见时把 `preview` 去掉：截图帧是 base64 的大包，而它只在订阅期间才推——
     * 停在别的主页时还照收，纯属白耗流量与内存。
     */
    private fun wantedTopics(instance: String?): List<String> = when {
        instance == null -> listOf(TOPIC_INSTANCES)
        foreground -> TOPICS
        else -> TOPICS.filterNot { it == TOPIC_PREVIEW }
    }

    @Volatile
    private var foreground = true

    fun selectInstance(name: String?) {
        if (_selectedInstance.value == name) return
        _selectedInstance.value = name
        store.selectedInstance = name
        _overview.value = null
        _startup.value = null
        _logs.value = emptyList()
        _preview.value = null
        logCursor = 0L
        // 在途标记跟着实例走，否则切走再切回来会卡在禁用态
        _schedulerBusy.value = false
        configEditor.reset()
        scope.launch(AppDispatchers.IO) {
            lastSubscription = null
            syncSubscription(force = true)
        }
    }

    // ── 骨架 ─────────────────────────────────────────────────────────────

    /** 界面语言对应的 schema 语言；语言切换后下一次拉取自动换成新语言 */
    private fun currentSchemaLanguage(): String = when (AppLocales.currentTag()?.lowercase()) {
        "en" -> "en-US"
        "ja" -> "ja-JP"
        "zh-tw" -> "zh-TW"
        else -> "zh-CN"
    }

    fun ensureSchema(force: Boolean = false) {
        val language = currentSchemaLanguage()
        if (!force && schemaLanguage == language && _schema.value != null) return
        scope.launch(AppDispatchers.IO) {
            when (val reply = gateway.call("schema.get", JSONObject().put("language", language))) {
                is AzurPilotGateway.Reply.Ok -> {
                    schemaLanguage = language
                    _schema.value = AzurPilotSchema.from(reply.obj)
                    _schemaError.value = null
                }

                is AzurPilotGateway.Reply.Err -> _schemaError.value = reply.message
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    // ── 事件 ─────────────────────────────────────────────────────────────

    private fun onEvent(event: JSONObject) {
        when (event.optString("topic")) {
            "instances" -> {
                val list = event.optJSONArray("data")
                _instances.value = parseInstances(list)
                // 当前实例被删了：跟着切到列表里的第一个，避免停在一个不存在的名字上
                val selected = _selectedInstance.value
                if (selected != null && list != null && _instances.value.none { it.name == selected }) {
                    selectInstance(_instances.value.firstOrNull()?.name)
                }
            }

            "overview" -> applyOverview(event.optJSONObject("data"))

            "logs" -> applyLogs(event.optJSONObject("data"))

            "preview" -> applyPreview(event.optJSONObject("data"))

            "statistics" -> _statisticsTick.value = System.currentTimeMillis()
        }
    }

    private fun parseInstances(array: JSONArray?): List<AzurPilotInstance> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@List null
            val name = item.optString("name")
            if (name.isEmpty()) null else AzurPilotInstance(
                name = name,
                status = AzurPilotStatus.of(item.optString("status")),
                currentTask = item.optText("currentTask"),
                serial = item.optString("serial"),
                server = item.optString("server"),
            )
        }.filterNotNull()
    }

    private fun applyOverview(data: JSONObject?) {
        if (data == null) return
        val instance = data.optString("instance")
        if (instance.isNotEmpty() && instance != _selectedInstance.value) return
        val tasks = data.optJSONArray("tasks")?.let { array ->
            List(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@List null
                AzurPilotTask(
                    name = item.optString("name"),
                    nextRun = item.optString("nextRun"),
                    pending = item.optBoolean("pending"),
                    state = AzurPilotTaskState.of(item.optString("state")),
                )
            }.filterNotNull()
        }.orEmpty()
        val resources = data.optJSONArray("resources")?.let { array ->
            List(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@List null
                AzurPilotResource(
                    name = item.optString("name"),
                    label = item.optString("label"),
                    value = (item.opt("value") as? Number)?.toDouble(),
                    limit = (item.opt("limit") as? Number)?.toDouble(),
                    total = (item.opt("total") as? Number)?.toDouble(),
                    record = item.optText("record"),
                )
            }.filterNotNull()
        }.orEmpty()
        _overview.value = AzurPilotOverview(
            instance = instance,
            revision = data.optString("revision"),
            status = AzurPilotStatus.of(data.optString("status")),
            tasks = tasks,
            resources = resources,
        )
    }

    private fun applyLogs(data: JSONObject?) {
        if (data == null) return
        val instance = data.optString("instance")
        if (instance.isNotEmpty() && instance != _selectedInstance.value) return
        val entries = data.optJSONArray("entries")?.let(::parseLogEntries).orEmpty()
        val cursor = data.optLong("cursor", 0L)
        if (data.optBoolean("reset")) {
            _logs.value = entries.takeLast(LOG_LIMIT)
            logCursor = cursor
            return
        }
        if (entries.isEmpty()) {
            logCursor = maxOf(logCursor, cursor)
            return
        }
        // 增量按 id 去重：重连后 `logs.get` 的补拉与推送可能重复同一批
        val known = _logs.value.lastOrNull()?.id ?: Long.MIN_VALUE
        val fresh = entries.filter { it.id > known }
        if (fresh.isEmpty()) {
            logCursor = maxOf(logCursor, cursor)
            return
        }
        _logs.value = (_logs.value + fresh).takeLast(LOG_LIMIT)
        logCursor = maxOf(logCursor, cursor)
    }

    private fun parseLogEntries(array: JSONArray): List<AzurPilotLogEntry> =
        List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@List null
            AzurPilotLogEntry(
                id = item.optLong("id"),
                level = item.optString("level", "INFO"),
                text = item.optString("text"),
            )
        }.filterNotNull()

    private fun applyPreview(data: JSONObject?) {
        if (data == null) return
        val instance = data.optString("instance")
        if (instance.isNotEmpty() && instance != _selectedInstance.value) return
        val raw = data.optText("image")
        val capturedAt = data.optText("capturedAt")
        val runId = data.optText("runId")
        if (raw == null) {
            _preview.value = AzurPilotPreview(capturedAt, runId, null)
            return
        }
        scope.launch(AppDispatchers.Default) {
            val bitmap = decodeDataUri(raw)
            _preview.value = AzurPilotPreview(capturedAt, runId, bitmap)
        }
    }

    private fun decodeDataUri(value: String): android.graphics.Bitmap? = runCatching {
        val payload = value.substringAfter("base64,", value)
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    fun dismissMessage() {
        _messages.value = null
    }

    private fun report(text: String) {
        _messages.value = text
    }

    private suspend fun loadLogsLocked(instance: String, after: Long) {
        val reply = gateway.call(
            "logs.get",
            JSONObject().put("instance", instance).put("after", after),
        )
        if (reply is AzurPilotGateway.Reply.Ok) applyLogs(reply.obj)
    }

    // ── 轮询 ─────────────────────────────────────────────────────────────

    private suspend fun refreshLocked() {
        if (!gateway.connected.value) return
        ensureSchema()
        // 运行时版本与实例无关，设置页没进过更新页也要能显示
        if (_updater.value == null) refreshUpdater()
        // 同理：部署设置卡在用「共 N 组」当副标题，不先拉一次就永远停在「正在读取…」
        if (_deploy.value == null) loadDeploySettings()
        if (_instances.value.isEmpty()) {
            // 协议里这是个裸数组，不是 {"instances": [...]}
            (gateway.call("instances.list") as? AzurPilotGateway.Reply.Ok)?.let { reply ->
                _instances.value = parseInstances(reply.array)
            }
        }
        // 订阅可能因重连丢失：每次刷新补订一次（相同订阅会被 syncSubscription 短路）
        syncSubscription()
        val instance = _selectedInstance.value ?: return
        (gateway.call("overview.get", JSONObject().put("instance", instance)) as? AzurPilotGateway.Reply.Ok)
            ?.let { applyOverview(it.obj) }
        loadStartup(instance)
    }

    private suspend fun loadStartup(instance: String) {
        (gateway.call("startup.get", JSONObject().put("instance", instance)) as? AzurPilotGateway.Reply.Ok)
            ?.let { reply ->
                _startup.value = AzurPilotStartup(
                    enabled = reply.obj.optBoolean("enabled"),
                    remember = reply.obj.optBoolean("remember"),
                    run = reply.obj.optJSONArray("run")?.let { array ->
                        List(array.length()) { array.optString(it) }
                    }.orEmpty(),
                )
            }
    }

    // ── 运行控制 ─────────────────────────────────────────────────────────

    /** 启动/停止调度器；成功与否都用 [messages] 报出来 */
    fun setSchedulerRunning(running: Boolean) {
        val instance = _selectedInstance.value ?: return
        if (_schedulerBusy.value) return
        _schedulerBusy.value = true
        scope.launch(AppDispatchers.IO) {
            // 启动前等编辑队列落盘：调度器读的是磁盘上的配置，手上没保存的改动会白改
            if (running) configEditor.settle()
            val method = if (running) "scheduler.start" else "scheduler.stop"
            when (val reply = gateway.call(method, JSONObject().put("instance", instance))) {
                is AzurPilotGateway.Reply.Ok -> applyOverview(reply.obj)
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
            _schedulerBusy.value = false
        }
    }

    fun runTask(task: String) {
        val instance = _selectedInstance.value ?: return
        scope.launch(AppDispatchers.IO) {
            val params = JSONObject().put("instance", instance).put("task", task)
            when (val reply = gateway.call("tasks.run", params)) {
                is AzurPilotGateway.Reply.Ok -> applyOverview(reply.obj)
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    fun setStartup(enabled: Boolean? = null, remember: Boolean? = null) {
        val instance = _selectedInstance.value ?: return
        scope.launch(AppDispatchers.IO) {
            val params = JSONObject().put("instance", instance)
            enabled?.let { params.put("enabled", it) }
            remember?.let { params.put("remember", it) }
            val reply = gateway.call("startup.set", params)
            when (reply) {
                is AzurPilotGateway.Reply.Ok -> loadStartup(instance)
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    // ── 实例管理 ─────────────────────────────────────────────────────────

    fun createInstance(
        name: String,
        source: String? = null,
        importFile: String? = null,
        onCreated: (String) -> Unit = {},
    ) {
        scope.launch(AppDispatchers.IO) {
            val params = JSONObject().put("name", name)
            source?.let { params.put("source", it) }
            importFile?.let { params.put("import_file", it) }
            when (val reply = gateway.call("instances.create", params)) {
                is AzurPilotGateway.Reply.Ok -> {
                    val created = reply.obj.optString("instance", name)
                    selectInstance(created)
                    onCreated(created)
                }

                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    /** 删除要带 revision：网关拿它挡住「读到旧快照后删掉别人刚改的配置」 */
    fun deleteInstance(name: String) {
        scope.launch(AppDispatchers.IO) {
            val config = gateway.call("config.get", JSONObject().put("instance", name))
            if (config !is AzurPilotGateway.Reply.Ok) {
                if (config is AzurPilotGateway.Reply.Err) report(config.message)
                return@launch
            }
            val revision = config.obj.optString("revision")
            val params = JSONObject().put("instance", name).put("revision", revision)
            when (val reply = gateway.call("instances.delete", params)) {
                is AzurPilotGateway.Reply.Ok -> {
                    val remaining = _instances.value.filterNot { it.name == name }
                    _instances.value = remaining
                    if (_selectedInstance.value == name) selectInstance(remaining.firstOrNull()?.name)
                }

                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    suspend fun importableFiles(): List<AzurPilotImportable> =
        when (val reply = gateway.call("instances.importable")) {
            // 同样是裸数组
            is AzurPilotGateway.Reply.Ok -> reply.array?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    AzurPilotImportable(item.optString("name"), item.optDouble("modified"))
                }.filterNotNull()
            }.orEmpty()

            else -> emptyList()
        }

    fun importConfig(name: String, content: String, onImported: (String) -> Unit = {}) {
        scope.launch(AppDispatchers.IO) {
            val params = JSONObject().put("name", name).put("content", content)
            when (val reply = gateway.call("instances.importConfig", params)) {
                is AzurPilotGateway.Reply.Ok -> onImported(reply.obj.optString("name", name))
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    /** 导出用：拿一份完整实例列表（含 revision 之外的全部内容） */
    suspend fun exportConfig(instance: String): AzurPilotConfig? =
        (gateway.call("config.get", JSONObject().put("instance", instance)) as? AzurPilotGateway.Reply.Ok)
            ?.obj?.let(::parseConfig)

    // ── 配置 ─────────────────────────────────────────────────────────────

    suspend fun loadConfig(instance: String): AzurPilotConfig? =
        (gateway.call("config.get", JSONObject().put("instance", instance)) as? AzurPilotGateway.Reply.Ok)
            ?.obj?.let(::parseConfig)

    private fun parseConfig(result: JSONObject): AzurPilotConfig = AzurPilotConfig(
        instance = result.optString("instance"),
        revision = result.optString("revision"),
        values = result.optJSONObject("values")?.toConfigValues().orEmpty(),
    )

    /** 商店高级模式的脚本校验；返回诊断而不是抛错，编辑器要把行列标出来 */
    suspend fun validateShopStrategy(instance: String, task: String, script: String): AzurPilotShopValidation? {
        val params = JSONObject().put("instance", instance).put("task", task).put("script", script)
        val reply = gateway.call("shop_strategy.validate", params)
        if (reply !is AzurPilotGateway.Reply.Ok) return null
        val diagnostics = reply.obj.optJSONArray("diagnostics")?.let { array ->
            List(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@List null
                AzurPilotDiagnostic(
                    code = item.optString("code"),
                    message = item.optString("message"),
                    line = item.opt("line")?.takeIf { it != JSONObject.NULL }?.let { (it as Number).toInt() },
                    column = item.opt("column")?.takeIf { it != JSONObject.NULL }?.let { (it as Number).toInt() },
                )
            }.filterNotNull()
        }.orEmpty()
        return AzurPilotShopValidation(reply.obj.optBoolean("valid"), diagnostics)
    }

    // ── 统计 ─────────────────────────────────────────────────────────────

    suspend fun statisticsReport(query: AzurPilotStatsQuery): Result<AzurPilotStatisticsReport> {
        val instance = _selectedInstance.value
            ?: return Result.failure(AzurPilotException("NOT_FOUND", "未选择实例"))
        val params = JSONObject()
            .put("instance", instance)
            .put("category", query.category.key)
            .put("days", query.days)
            .put("period", query.period)
            .put("series", query.series)
            .put("scope", query.scope)
        query.month?.let { params.put("month", it) }
        query.task?.let { params.put("task", it) }
        val reply = gateway.call("statistics.report", params)
        if (reply !is AzurPilotGateway.Reply.Ok) return reply.asFailure()
        val data = reply.obj
        return Result.success(
            AzurPilotStatisticsReport(
            instance = data.optString("instance"),
            category = data.optString("category"),
            month = data.optString("month"),
            metrics = data.optJSONArray("metrics")?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    AzurPilotMetric(
                        label = item.optString("label"),
                        value = (item.opt("value") as? Number)?.toDouble(),
                        unit = item.optString("unit"),
                        icon = item.optText("icon"),
                    )
                }.filterNotNull()
            }.orEmpty(),
            series = data.optJSONArray("series")?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    AzurPilotStatSeries(
                        key = item.optString("key"),
                        label = item.optString("label"),
                        points = item.optJSONArray("points")?.let { points ->
                            List(points.length()) { p ->
                                val point = points.optJSONObject(p) ?: return@List null
                                AzurPilotStatPoint(
                                    time = point.optString("time"),
                                    value = (point.opt("value") as? Number)?.toDouble() ?: 0.0,
                                    source = point.optText("source"),
                                )
                            }.filterNotNull()
                        }.orEmpty(),
                    )
                }.filterNotNull()
            }.orEmpty(),
            tables = data.optJSONArray("tables")?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    val sort = item.optJSONObject("defaultSort")
                    AzurPilotStatTable(
                        title = item.optString("title"),
                        columns = item.optJSONArray("columns")?.let { c ->
                            List(c.length()) { c.optString(it) }
                        }.orEmpty(),
                        rows = item.optJSONArray("rows")?.let { rows ->
                            List(rows.length()) { r ->
                                rows.optJSONArray(r)?.toValueList().orEmpty()
                            }
                        }.orEmpty(),
                        note = item.optString("note"),
                        defaultSortIndex = sort?.optInt("index"),
                        defaultSortDescending = sort?.optBoolean("descending") ?: false,
                    )
                }.filterNotNull()
            }.orEmpty(),
            notes = data.optJSONArray("notes")?.let { array ->
                List(array.length()) { array.optString(it) }
            }.orEmpty(),
            taskOptions = data.optJSONArray("taskOptions")?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    AzurPilotTaskOption(
                        key = item.optString("key"),
                        label = item.optString("label"),
                        count = item.optInt("count"),
                    )
                }.filterNotNull()
            }.orEmpty(),
        )
        )
    }

    suspend fun statisticsResources(resource: String, days: Int): Result<AzurPilotStatistics> {
        val instance = _selectedInstance.value
            ?: return Result.failure(AzurPilotException("NOT_FOUND", "未选择实例"))
        val params = JSONObject().put("instance", instance).put("resource", resource).put("days", days)
        val reply = gateway.call("statistics.resources", params)
        if (reply !is AzurPilotGateway.Reply.Ok) return reply.asFailure()
        val data = reply.obj
        return Result.success(
            AzurPilotStatistics(
                instance = data.optString("instance"),
                resource = data.optString("resource"),
                points = data.optJSONArray("points")?.let { points ->
                    List(points.length()) { index ->
                        val point = points.optJSONObject(index) ?: return@List null
                        AzurPilotStatPoint(
                            time = point.optString("time"),
                            value = (point.opt("value") as? Number)?.toDouble() ?: 0.0,
                            source = null,
                        )
                    }.filterNotNull()
                }.orEmpty(),
                truncated = data.optBoolean("truncated"),
            )
        )
    }

    fun refreshLoot() {
        val instance = _selectedInstance.value ?: return
        scope.launch(AppDispatchers.IO) {
            when (val reply = gateway.call("statistics.refreshLoot", JSONObject().put("instance", instance))) {
                is AzurPilotGateway.Reply.Ok -> _statisticsTick.value = System.currentTimeMillis()
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    // ── 部署设置 ─────────────────────────────────────────────────────────

    suspend fun loadDeploySettings(): AzurPilotDeploySettings? {
        val reply = gateway.call("settings.get")
        if (reply !is AzurPilotGateway.Reply.Ok) return null
        val data = reply.obj
        val groups = data.optJSONArray("groups")?.let { array ->
            List(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@List null
                AzurPilotDeployGroup(
                    key = item.optString("key"),
                    label = item.optString("label"),
                    fields = item.optJSONArray("fields")?.let { fields ->
                        List(fields.length()) { f ->
                            val field = fields.optJSONObject(f) ?: return@List null
                            AzurPilotDeployField(
                                key = field.optString("key"),
                                type = field.optString("type"),
                                label = field.optString("label"),
                                help = field.optString("help"),
                                value = field.opt("value").asApValue(),
                                options = field.optJSONArray("options")?.toValueList().orEmpty(),
                            )
                        }.filterNotNull()
                    }.orEmpty(),
                )
            }.filterNotNull()
        }.orEmpty()
        val remote = data.optJSONObject("remote")?.let {
            AzurPilotRemoteAccess(
                enabled = it.optBoolean("enabled"),
                state = it.optString("state"),
                address = it.optText("address").orEmpty(),
                error = it.optText("error").orEmpty(),
            )
        }
        return AzurPilotDeploySettings(
            groups = groups,
            notice = data.optString("notice"),
            demo = data.optBoolean("demo"),
            remote = remote,
        ).also { _deploy.value = it }
    }

    /** 保存一组部署设置；空密码表示「不改」，由网关侧丢弃 */
    fun patchDeploySettings(values: Map<String, ApValue>, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch(AppDispatchers.IO) {
            val payload = JSONObject()
            values.forEach { (key, value) -> payload.put(key, value.toJsonCompatible()) }
            val params = JSONObject().put("values", payload)
            when (val reply = gateway.call("settings.patch", params)) {
                is AzurPilotGateway.Reply.Ok -> {
                    loadDeploySettings()
                    onDone(true, null)
                }

                is AzurPilotGateway.Reply.Err -> {
                    report(reply.message)
                    onDone(false, reply.message)
                }

                AzurPilotGateway.Reply.Offline -> onDone(false, null)
            }
        }
    }

    // ── 更新器 ───────────────────────────────────────────────────────────

    /** 更新器是全局的，跟实例无关；页面在自己可见时起停这个轮询 */
    fun startUpdaterPolling() {
        if (updaterJob?.isActive == true) return
        updaterJob = scope.launch(AppDispatchers.IO) {
            while (true) {
                refreshUpdater()
                delay(UPDATER_POLL_MS)
            }
        }
    }

    fun stopUpdaterPolling() {
        updaterJob?.cancel()
        updaterJob = null
    }

    suspend fun refreshUpdater() {
        val reply = gateway.call("updater.status") as? AzurPilotGateway.Reply.Ok ?: return
        val data = reply.obj
        _updater.value = AzurPilotUpdateStatus(
            state = data.optString("state"),
            localHead = data.optText("localHead"),
            upstreamHead = data.optText("upstreamHead"),
            branch = data.optString("branch"),
            ahead = data.optInt("ahead"),
            behind = data.optInt("behind"),
            available = data.optBoolean("available"),
            busy = data.optBoolean("busy"),
            canApply = data.optBoolean("canApply"),
            canCancel = data.optBoolean("canCancel"),
            error = data.optString("error"),
            managedByAndroid = data.optBoolean("managedByAndroid"),
        )
    }

    suspend fun updaterCommits(offset: Int, limit: Int): AzurPilotCommitHistory? {
        val params = JSONObject().put("offset", offset).put("limit", limit)
        val reply = gateway.call("updater.commits", params)
        if (reply !is AzurPilotGateway.Reply.Ok) return null
        val data = reply.obj
        return AzurPilotCommitHistory(
            entries = data.optJSONArray("entries")?.let { array ->
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    AzurPilotCommit(
                        sha = item.optString("sha"),
                        author = item.optString("author"),
                        date = item.optString("date"),
                        message = item.optString("message"),
                    )
                }.filterNotNull()
            }.orEmpty(),
            total = data.optInt("total"),
            hasMore = data.optBoolean("hasMore"),
            localHead = data.optText("localHead"),
            upstreamHead = data.optText("upstreamHead"),
        )
    }

    fun updaterAction(action: String) {
        scope.launch(AppDispatchers.IO) {
            when (val reply = gateway.call("updater.$action")) {
                is AzurPilotGateway.Reply.Ok -> refreshUpdater()
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    // ── 公告 ─────────────────────────────────────────────────────────────

    fun refreshAnnouncement(force: Boolean = false) {
        scope.launch(AppDispatchers.IO) {
            val reply = gateway.call("announcement.get", JSONObject().put("force", force))
            if (reply !is AzurPilotGateway.Reply.Ok) return@launch
            val data = reply.obj
            val id = data.optString("announcementId")
            if (id.isEmpty()) return@launch
            _announcement.value = AzurPilotAnnouncement(
                id = id,
                title = data.optString("title"),
                content = data.optString("content"),
                url = data.optText("url"),
            )
        }
    }

    // ── 指挥喵评分 ───────────────────────────────────────────────────────

    suspend fun loadMeowfficerReport(limit: Int = 100): Result<AzurPilotMeowfficerReport> {
        val instance = _selectedInstance.value ?: return Result.failure(AzurPilotException("NOT_FOUND", "未选择实例"))
        val params = JSONObject().put("instance", instance).put("limit", limit)
        return when (val reply = gateway.call("meowfficer.scoreReport", params)) {
            is AzurPilotGateway.Reply.Ok -> {
                val data = reply.obj
                val cats = data.optJSONArray("cats")?.let { array ->
                    List(array.length()) { index ->
                        array.optJSONObject(index)?.let(::parseCat)
                    }.filterNotNull()
                }.orEmpty()
                val report = AzurPilotMeowfficerReport(
                    generatedAt = data.optString("generatedAt"),
                    count = data.optInt("count"),
                    cats = cats,
                )
                _meowfficer.value = report
                Result.success(report)
            }

            is AzurPilotGateway.Reply.Err ->
                Result.failure(AzurPilotException(reply.code, reply.message))

            AzurPilotGateway.Reply.Offline ->
                Result.failure(AzurPilotException("DISCONNECTED", "未连接到 AzurPilot"))
        }
    }

    private fun parseCat(item: JSONObject): AzurPilotCat {
        val advice = item.optJSONObject("advice")
        val primary = item.optJSONArray("rubrics")?.let { rubrics ->
            List(rubrics.length()) { rubrics.optJSONObject(it) }.filterNotNull()
                .firstOrNull { it.optBoolean("primary") } ?: run {
                List(rubrics.length()) { rubrics.optJSONObject(it) }.filterNotNull().firstOrNull()
            }
        }
        return AzurPilotCat(
            cat = item.optString("cat"),
            level = (item.opt("level") as? Number)?.toInt(),
            maxed = item.optBoolean("maxed"),
            fixed = item.optBoolean("fixed"),
            source = item.optText("source"),
            note = item.optText("note"),
            talents = item.optJSONArray("talents")?.let { array ->
                List(array.length()) { index ->
                    val talent = array.optJSONObject(index) ?: return@List null
                    AzurPilotTalent(
                        name = talent.optString("name"),
                        level = (talent.opt("level") as? Number)?.toInt(),
                        inferred = talent.optBoolean("inferred"),
                    )
                }.filterNotNull()
            }.orEmpty(),
            score = (primary?.opt("score") as? Number)?.toDouble(),
            tier = primary?.optText("tier"),
            verdict = advice?.optText("verdict"),
            headline = advice?.optText("headline"),
            reason = primary?.optText("label"),
            adviceReason = advice?.optText("reason"),
            costText = advice?.optText("costText"),
            pointsSpent = (advice?.opt("pointsSpent") as? Number)?.toInt()
                ?: (item.opt("pointsSpent") as? Number)?.toInt(),
            targets = advice?.optJSONArray("targets")?.let { array ->
                List(array.length()) { array.optString(it) }
            }.orEmpty(),
            tags = item.optJSONArray("tags")?.let { array ->
                List(array.length()) { array.optString(it) }
            }.orEmpty(),
        )
    }

    fun clearMeowfficerReport() {
        val instance = _selectedInstance.value ?: return
        scope.launch(AppDispatchers.IO) {
            when (val reply = gateway.call(
                "meowfficer.clearReport",
                JSONObject().put("instance", instance),
            )) {
                is AzurPilotGateway.Reply.Ok -> _meowfficer.value = null
                is AzurPilotGateway.Reply.Err -> report(reply.message)
                AzurPilotGateway.Reply.Offline -> Unit
            }
        }
    }

    // ── 屏保/后台让位 ────────────────────────────────────────────────────

    /** 界面可见性变化：离开时把截图订阅摘掉，回来再补上 */
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (!gateway.connected.value) return
        scope.launch(AppDispatchers.IO) {
            lastSubscription = null
            syncSubscription(force = true)
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
        const val UPDATER_POLL_MS = 3_000L
        const val LOG_LIMIT = 1_000
        const val TOPIC_INSTANCES = "instances"
        const val TOPIC_PREVIEW = "preview"
        val TOPICS = listOf("instances", "overview", "logs", TOPIC_PREVIEW)
    }
}

/** 少量要跨重启留存的界面选择（选中实例） */
interface AzurPilotPreferenceStore {
    var selectedInstance: String?
}

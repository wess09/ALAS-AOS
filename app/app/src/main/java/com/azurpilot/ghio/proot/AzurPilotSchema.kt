package com.azurpilot.ghio.proot

import org.json.JSONObject

/** 界面上一个参数该怎么渲染——取值全部来自网关的 `args.json`，App 侧不硬编码任何一项 */
data class AzurPilotField(
    /** input / checkbox / select / datetime / textarea / multiselect / storage / stored / state / lock / task_priority */
    val type: String,
    /** 默认值；网关也用它决定这个参数能接受什么类型 */
    val value: ApValue,
    /** select / multiselect 的候选；非数组（历史上有一个 dict）按「没有候选」处理 */
    val option: List<ApValue>?,
    /** "datetime" | [min,max] | 正则字符串 */
    val validate: ApValue,
    /** hide / disabled / readonly / display */
    val display: String?,
    /** yaml / restricted_lua / text */
    val mode: String?,
    val valuetype: String?,
    val preserveEmpty: Boolean,
) {
    /** 网关会拒绝写入的类型：纯展示位 */
    val readOnly: Boolean
        get() = display == "disabled" || display == "readonly" ||
            type == "storage" || type == "stored" || type == "state" || type == "lock"

    val hidden: Boolean get() = display == "hide"

    /** 值是不是布尔：默认值是 bool，或类型标了 checkbox */
    val booleanish: Boolean get() = value is Boolean || type == "checkbox"

    val numeric: Boolean get() = value is Number

    /** `validate: [min, max]` 的区间；不是区间约束时为 null */
    val range: Pair<Double, Double>?
        get() {
            val list = validate as? List<*> ?: return null
            if (list.size != 2) return null
            val min = (list[0] as? Number)?.toDouble() ?: return null
            val max = (list[1] as? Number)?.toDouble() ?: return null
            return min to max
        }

    /** 正则约束；没有正则时为 null */
    val pattern: String? get() = validate as? String

    /** 网关只允许把 storage 清成 `{}`，所以这个按钮的语义是「清空」而不是「随便写」 */
    val clearableStorage: Boolean get() = type == "storage" && !hidden

    companion object {
        fun from(json: JSONObject): AzurPilotField = AzurPilotField(
            type = json.optString("type", "input"),
            value = json.opt("value").asApValue(),
            // org.json 的 JSONArray 不实现 kotlin List，`as? List<*>` 会永远失败——
            // 必须走 optJSONArray，否则所有 select/multiselect 都丢了候选，退化成裸值文本框
            option = json.optJSONArray("option")?.toValueList()?.takeIf { it.isNotEmpty() },
            validate = json.opt("validate").asApValue(),
            display = json.optString("display").ifEmpty { null },
            mode = json.optString("mode").ifEmpty { null },
            valuetype = json.optString("valuetype").ifEmpty { null },
            preserveEmpty = json.optBoolean("preserve_empty"),
        )
    }
}

/** 侧边栏菜单的一组；[tasks] 是组内的任务名 */
data class AzurPilotMenuGroup(
    val key: String,
    val menu: String,
    /** setting = 配置页，tool = 工具页（多一个「运行工具」入口和日志面板） */
    val page: String,
    val tasks: List<String>,
) {
    val isTool: Boolean get() = page == "tool"
}

/**
 * 网关下发的界面骨架：菜单、参数定义、翻译
 *
 * 这是「原生复刻 WebUI」能成立的前提——WebUI 的前端本身也是拿这份数据现渲染的，
 * 所以原生侧照同一份定义渲染，就不会出现「漏了某个任务」这种差异。
 */
class AzurPilotSchema(
    val menu: List<AzurPilotMenuGroup>,
    /** 任务 → 分组 → 参数 → 定义 */
    val args: Map<String, Map<String, Map<String, AzurPilotField>>>,
    private val translations: Map<String, ApValue>,
) {

    /** 任务是否可单独运行（`tasks.run` 的合法名字集合） */
    val runnableTasks: Set<String> = menu.flatMapTo(LinkedHashSet()) { it.tasks }

    val toolTasks: Set<String> = menu.filter { it.isTool }.flatMapTo(LinkedHashSet()) { it.tasks }

    fun groupsOf(task: String): Map<String, Map<String, AzurPilotField>> = args[task].orEmpty()

    fun field(task: String, group: String, argument: String): AzurPilotField? =
        args[task]?.get(group)?.get(argument)

    /**
     * 取翻译：`Emulator.PackageName.name` / `Alas.Emulator.PackageName`
     *
     * 键是**分组**开头的扁平结构（`Emulator.*` 而不是 `Alas.Emulator.*`），与 WebUI 一致；
     * 取不到就退回键名本身，宁可显示生键也不要空标签。
     *
     * 查表结果进缓存：滚动配置页时每个可见字段每帧都要查 2~3 次，缓存把 split 与
     * 逐级 Map 查找的分配全省掉。schema 换语言时整个实例被替换，缓存随之失效。
     */
    private val translateCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun translate(path: String): String {
        translateCache[path]?.let { return it }
        var node: ApValue = translations
        for (segment in path.split('.')) {
            node = (node as? Map<*, *>)?.get(segment) ?: return path
        }
        val text = (node as? String)?.takeIf { it.isNotEmpty() } ?: path
        translateCache[path] = text
        return text
    }

    /** 有翻译就显示翻译，没有就显示原键——组标题、任务名、选项名都走它 */
    fun translateOr(path: String, fallback: String): String {
        val text = translate(path)
        return if (text == path) fallback else text
    }

    /** 参数的显示名与说明 */
    fun fieldLabel(group: String, argument: String): String =
        translateOr("$group.$argument.name", argument)

    fun fieldHelp(group: String, argument: String): String =
        translateOr("$group.$argument.help", "")

    /** 选项的显示名：翻译键就是选项值本身（`Scheduler.Enable.True`） */
    fun optionLabel(group: String, argument: String, value: ApValue): String {
        val key = when (value) {
            is Boolean -> value.toString()
            null -> ""
            else -> value.toString()
        }
        val path = if (key.isEmpty()) null else "$group.$argument.$key"
        val text = path?.let { translate(it) }
        return text?.takeIf { it != path } ?: key.ifEmpty { "—" }
    }

    fun groupTitle(group: String): String = translateOr("$group._info.name", group)

    fun taskTitle(task: String): String = translateOr("Task.$task.name", task)

    fun menuTitle(menu: String): String = translateOr("Menu.$menu.name", menu)

    /** 部署设置（`Gui.DeploySetting.*`）不在 schema 里，单独走这个取词器 */
    fun translateGui(path: String): String = translate(path)

    companion object {
        fun from(json: JSONObject): AzurPilotSchema {
            val menu = ArrayList<AzurPilotMenuGroup>()
            json.optJSONObject("menu")?.let { menuJson ->
                menuJson.keys().forEach { key ->
                    val entry = menuJson.optJSONObject(key) ?: return@forEach
                    val tasks = entry.optJSONArray("tasks")
                    menu += AzurPilotMenuGroup(
                        key = key,
                        menu = entry.optString("menu", "collapse"),
                        page = entry.optString("page", "setting"),
                        tasks = List(tasks?.length() ?: 0) { tasks!!.optString(it) },
                    )
                }
            }
            val args = LinkedHashMap<String, Map<String, Map<String, AzurPilotField>>>()
            json.optJSONObject("args")?.let { argsJson ->
                argsJson.keys().forEach { task ->
                    val groupsJson = argsJson.optJSONObject(task) ?: return@forEach
                    val groups = LinkedHashMap<String, Map<String, AzurPilotField>>()
                    groupsJson.keys().forEach { group ->
                        val fieldsJson = groupsJson.optJSONObject(group) ?: return@forEach
                        val fields = LinkedHashMap<String, AzurPilotField>()
                        fieldsJson.keys().forEach { argument ->
                            fieldsJson.optJSONObject(argument)?.let { fields[argument] = AzurPilotField.from(it) }
                        }
                        groups[group] = fields
                    }
                    args[task] = groups
                }
            }
            val translations = json.optJSONObject("translations")?.toValueMap() ?: emptyMap()
            return AzurPilotSchema(menu, args, translations)
        }
    }
}

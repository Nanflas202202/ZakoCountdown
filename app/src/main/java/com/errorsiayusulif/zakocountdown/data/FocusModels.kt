// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/FocusModels.kt
package com.errorsiayusulif.zakocountdown.data

/**
 * 防沉迷（Focus Guard）的数据模型。
 *
 * 由 anti-addiction（MIT, © 2025 wd）的 `CustomApp` / `RelaxManager` /
 * `LeisureTimeManager` 移植改造而来，本工程做了三处**刻意的简化**：
 *
 *  1. **去掉全部云端依赖**。原项目有 `DeviceInfoReporter`（上报 androidId/品牌/型号等
 *     设备指纹到自有服务器）与 `AppConfigManager`（用 androidId 当 devid 去拉服务端
 *     下发的模型配置）。这里一行都没有 —— 配置只存本地。
 *  2. **去掉 MMKV**，统一用 SharedPreferences + Gson，与本工程其余部分保持一致。
 *  3. **去掉多档配额与冷却阶梯**。原版把「宽松次数 / 严格时长 / 宽松时长 / 冷却」
 *     拆成十来个互相牵制的键，可配置性很高但理解成本也高。
 *     这里收敛为「每个应用：每天 N 次免答题 + 每次免答题 M 分钟」两个参数，
 *     把「防沉迷」该有的摩擦保住，同时让设置页能一眼看懂。
 */

/** 单次「免答题」能放开多久的候选值（分钟）。 */
val FOCUS_LEISURE_MINUTE_OPTIONS = listOf(1, 2, 3, 5, 10)

/** 每个应用每天免答题次数上限的候选值。 */
val FOCUS_DAILY_PASS_OPTIONS = listOf(0, 1, 2, 3)

/**
 * 遮罩向上/向下让出多少屏幕高度（百分比）的候选值。
 *
 * 这不是「装饰性参数」，而是功能性的：
 *   · 让出**顶部** → 保住搜索栏、扫码、消息入口（这些是「工具」而非「沉浸流」）
 *   · 让出**底部** → 保住底部导航栏，用户可以继续去别的功能区
 * 遮挡的目标是「无意识刷推荐流」，不是把整个应用封死。
 *
 * 0 表示不留边距（遮满）。
 */
val FOCUS_MASK_OFFSET_OPTIONS = listOf(0, 5, 8, 10, 12, 15, 20)

/**
 * 遮罩顶部让出的默认比例。
 *
 * 取 10% 而不是 0：主流信息流应用的顶部搜索栏大约就占这个高度，
 * 默认留出来才能做到「打开后立刻能用搜索/扫码」，而不是一上来就被整屏挡住。
 * 这也让首次使用的观感更接近「挡住推荐流」而不是「封死整个应用」。
 */
const val DEFAULT_MASK_TOP_PERCENT = 10

/**
 * 一个受管制的应用。
 *
 * @param packageName      目标应用包名
 * @param appName          展示名（尽量用系统里的真实应用名）
 * @param keywords         触发遮挡的关键词。匹配规则见
 *                         [com.errorsiayusulif.zakocountdown.services.FocusGuardEngine]：
 *                         页面节点文本若被任一关键词**包含**，就判定命中。
 *                         例如关键词「推荐」能命中页面上写着「推荐」的入口。
 * @param globalBlock      true = 进入该应用即遮挡，不做关键词匹配
 *                         （用于「整个应用都不想碰」的场景）
 * @param dailyPassLimit   每天可用的免答题次数（0 = 不给免答题机会）
 * @param leisureMinutes   每次免答题放开多少分钟
 * @param enabled          该条规则是否生效
 * @param maskTopPercent   遮罩顶部让出的屏幕高度百分比（默认 10 = 从屏幕高度的 10% 处开始遮）。
 *                         让出顶部是为了保住搜索栏 / 扫码 / 消息入口 ——
 *                         这些属于「工具」而不是「沉浸流」，遮住它们只会逼用户关掉整个功能。
 *                         默认取 10% 而不是 0：实测主流信息流应用的顶部搜索栏
 *                         大约就占这个高度，默认留出来才能「一打开就能用别的功能」。
 * @param maskBottomPercent 遮罩底部让出的屏幕高度百分比（0 = 遮到屏幕底端）。
 *                         让出底部是为了保住底部导航栏；默认 0，
 *                         因为多数信息流应用底部就是内容区，留白反而露出推荐流。
 */
data class FocusAppRule(
    val packageName: String,
    val appName: String,
    val keywords: List<String> = emptyList(),
    val globalBlock: Boolean = false,
    val dailyPassLimit: Int = 1,
    val leisureMinutes: Int = 2,
    val enabled: Boolean = true,
    val maskTopPercent: Int = DEFAULT_MASK_TOP_PERCENT,
    val maskBottomPercent: Int = 0
) {
    /** 规则是否具备生效条件：必须有关键词，或者开了整应用遮挡。 */
    val isUsable: Boolean
        get() = enabled && (globalBlock || keywords.isNotEmpty())

    /** 关键词数组转成便于展示的一行文本。 */
    fun keywordsText(): String = keywords.joinToString("，")

    /**
     * 遮罩实际覆盖的屏幕高度比例（0f~1f）。
     *
     * 上下让出的比例之和被钳制在 80% —— 否则用户可以设成「只遮中间一条」，
     * 那就完全失去遮挡意义了（脚下一滑就又能刷）。留 20% 是下限。
     */
    val maskCoverageRatio: Float
        get() {
            val top = maskTopPercent.coerceIn(0, 80)
            val bottom = maskBottomPercent.coerceIn(0, 80)
            val kept = (top + bottom).coerceAtMost(80)
            return (100 - kept) / 100f
        }
}

/**
 * 防沉迷总配置。
 *
 * 存成一份 JSON 放在独立的 prefs 文件里（见 [FocusGuardStore]），
 * 不混进 `zako_prefs`：规则列表会随用户添加的应用增长，混进去会让设置备份变大。
 */
data class FocusGuardConfig(
    /** 总开关。关闭时无障碍服务完全不介入遮挡逻辑。 */
    val enabled: Boolean = false,

    /** 受管制的应用规则。 */
    val rules: List<FocusAppRule> = emptyList(),

    /**
     * 免答题放开的剩余截止时间（毫秒时间戳），按包名记录。
     * 到期后重新开始遮挡。
     */
    val leisureUntil: Map<String, Long> = emptyMap(),

    /**
     * 当天已用掉的免答题次数，按包名记录。
     * 跨天由 [FocusGuardStore] 在读取时依据 [passDate] 重置。
     */
    val passUsed: Map<String, Int> = emptyMap(),

    /** [passUsed] 对应的日期（yyyy-MM-dd），用于判断是否需要跨天重置。 */
    val passDate: String = ""
) {
    fun ruleFor(packageName: String): FocusAppRule? =
        rules.firstOrNull { it.packageName == packageName }
}

/**
 * 预置的受管制应用模板。
 *
 * 包名与关键词沿用 anti-addiction 的默认值 —— 这些是它实测有效的组合，
 * 例如「抖音」的推荐流入口写着「推荐」，B 站的首页入口写着「推荐」。
 * 用户也可以在设置里自行增删。
 */
object FocusGuardDefaults {

    val PRESETS: List<FocusAppRule> = listOf(
        FocusAppRule(
            packageName = "com.xingin.xhs",
            appName = "小红书",
            keywords = listOf("发现"),
            dailyPassLimit = 2
        ),
        FocusAppRule(
            packageName = "tv.danmaku.bili",
            appName = "哔哩哔哩",
            keywords = listOf("推荐"),
            dailyPassLimit = 2
        ),
        FocusAppRule(
            packageName = "com.ss.android.ugc.aweme",
            appName = "抖音",
            keywords = listOf("推荐", "精选", "热点"),
            dailyPassLimit = 1
        ),
        FocusAppRule(
            packageName = "com.zhihu.android",
            appName = "知乎",
            keywords = listOf("热榜"),
            dailyPassLimit = 1
        ),
        FocusAppRule(
            packageName = "com.eg.android.AlipayGphone",
            appName = "支付宝",
            keywords = listOf("行情", "持有"),
            dailyPassLimit = 1
        )
    )

    /**
     * 微信特殊：它的首页没有稳定可匹配的关键词（原项目里就是把整应用放过、
     * 只依赖其他入口），所以这里默认不预置，交由用户自行决定是否开启整应用遮挡。
     */
    const val WECHAT_PACKAGE = "com.tencent.mm"
}

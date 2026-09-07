package com.bradj.airshift.parser

/**
 * 把无分隔的连写姓名切成单人姓名，如“甲乙丙丁戊己” 这样的 6 字串切成 3 + 3。
 * 二组排班表的人员栏与班次行都这样连写；一组表以空格分隔，2–3 字的单名不经过这里。
 *
 * 规则：每个姓名 2–3 字（复姓 3–4 字），首字（复姓为首二字）必须在常见姓氏表内；
 * 有多种合法切分时取“姓氏罕见程度之和”最小的一种，因此“王兰成李琪”切成 王兰成 / 李琪，
 * 而不是 王兰 / 成李琪。切不开（含未知姓氏或非汉字）时整串原样返回，由上层按包含关系兜底。
 */
internal object ChineseNameSplitter {
    /** 单人姓名不会长过 4 字；不足这个长度的块本身就是一个名字。 */
    private const val MIN_SPLITTABLE_LENGTH = 4
    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 3
    private const val COMPOUND_SURNAME_LENGTH = 2
    private const val MAX_COMPOUND_NAME_LENGTH = 4

    /** 常见姓氏以外的百家姓：合法，但在歧义时让位于常见姓氏。 */
    private const val RARE_COST = 200
    private const val COMPOUND_COST = 150

    /** 按公开的人口排序取前 100 个常见姓氏，名次即代价；越常见越优先。 */
    private val COMMON_SURNAMES = listOf(
        '王', '李', '张', '刘', '陈', '杨', '黄', '赵', '吴', '周',
        '徐', '孙', '马', '朱', '胡', '郭', '何', '林', '高', '罗',
        '郑', '梁', '谢', '宋', '唐', '许', '韩', '冯', '邓', '曹',
        '彭', '曾', '肖', '田', '董', '潘', '袁', '蔡', '蒋', '余',
        '于', '杜', '叶', '程', '魏', '苏', '吕', '丁', '任', '卢',
        '姚', '沈', '钟', '姜', '崔', '谭', '陆', '范', '汪', '廖',
        '石', '金', '韦', '贾', '夏', '付', '方', '邹', '熊', '白',
        '孟', '秦', '邱', '侯', '江', '尹', '薛', '闵', '段', '雷',
        '龙', '黎', '史', '陶', '贺', '毛', '郝', '顾', '龚', '邵',
        '万', '覃', '武', '钱', '戴', '严', '欧', '莫', '孔', '向',
    )

    private val OTHER_SURNAMES = (
        "安敖巴柏班包鲍毕边卜岑柴昌常车成池迟储褚淳党狄刁窦樊房费丰封凤伏符傅甘郜戈耿宫巩勾苟辜古谷关管桂杭和洪花华滑桓惠霍姬嵇吉计纪季冀简焦靳荆井景居鞠康柯寇匡邝况赖蓝郎劳乐冷厉利励连廉练蔺凌柳隆娄楼芦鲁路栾骆麻买满茅梅米苗缪明牟母穆倪聂宁牛钮农庞裴皮平蒲濮浦戚祁齐强乔裘曲屈瞿全权冉饶荣阮芮萨赛桑沙单商尚佘申盛施时寿舒司松宿隋索汤滕铁佟童涂屠危卫温文闻翁邬巫伍郗奚席冼项萧辛邢幸宣荀闫阎颜晏燕易殷应尤游俞虞禹郁喻元岳云恽臧翟詹湛章招甄支诸竺祝庄卓宗祖左"
        ).toSet()

    private val COMPOUND_SURNAMES = setOf(
        "欧阳", "太史", "端木", "上官", "司马", "东方", "独孤", "南宫", "万俟", "闻人",
        "夏侯", "诸葛", "尉迟", "公羊", "赫连", "澹台", "皇甫", "宗政", "濮阳", "公冶",
        "太叔", "申屠", "公孙", "慕容", "仲孙", "钟离", "长孙", "宇文", "司徒", "鲜于",
        "司空", "闾丘", "令狐", "百里", "呼延", "东郭", "西门", "第五", "轩辕", "拓跋",
    )

    private val commonSurnameCost: Map<Char, Int> =
        COMMON_SURNAMES.withIndex().associate { (index, surname) -> surname to index + 1 }

    /**
     * 切分一个不含分隔符的姓名串。2–3 字或含非汉字的输入原样返回单元素列表；
     * 找不到全部合法的切分时同样原样返回。
     */
    fun split(raw: String): List<String> {
        val text = raw.trim()
        if (text.length < MIN_SPLITTABLE_LENGTH || !text.all(::isCjk)) return listOf(text)
        val length = text.length
        // best[i] = 前 i 个字的最小代价；cut[i] = 达到该代价时最后一个名字的起点。
        val best = IntArray(length + 1) { Int.MAX_VALUE }
        val cut = IntArray(length + 1) { -1 }
        best[0] = 0
        for (end in 1..length) {
            for (start in maxOf(0, end - MAX_COMPOUND_NAME_LENGTH) until end) {
                if (best[start] == Int.MAX_VALUE) continue
                val cost = nameCost(text, start, end) ?: continue
                val total = best[start] + cost
                if (total < best[end]) {
                    best[end] = total
                    cut[end] = start
                }
            }
        }
        if (best[length] == Int.MAX_VALUE) return listOf(text)
        val names = ArrayDeque<String>()
        var end = length
        while (end > 0) {
            val start = cut[end]
            names.addFirst(text.substring(start, end))
            end = start
        }
        return names.toList()
    }

    /** text[start, end) 作为一个姓名的代价；不合法返回 null。 */
    private fun nameCost(text: String, start: Int, end: Int): Int? {
        val size = end - start
        if (size >= MIN_NAME_LENGTH + COMPOUND_SURNAME_LENGTH - 1 && size <= MAX_COMPOUND_NAME_LENGTH &&
            text.substring(start, start + COMPOUND_SURNAME_LENGTH) in COMPOUND_SURNAMES
        ) {
            return COMPOUND_COST
        }
        if (size < MIN_NAME_LENGTH || size > MAX_NAME_LENGTH) return null
        val surname = text[start]
        return commonSurnameCost[surname] ?: if (surname in OTHER_SURNAMES) RARE_COST else null
    }

    private fun isCjk(character: Char): Boolean =
        Character.UnicodeScript.of(character.code) == Character.UnicodeScript.HAN
}

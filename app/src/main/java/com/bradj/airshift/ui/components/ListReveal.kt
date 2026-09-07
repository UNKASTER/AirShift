package com.bradj.airshift.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt

/*
 * 条在列表里就地展开时，展开出来的内容会伸到视口之下（最后一条尤其如此），用户还得再往下划一下。
 *
 * 这里不另起一支滚动动画去"追"它——那会和条自己的尺寸弹簧打架，读成两段动作。做法是逐帧跟随：
 * 每帧读上一帧的布局，条底边超出视口多少，就把列表推上多少（最多推到条顶离视口顶只剩一个条间距），
 * 条看起来是原地"顶上去"展开；折叠时把推上去的距离逐帧还回去，条落回原处。列表末尾的条折叠时
 * LazyColumn 本来就会把内容拉回填满视口，这时底边不动、这里什么也不做。
 *
 * 只滞后一帧、没有自己的曲线，所以跟随的速度就是条的弹簧的速度；系统"移除动画"时条一帧到位，列表也一帧到位。
 * 跟随期间用户拖动列表（UserInput 优先级）会让 scrollBy 抛出取消异常、协程结束：手指永远赢。
 */

/** 条的尺寸连续这么多帧没变就算弹簧已静止。点击后要到第二帧尺寸才开始变，所以要留出起步的几帧。 */
private const val SETTLED_FRAMES = 8

/** 无论如何最多跟随这么多帧（系统动画时长比例放大到 10 倍时也够）。 */
private const val MAX_FRAMES = 240

/** 一帧该推多少像素：条底边超出视口的部分，最多推到条顶离视口顶只剩一个条间距；条已完全可见时为 0。 */
internal fun revealPushPx(itemTop: Int, itemBottom: Int, viewportTop: Int, viewportBottom: Int, spacing: Int): Int =
    minOf(itemBottom - viewportBottom, itemTop - viewportTop - spacing).coerceAtLeast(0)

/** 跟随 [key] 这一条的展开，直到它的尺寸静止。返回一共推上去的像素数，折叠时交给 [followCollapse] 还回去。 */
suspend fun LazyListState.followExpansion(key: Any): Int {
    var pushed = 0
    val settle = SizeSettle()
    var frames = 0
    while (frames++ < MAX_FRAMES) {
        withFrameNanos { }
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key }
        if (item == null || settle.update(item.size)) break
        // 最后一条推到底部内边距之上——与用户自己滚到列表末尾时的位置一致；中间的条推到视口底边即可，
        // 再多推只会让下一条的顶边在内边距里露出一线。
        val isLast = item.index == info.totalItemsCount - 1
        val delta = revealPushPx(
            itemTop = item.offset,
            itemBottom = item.offset + item.size,
            viewportTop = info.viewportStartOffset,
            viewportBottom = info.viewportEndOffset - if (isLast) info.afterContentPadding else 0,
            spacing = info.mainAxisItemSpacing,
        )
        if (delta > 0) pushed += scrollBy(delta.toFloat()).roundToInt()
    }
    return pushed
}

/**
 * 跟随 [key] 这一条的折叠：条底边每上移一段就把列表拉回一段，最多还回 [pushedPx]，直到尺寸静止。
 * 按"相对上一帧的位移"而不是"离视口底还差多少"来算，这样即使用户在展开后滚动过列表，折叠也不会突然跳一下。
 */
suspend fun LazyListState.followCollapse(key: Any, pushedPx: Int) {
    var remaining = pushedPx
    var lastBottom: Int? = null
    val settle = SizeSettle()
    var frames = 0
    while (remaining > 0 && frames++ < MAX_FRAMES) {
        withFrameNanos { }
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        if (item == null || settle.update(item.size)) break
        val bottom = item.offset + item.size
        val rise = lastBottom?.let { it - bottom } ?: 0
        val delta = minOf(rise, remaining)
        val restored = if (delta > 0) (-scrollBy(-delta.toFloat())).roundToInt() else 0
        remaining -= restored
        lastBottom = bottom + restored
    }
}

/** 连续 [SETTLED_FRAMES] 帧尺寸不变即静止。 */
private class SizeSettle {
    private var lastSize = -1
    private var settledFrames = 0

    /** 记入这一帧的尺寸，返回是否已静止。 */
    fun update(size: Int): Boolean {
        settledFrames = if (size == lastSize) settledFrames + 1 else 0
        lastSize = size
        return settledFrames >= SETTLED_FRAMES
    }
}

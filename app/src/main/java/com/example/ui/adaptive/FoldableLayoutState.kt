package com.example.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.example.ai.FoldableDisplayPreference
import kotlinx.coroutines.flow.map

enum class FoldablePostureMode {
  COVER_COMPACT,       // Z Fold outer cover screen (< 600dp width)
  TOP_BOTTOM_SPLIT,    // Z Fold unfolded portrait (top list / bottom persistent AI + editor)
  FULLSCREEN_EXPANDED, // Z Fold unfolded fullscreen single-pane focus view
  BOOK_TWO_PANE,       // Z Fold unfolded landscape or side-by-side dual pane
  TABLETOP_FLEX,       // Z Fold half-opened horizontal hinge (top dashboard / bottom list)
}

data class FoldableLayoutMetrics(
  val mode: FoldablePostureMode,
  val isExpandedFoldable: Boolean,
  val isUnfoldedPortrait: Boolean,
  val showNavigationRail: Boolean,
  val hasVerticalHinge: Boolean,
  val hingeBoundsWidthDp: Dp,
  val hingeTopHeightDp: Dp,
  val hingeGapHeightDp: Dp,
  val leftPaneRatio: Float,
  val topPaneRatio: Float,
  val postureBadgeLabel: String,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

@Composable
fun rememberFoldableLayoutMetrics(
  availableWidthDp: Dp,
  availableHeightDp: Dp,
  userPreference: FoldableDisplayPreference = FoldableDisplayPreference.AUTO,
): FoldableLayoutMetrics {
  val context = LocalContext.current
  val density = LocalDensity.current
  val activity = remember(context) { context.findActivity() }
  val adaptiveInfo = currentWindowAdaptiveInfo()

  val foldingFeature by produceState<FoldingFeature?>(initialValue = null, key1 = activity) {
    if (activity != null) {
      WindowInfoTracker.getOrCreate(activity)
        .windowLayoutInfo(activity)
        .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }
        .collect { value = it }
    }
  }

  val isTabletop = foldingFeature?.let {
    it.state == FoldingFeature.State.HALF_OPENED &&
      it.orientation == FoldingFeature.Orientation.HORIZONTAL
  } == true

  val hasVerticalFold = foldingFeature?.let {
    it.orientation == FoldingFeature.Orientation.VERTICAL &&
      (it.isSeparating || it.state == FoldingFeature.State.HALF_OPENED)
  } == true

  val isHorizontalFoldFlatOrSeparating = foldingFeature?.let {
    it.orientation == FoldingFeature.Orientation.HORIZONTAL
  } == true

  // Z Fold unfolded inner display typically reports ~670-760dp width in portrait
  // and >=800dp in landscape, while the outer cover screen stays <600dp.
  val isExpandedFoldable =
    adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT ||
      availableWidthDp >= 600.dp

  val isPortraitAspect = availableHeightDp >= availableWidthDp
  val isUnfoldedPortrait = isExpandedFoldable && !isTabletop && (isPortraitAspect || isHorizontalFoldFlatOrSeparating)

  val mode = when {
    isTabletop && availableHeightDp >= 480.dp -> FoldablePostureMode.TABLETOP_FLEX
    !isExpandedFoldable -> FoldablePostureMode.COVER_COMPACT
    else -> when (userPreference) {
      FoldableDisplayPreference.AUTO ->
        if (isUnfoldedPortrait) FoldablePostureMode.TOP_BOTTOM_SPLIT else FoldablePostureMode.BOOK_TWO_PANE
      FoldableDisplayPreference.TOP_BOTTOM_SPLIT -> FoldablePostureMode.TOP_BOTTOM_SPLIT
      FoldableDisplayPreference.FULLSCREEN -> FoldablePostureMode.FULLSCREEN_EXPANDED
      FoldableDisplayPreference.LEFT_RIGHT_TWO_PANE -> FoldablePostureMode.BOOK_TWO_PANE
    }
  }

  val hingeGapWidthDp = if (hasVerticalFold && foldingFeature != null) {
    with(density) { foldingFeature!!.bounds.width().toDp().coerceAtLeast(12.dp) }
  } else {
    12.dp
  }

  val leftPaneRatio = if (hasVerticalFold && foldingFeature != null && availableWidthDp > 0.dp) {
    val hingeLeftDp = with(density) { foldingFeature!!.bounds.left.toDp() }
    (hingeLeftDp / availableWidthDp).coerceIn(0.38f, 0.56f)
  } else if (availableWidthDp >= 840.dp) {
    0.46f
  } else {
    0.48f
  }

  val hingeTopHeightDp = if (isTabletop && foldingFeature != null) {
    with(density) { foldingFeature!!.bounds.top.toDp().coerceAtLeast(180.dp) }
  } else {
    availableHeightDp * 0.42f
  }

  val hingeGapHeightDp = if ((isTabletop || isHorizontalFoldFlatOrSeparating) && foldingFeature != null) {
    with(density) { foldingFeature!!.bounds.height().toDp().coerceAtLeast(10.dp) }
  } else {
    10.dp
  }

  val topPaneRatio = if (isHorizontalFoldFlatOrSeparating && foldingFeature != null && availableHeightDp > 0.dp) {
    val hingeTopDp = with(density) { foldingFeature!!.bounds.top.toDp() }
    (hingeTopDp / availableHeightDp).coerceIn(0.42f, 0.58f)
  } else {
    0.50f
  }

  // Keep full horizontal room on unfolded portrait (~600-759dp), show NavigationRail on wider landscape screens
  val showNavigationRail =
    (mode == FoldablePostureMode.BOOK_TWO_PANE || mode == FoldablePostureMode.FULLSCREEN_EXPANDED) &&
      availableWidthDp >= 760.dp

  val postureLabel = when (mode) {
    FoldablePostureMode.TABLETOP_FLEX -> "悬停立式"
    FoldablePostureMode.COVER_COMPACT -> "外屏单手"
    FoldablePostureMode.TOP_BOTTOM_SPLIT -> if (isUnfoldedPortrait) "展开竖屏·上下" else "上下分栏"
    FoldablePostureMode.FULLSCREEN_EXPANDED -> if (isUnfoldedPortrait) "展开竖屏·全屏" else "大屏全屏"
    FoldablePostureMode.BOOK_TWO_PANE -> if (hasVerticalFold) "折叠双栏" else "展开双栏"
  }

  return FoldableLayoutMetrics(
    mode = mode,
    isExpandedFoldable = isExpandedFoldable && !isTabletop,
    isUnfoldedPortrait = isUnfoldedPortrait,
    showNavigationRail = showNavigationRail,
    hasVerticalHinge = hasVerticalFold,
    hingeBoundsWidthDp = hingeGapWidthDp,
    hingeTopHeightDp = hingeTopHeightDp,
    hingeGapHeightDp = hingeGapHeightDp,
    leftPaneRatio = leftPaneRatio,
    topPaneRatio = topPaneRatio,
    postureBadgeLabel = postureLabel,
  )
}

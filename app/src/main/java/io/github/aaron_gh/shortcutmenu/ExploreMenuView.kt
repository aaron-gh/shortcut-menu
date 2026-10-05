package io.github.aaron_gh.shortcutmenu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * The menu for when no screen reader is on, like the accessibility shortcut menu on iPhone. The
 * choices fill the screen in rows, so there is always one under the finger. Touching or dragging
 * announces the row under the finger, and lifting the finger chooses it.
 */
@SuppressLint("ViewConstructor")
class ExploreMenuView(
  context: Context,
  private val labels: List<String>,
  private val onHover: (Int) -> Unit,
  private val onChoose: (Int) -> Unit,
) : View(context) {
  private var current = -1

  private val textPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = context.getColor(R.color.menu_text)
      textAlign = Paint.Align.CENTER
      textSize = sp(28f)
    }
  private val linePaint =
    Paint().apply {
      color = context.getColor(R.color.menu_line)
      strokeWidth = sp(1f)
    }
  private val highlightPaint = Paint().apply { color = context.getColor(R.color.menu_highlight) }

  init {
    setBackgroundColor(context.getColor(R.color.menu_background))
    isHapticFeedbackEnabled = true
  }

  /** Which row is at [y], with the rows sharing the height equally. */
  fun rowAt(y: Float): Int = MenuRows.rowAt(y, height, labels.size)

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    // Keeps the system's back gesture from taking a drag that starts at the side of the screen.
    systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
  }

  override fun onDraw(canvas: Canvas) {
    if (labels.isEmpty()) {
      return
    }
    val rowHeight = height.toFloat() / labels.size
    labels.forEachIndexed { index, label ->
      val top = index * rowHeight
      if (index == current) {
        canvas.drawRect(0f, top, width.toFloat(), top + rowHeight, highlightPaint)
      }
      if (index > 0) {
        canvas.drawLine(0f, top, width.toFloat(), top, linePaint)
      }
      val baseline = top + rowHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2
      canvas.drawText(label, width / 2f, baseline, textPaint)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN,
      MotionEvent.ACTION_MOVE -> {
        val row = rowAt(event.y)
        if (row != current) {
          current = row
          invalidate()
          performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
          onHover(row)
        }
      }
      MotionEvent.ACTION_UP -> {
        val row = current
        current = -1
        invalidate()
        if (row >= 0) {
          performHapticFeedback(HapticFeedbackConstants.CONFIRM)
          onChoose(row)
        }
      }
      MotionEvent.ACTION_CANCEL -> {
        current = -1
        invalidate()
      }
    }
    return true
  }

  private fun sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}

/** Where the rows of the menu are. */
object MenuRows {
  /** The row at [y] in a view [height] pixels tall with [count] rows, or -1 with no rows. */
  fun rowAt(y: Float, height: Int, count: Int): Int {
    if (count <= 0 || height <= 0) {
      return -1
    }
    return (y * count / height).toInt().coerceIn(0, count - 1)
  }
}

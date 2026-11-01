package thesonid.com.brailler;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

/** Draws above the demonstration, following its actual target through layout and swipe motion. */
final class GuidePointer extends FrameLayout {
  private final ConstellateUi ui;
  private final View target;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect bounds = new Rect();
  private final Path arrow = new Path();
  private ValueAnimator pulse;
  private float phase;

  GuidePointer(ConstellateUi ui, View target) {
    super(ui.context);
    this.ui = ui;
    this.target = target;
    setTag("starter_guide_pointer");
    setClipChildren(false);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    pulse = ValueAnimator.ofFloat(0, 1);
    pulse.setDuration(850);
    pulse.setRepeatMode(ValueAnimator.REVERSE);
    pulse.setRepeatCount(ValueAnimator.INFINITE);
    pulse.addUpdateListener(value -> { phase = (float) value.getAnimatedValue(); invalidate(); });
    pulse.start();
  }

  @Override protected void onDetachedFromWindow() {
    if (pulse != null) { pulse.cancel(); pulse = null; }
    super.onDetachedFromWindow();
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    target.getDrawingRect(bounds);
    offsetDescendantRectToMyCoords(target, bounds);
    float x = bounds.centerX(), y = bounds.centerY();
    float size = ui.dp(32), nudge = ui.dp(3) * phase;
    x = Math.min(x + nudge, getWidth() - size - ui.dp(4));
    y = Math.min(y + nudge, getHeight() - size - ui.dp(4));
    canvas.save();
    canvas.translate(x, y);
    arrow.reset();
    arrow.moveTo(0, 0);
    arrow.lineTo(size * .12f, size);
    arrow.lineTo(size * .4f, size * .72f);
    arrow.lineTo(size * .62f, size * 1.06f);
    arrow.lineTo(size * .82f, size * .94f);
    arrow.lineTo(size * .59f, size * .6f);
    arrow.lineTo(size * .98f, size * .56f);
    arrow.close();
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(ui.accent);
    canvas.drawPath(arrow, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(ui.dp(2));
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setColor(0xFFFFFFFF);
    canvas.drawPath(arrow, paint);
    canvas.restore();
  }
}

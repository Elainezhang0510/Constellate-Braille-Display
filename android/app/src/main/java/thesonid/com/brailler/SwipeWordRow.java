package thesonid.com.brailler;

import android.view.*;
import android.widget.*;

/** Horizontal gestures reveal Delete without taking vertical scrolling from the library. */
final class SwipeWordRow extends FrameLayout {
  private final View foreground;
  private final int reveal, slop;
  private float startX, startY, base;
  private boolean dragging;
  SwipeWordRow(ConstellateUi ui, View card, String word, Runnable remove) {
    super(ui.context);
    foreground = card; reveal = ui.dp(92);
    slop = ViewConfiguration.get(ui.context).getScaledTouchSlop();
    setClipChildren(true);
    TextView delete = ui.text("Delete", 16, true);
    delete.setTextColor(0xFFFFFFFF); delete.setGravity(android.view.Gravity.CENTER);
    delete.setBackground(ui.shape(0xFFD92D20, 0xFFD92D20, 12, 0));
    delete.setContentDescription("Delete " + word); delete.setFocusable(true);
    delete.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(delete, new FrameLayout.LayoutParams(reveal, -1));
    addView(card, new FrameLayout.LayoutParams(-1, -2));
    delete.setOnClickListener(v -> remove.run());
    card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override public void onInitializeAccessibilityNodeInfo(View v, android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(v, info);
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.R.id.cut, "Delete " + word));
      }
      @Override public boolean performAccessibilityAction(View v, int action, android.os.Bundle args) {
        if (action == android.R.id.cut) { remove.run(); return true; }
        return super.performAccessibilityAction(v, action, args);
      }
    });
  }
  @Override public boolean onInterceptTouchEvent(MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      foreground.animate().cancel(); startX=e.getX();startY=e.getY();base=foreground.getTranslationX();dragging=false;
    } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
      float dx=e.getX()-startX,dy=e.getY()-startY;
      if (Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.2f) {
        dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
      }
    }
    return false;
  }
  @Override public boolean onTouchEvent(MotionEvent e) {
    if (e.getActionMasked()==MotionEvent.ACTION_MOVE) {
      // Empty card space can make this row the touch target itself. In that case
      // Android no longer calls onInterceptTouchEvent for subsequent moves.
      float dx=e.getX()-startX,dy=e.getY()-startY;
      if (!dragging && Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.2f) {
        dragging=true;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
      }
      if(dragging)foreground.setTranslationX(Math.max(0,Math.min(reveal,base+dx)));
      return true;
    }
    if (e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
      boolean open=e.getActionMasked()==MotionEvent.ACTION_UP && foreground.getTranslationX()>reveal/2f;
      foreground.animate().translationX(open?reveal:0).setDuration(180).start();
      getChildAt(0).setImportantForAccessibility(open?View.IMPORTANT_FOR_ACCESSIBILITY_YES:View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
      dragging=false;return true;
    }
    return true;
  }
  void demonstrate() { foreground.animate().translationX(reveal).setDuration(700).start(); }
}

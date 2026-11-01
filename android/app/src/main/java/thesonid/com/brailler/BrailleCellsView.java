package thesonid.com.brailler;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Six-dot cells wrap to available native width; quiz mode never exposes the answer. */
final class BrailleCellsView extends View {
  private final ConstellateUi ui;
  private final String word;
  private final boolean quiz;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int columns = 4;

  BrailleCellsView(ConstellateUi ui, String word, boolean quiz) {
    super(ui.context);
    this.ui = ui;
    this.word = word;
    this.quiz = quiz;
    setBackground(ui.shape(ui.alt, ui.line, 14, 2));
    setContentDescription(
        quiz
            ? "Braille practice pattern. Use Listen to a hint for an audio alternative."
            : "Grade 1 Braille preview of " + word);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  @Override
  protected void onMeasure(int w, int h) {
    int width = MeasureSpec.getSize(w);
    columns = Math.max(1, Math.min(4, (width - ui.dp(24)) / ui.dp(60)));
    int rows = Math.max(1, (word.length() + columns - 1) / columns);
    setMeasuredDimension(width, ui.dp(40 + rows * (quiz ? 82 : 106)));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    for (int i = 0; i < word.length(); i++) {
      int row = i / columns,
          col = i % columns,
          count = Math.min(columns, word.length() - row * columns);
      float start = (getWidth() - ui.dp(count * 60)) / 2f;
      for (int dot = 1; dot <= 6; dot++) {
        float x = start + ui.dp(col * 60 + 18 + (dot > 3 ? 22 : 0));
        float y = ui.dp(28 + row * (quiz ? 82 : 106) + ((dot - 1) % 3) * 22);
        boolean raised = BraillePreview.isRaised(word.charAt(i), dot);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(raised ? ui.ink : (ui.dark ? 0xFF2D2F33 : 0xFFE5E7EB));
        canvas.drawCircle(x, y, ui.dp(7), paint);
        if (!raised) {
          paint.setColor(0xFF6B7280);
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(ui.dp(1));
          canvas.drawCircle(x, y, ui.dp(7), paint);
        }
      }
      if (!quiz) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ui.muted);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(ui.dp(16));
        canvas.drawText(
            String.valueOf(word.charAt(i)),
            start + ui.dp(col * 60 + 29),
            ui.dp(99 + row * 106),
            paint);
      }
    }
  }
}

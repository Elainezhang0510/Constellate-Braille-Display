package thesonid.com.brailler;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;

/** Native, reusable controls sized from the supplied Make CSS. No WebView. */
final class ConstellateUi {
  static ConstellateUi forAccount(Context context) {
    com.google.firebase.auth.FirebaseUser account =
        FirebaseProvider.isConfigured(context)
            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()
            : null;
    if (account == null) return new ConstellateUi(context, false, false);
    android.content.SharedPreferences prefs =
        context.getSharedPreferences(
            "constellate_preferences_" + account.getUid(), Context.MODE_PRIVATE);
    return new ConstellateUi(
        context, prefs.getBoolean("dark", false), "user".equals(prefs.getString("experience", "")));
  }

  final Context context;
  final boolean user, dark;
  final int bg, surface, alt, ink, muted, line, accent = Color.rgb(240, 68, 40), soft, active;
  private final Typeface font, displayFont;

  ConstellateUi(Context context, boolean dark, boolean user) {
    this.context = context;
    this.user = user;
    this.dark = dark || user;
    bg = color(user ? "#000000" : dark ? "#121212" : "#F6F0E4");
    surface = color(user ? "#111111" : dark ? "#1E1E1E" : "#FFFFFF");
    alt = color(user ? "#1B1B1B" : dark ? "#28282B" : "#ECEEF2");
    ink = color(this.dark ? "#FFFFFF" : "#121212");
    muted = color(this.dark ? "#D1D5DB" : "#4A5048");
    line = color(this.dark ? "#FFFFFF" : "#222523");
    soft = color(this.dark ? "#2B1713" : "#FFF0EB");
    active = color(this.dark ? "#FFB5A5" : "#98200C");
    font = ResourcesCompat.getFont(context, R.font.dmsans);
    displayFont = ResourcesCompat.getFont(context, R.font.manrope);
  }

  static int color(String hex) {
    return Color.parseColor(hex);
  }

  int dp(float value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(context);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(context);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  GradientDrawable shape(int fill, int stroke, float radius, int width) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(radius));
    if (width > 0) d.setStroke(dp(width), stroke);
    return d;
  }

  void padding(View v, int h, int vertical) {
    v.setPadding(dp(h), dp(vertical), dp(h), dp(vertical));
  }

  void add(LinearLayout parent, View child, int top) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(top);
    parent.addView(child, p);
  }

  void gap(LinearLayout parent, int height) {
    View v = new View(context);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
  }

  LinearLayout panel(LinearLayout parent) {
    LinearLayout p = column();
    padding(p, 18, 22);
    p.setBackground(shape(surface, line, 16, 2));
    add(parent, p, 0);
    return p;
  }

  TextView text(String value, int size, boolean bold) {
    TextView t = new TextView(context);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(ink);
    t.setFontFeatureSettings("kern");
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(2), 1.08f);
    t.setTypeface(Typeface.create(font, bold ? Typeface.BOLD : Typeface.NORMAL));
    if (android.os.Build.VERSION.SDK_INT >= 26)
      t.setFontVariationSettings(bold ? "'wght' 800" : "'wght' 400");
    return t;
  }

  TextView title(LinearLayout parent, String value, int size) {
    TextView t = text(value, size, true);
    ViewCompat.setAccessibilityHeading(t, true);
    add(parent, t, 0);
    return t;
  }

  void display(TextView view) {
    view.setTypeface(Typeface.create(displayFont, Typeface.BOLD));
  }

  TextView body(LinearLayout parent, String value) {
    TextView t = text(value, 15, false);
    t.setTextColor(muted);
    add(parent, t, 12);
    return t;
  }

  TextView label(LinearLayout parent, String value) {
    TextView t = text(value, 15, true);
    add(parent, t, 18);
    return t;
  }

  EditText field(LinearLayout parent, String label, String hint, int inputType) {
    TextView l = label(parent, label);
    EditText e = new EditText(context);
    e.setId(View.generateViewId());
    l.setLabelFor(e.getId());
    e.setTextSize(17);
    e.setTextColor(ink);
    e.setHintTextColor(muted);
    e.setHint(hint);
    e.setSingleLine(true);
    e.setInputType(inputType);
    e.setMinHeight(dp(user ? 72 : 54));
    padding(e, 16, 12);
    e.setTypeface(font);
    e.setBackground(shape(surface, line, 12, 2));
    add(parent, e, 8);
    return e;
  }

  ImageView icon(int resource, int size, int tint) {
    ImageView i = new ImageView(context);
    i.setImageDrawable(ContextCompat.getDrawable(context, resource));
    i.setImageTintList(ColorStateList.valueOf(tint));
    i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    i.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return i;
  }

  LinearLayout action(
      LinearLayout parent, String label, int icon, boolean primary, Runnable click) {
    LinearLayout b = row();
    padding(b, 16, 14);
    b.setMinimumHeight(dp(user ? 72 : 56));
    int fg = primary ? bg : ink;
    int fill = primary ? ink : surface;
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000),
            shape(fill, line, 12, 2),
            null));
    if (icon != 0) {
      b.addView(icon(icon, user ? 36 : 26, fg));
    }
    TextView t = text(label, user ? 18 : 17, true);
    t.setTextColor(fg);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
    tp.setMarginStart(dp(icon == 0 ? 0 : 12));
    b.addView(t, tp);
    b.setContentDescription(label);
    b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    b.setFocusable(true);
    b.setClickable(true);
    b.setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName("android.widget.Button");
          }
        });
    b.setOnClickListener(
        v -> {
          if (b.isEnabled()) click.run();
        });
    for (int i = 0; i < b.getChildCount(); i++)
      b.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    add(parent, b, 16);
    return b;
  }

  LinearLayout choice(LinearLayout parent, String label, int icon, Runnable click) {
    LinearLayout b = action(parent, label, icon, false, click);
    b.setOrientation(LinearLayout.VERTICAL);
    b.setGravity(Gravity.CENTER);
    b.setMinimumHeight(dp(128));
    b.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
    TextView text = (TextView) b.getChildAt(1);
    text.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(12);
    text.setLayoutParams(p);
    return b;
  }

  void enable(View v, boolean enabled) {
    v.setEnabled(enabled);
    v.setAlpha(enabled ? 1f : .45f);
  }

  void styleTextTree(View view) {
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      text.setTextColor(ink);
      text.setTypeface(font);
      if (view instanceof EditText) text.setHintTextColor(muted);
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) styleTextTree(group.getChildAt(i));
    }
  }

  void rule(LinearLayout parent) {
    View v = new View(context);
    v.setBackgroundColor(dark ? 0xFF3F4448 : 0xFFCBD1D8);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
    p.topMargin = dp(22);
    p.bottomMargin = dp(6);
    parent.addView(v, p);
  }
}

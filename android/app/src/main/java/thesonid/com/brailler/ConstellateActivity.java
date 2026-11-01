package thesonid.com.brailler;

import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.*;
import java.util.*;

/** Native Constellate companion. Account authentication and board linking are separate. */
public class ConstellateActivity extends AppCompatActivity implements ConstellateWords.Observer {
  private static final int VOICE_SEARCH = 73;
  private static final int WIFI_SETUP = 74;
  private static final String[] PAGES = {"home", "library", "practice", "settings", "help"};
  private static final String[] NAV = {
    "Your board", "Word library", "Practice", "Settings", "Help"
  };
  private static final int[] ICONS = {
    R.drawable.ic_constellate_home,
    R.drawable.ic_constellate_book,
    R.drawable.ic_constellate_dots,
    R.drawable.ic_constellate_settings,
    R.drawable.ic_constellate_help
  };
  private static final String[] STEPS = {
    "Connect your board", "Explore your word library", "Listen and practice", "Navigate accessibly"
  };
  private static final String[] GUIDES = {
    "Open Pair your board and choose Bluetooth to send Wi-Fi details to your board."
        + " NFC and QR pairing require compatible hardware and are currently unavailable.",
    "Add an English word of up to eight letters. Select a word to see its six-dot Grade 1 Braille"
        + " cells. Without a board, your library is saved on this phone for your account.",
    "Choose Speak word to listen. Voice input searches your library. In Assistant mode, read the"
        + " dots and type your answer. In User mode, listen to the word and spell it.",
    "Use Android TalkBack or a keyboard to move between controls. User mode has larger controls and"
        + " high contrast. Change your experience or appearance in Settings."
  };
  private String page = "identity",
      selected = "",
      query = "",
      draft = "",
      experience = "",
      accountUid = "";
  private ConstellateUi ui;
  private LinearLayout root, content, navigation, wordRows, preview;
  private ScrollView scroll;
  private TextView status, wordCount, libraryState;
  private TextView boardConnectionStatus, boardHeading;
  private final Runnable boardConnectionObserver = this::updateBoardConnection;
  private EditText newWord;
  private FirebaseAuth auth;
  private ConstellateWords library;
  private AssistantLibrary assistantLibrary;
  private VoiceSearch voiceSearch;
  private String pendingVoiceWord = "";
  private ConstellateWords pendingVoiceLibrary;
  private AlertDialog voiceWordDialog;
  private TextToSpeech tts;
  private boolean ttsReady, busy, userMode, dark, fromSettings;
  private int tutorialStep, practiceIndex;
  private boolean waitingPractice;
  private boolean keyboardVisible;
  private boolean imeInsetsVisible;
  private int visibleWindowWidth, largestVisibleWindowHeight;
  private boolean foreground;
  private final android.graphics.Rect visibleWindow = new android.graphics.Rect();
  private final ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener =
      this::updateKeyboardVisibility;
  private LinearLayout guideControls, guideTrack;
  private TextView guideStepLabel, guideTitle, guideBody;
  private String lastAutoGuide = "";
  private final Handler authHandler = new Handler(Looper.getMainLooper());
  private Runnable authWaitNotice;
  private final FirebaseAuth.AuthStateListener authListener =
      firebase -> {
        if (busy || content == null) return;
        FirebaseUser current = firebase.getCurrentUser();
        if (current != null && !current.getUid().equals(accountUid)) {
          loadAccount();
          page = experience.isEmpty() ? "identity" : "home";
          render();
        } else if (current == null && !accountUid.isEmpty()) {
          if (library != null) {
            library.close();
            library = null;
          }
          accountUid = "";
          experience = "";
          userMode = false;
          dark = false;
          page = "identity";
          render();
        }
      };

  @Override
  protected void onCreate(Bundle saved) {
    setTheme(R.style.ConstellateTheme);
    super.onCreate(saved);
    setContentView(R.layout.activity_constellate);
    root = findViewById(R.id.constellate_root);
    content = findViewById(R.id.constellate_content);
    scroll = findViewById(R.id.constellate_scroll);
    navigation = findViewById(R.id.constellate_navigation);
    guideControls = findViewById(R.id.constellate_guide_controls);
    // Keep the tab bar out of the resized area above the keyboard. The ScrollView
    // still resizes so focused inputs can scroll into view.
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          imeInsetsVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
          updateKeyboardVisibility();
          return insets;
        });
    ViewCompat.requestApplyInsets(root);
    root.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
    if (FirebaseProvider.isConfigured(this)) auth = FirebaseAuth.getInstance();
    if (signedIn()) {
      loadAccount();
      page = experience.isEmpty() ? "identity" : "home";
      if (saved != null) {
        page = saved.getString("page", page);
        tutorialStep = saved.getInt("tutorial");
        practiceIndex = saved.getInt("practice");
        selected = saved.getString("selected", "");
        query = saved.getString("query", "");
        draft = saved.getString("draft", "");
        fromSettings = saved.getBoolean("from_settings");
      }
    } else if (saved != null) {
      experience = saved.getString("experience", "");
      userMode = experience.equals("user");
      String restoredPage = saved.getString("page", "identity");
      if (!experience.isEmpty() && (restoredPage.equals("login") || restoredPage.equals("signup")))
        page = restoredPage;
    }
    if (saved != null) assistantLibrary().restore(saved);
    tts =
        new TextToSpeech(
            this,
            result -> {
              ttsReady = result == TextToSpeech.SUCCESS;
              if (ttsReady && tts != null) {
                int language = tts.setLanguage(Locale.US);
                ttsReady =
                    language != TextToSpeech.LANG_MISSING_DATA
                        && language != TextToSpeech.LANG_NOT_SUPPORTED;
                maybeAutoSpeakGuide();
              }
            });
    render();
  }

  private boolean signedIn() {
    return auth != null && auth.getCurrentUser() != null;
  }

  private SharedPreferences preferences() {
    return getSharedPreferences("constellate_preferences_" + accountUid, MODE_PRIVATE);
  }

  private void loadAccount() {
    accountUid = auth.getCurrentUser().getUid();
    experience = preferences().getString("experience", "");
    userMode = experience.equals("user");
    dark = preferences().getBoolean("dark", false);
    loadWords();
  }

  private void loadWords() {
    if (library != null) library.close();
    library = new ConstellateWords(this, accountUid, this);
  }

  private boolean tabPage() {
    return Arrays.asList(PAGES).contains(page);
  }

  private void hideKeyboard() {
    View focus = getCurrentFocus();
    if (focus != null) {
      ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(focus.getWindowToken(), 0);
      focus.clearFocus();
    }
  }

  private void go(String next) {
    if (busy) return;
    clearVoiceWordPrompt();
    if ((page.equals("help") || page.equals("tutorial")) && tts != null) tts.stop();
    hideKeyboard();
    page = next;
    query = "";
    render();
    scroll.scrollTo(0, 0);
  }

  private void render() {
    if (assistantLibrary != null && (!page.equals("library") || userMode)) assistantLibrary.stop();
    boolean account = signedIn();
    if (!account && !page.equals("identity") && !page.equals("login") && !page.equals("signup"))
      page = "identity";
    ui = new ConstellateUi(this, dark, userMode);
    root.setBackgroundColor(ui.bg);
    getWindow().setStatusBarColor(ui.bg);
    getWindow().setNavigationBarColor(ui.surface);
    int flags = ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if (android.os.Build.VERSION.SDK_INT >= 26 && !ui.dark)
      flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
    content.removeAllViews();
    navigation.removeAllViews();
    guideControls.removeAllViews();
    guideControls.setVisibility(View.GONE);
    if (!page.equals("help") && !page.equals("tutorial")) {
      if (!lastAutoGuide.isEmpty() && tts != null) tts.stop();
      lastAutoGuide = "";
    }
    wordRows = null;
    preview = null;
    wordCount = null;
    libraryState = null;
    status = null;
    newWord = null;
    content.setGravity(Gravity.TOP);
    content.setPadding(ui.dp(18), ui.dp(page.equals("library") && !userMode ? 16 : 28), ui.dp(18), ui.dp(32));
    switch (page) {
      case "signup":
      case "login":
        authScreen();
        break;
      case "identity":
        identity();
        break;
      case "tutorial":
      case "help":
        tutorial();
        break;
      case "pairing":
        pairing();
        break;
      case "library":
        libraryScreen();
        break;
      case "practice":
        practice();
        break;
      case "settings":
        settings();
        break;
      default:
        home();
    }
    status = ui.text("", 15, true);
    status.setTextColor(ui.active);
    status.setVisibility(View.GONE);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    ui.add(content, status, 16);
    updateNavigationVisibility();
    if (tabPage()) bottomNavigation();
  }

  private void updateNavigationVisibility() {
    navigation.setVisibility(tabPage() && !keyboardVisible ? View.VISIBLE : View.GONE);
  }

  private void updateKeyboardVisibility() {
    // Some keyboards/older window implementations do not report IME insets to
    // this content view. Also inspect the visible window, independently of tabs.
    View decor = getWindow().getDecorView();
    decor.getWindowVisibleDisplayFrame(visibleWindow);
    if (visibleWindow.width() != visibleWindowWidth) {
      visibleWindowWidth = visibleWindow.width();
      largestVisibleWindowHeight = visibleWindow.height();
    }
    largestVisibleWindowHeight = Math.max(largestVisibleWindowHeight, visibleWindow.height());
    int obscured = decor.getRootView().getHeight() - visibleWindow.bottom;
    int threshold = Math.round(120 * getResources().getDisplayMetrics().density);
    keyboardVisible = imeInsetsVisible || obscured > threshold
        || largestVisibleWindowHeight - visibleWindow.height() > threshold;
    updateNavigationVisibility();
  }

  private void heading(String title) {
    ui.title(content, title, 30);
    ui.gap(content, 28);
  }

  private void notifyUser(String message) {
    if (isFinishing() || isDestroyed()) return;
    if (status != null) {
      status.setText(message);
      status.setVisibility(View.VISIBLE);
    }
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void bottomNavigation() {
    navigation.setBackgroundColor(ui.surface);
    View border = new View(this);
    border.setBackgroundColor(ui.line);
    navigation.addView(border, new LinearLayout.LayoutParams(-1, ui.dp(.5f)));
    LinearLayout row = ui.row();
    ui.padding(row, 4, 8);
    navigation.addView(row);
    for (int i = 0; i < PAGES.length; i++) {
      final String next = PAGES[i];
      boolean active = next.equals(page);
      LinearLayout tab = ui.column();
      tab.setGravity(Gravity.CENTER);
      ui.padding(tab, 2, 8);
      tab.setMinimumHeight(ui.dp(72));
      if (active) tab.setBackground(ui.shape(ui.soft, ui.soft, 8, 0));
      tab.addView(ui.icon(ICONS[i], 28, active ? ui.active : ui.ink));
      TextView label = ui.text(NAV[i], 10, active);
      label.setGravity(Gravity.CENTER);
      label.setLines(1);
      androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          label, 8, 10, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
      label.setTextColor(active ? ui.active : ui.ink);
      LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, ui.dp(20));
      labelParams.topMargin = ui.dp(6);
      tab.addView(label, labelParams);
      tab.setContentDescription(NAV[i]);
      tab.setSelected(active);
      tab.setFocusable(true);
      tab.setOnClickListener(v -> go(next));
      tab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      for (int c = 0; c < tab.getChildCount(); c++)
        tab.getChildAt(c).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
    }
  }

  private void authScreen() {
    boolean signup = page.equals("signup");
    content.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout card = ui.panel(content);
    View accent = new View(this);
    accent.setBackground(ui.shape(ui.accent, ui.accent, 3, 0));
    card.addView(accent, new LinearLayout.LayoutParams(-1, ui.dp(5)));
    ui.gap(card, 22);
    LinearLayout mark = ui.row();
    mark.setGravity(Gravity.CENTER);
    mark.addView(
        ui.icon(
            signup ? R.drawable.ic_constellate_signup : R.drawable.ic_constellate_dots,
            44,
            ui.accent));
    mark.setBackground(ui.shape(ui.soft, ui.accent, 38, 2));
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ui.dp(76), ui.dp(76));
    mp.gravity = Gravity.CENTER;
    card.addView(mark, mp);
    ui.gap(card, 16);
    ui.title(card, signup ? "Sign up" : "CONSTELLATE", 25).setGravity(Gravity.CENTER);
    EditText email =
        ui.field(
            card,
            "Email",
            "Enter your email",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    email.setFilters(new InputFilter[] {new InputFilter.LengthFilter(254)});
    if (android.os.Build.VERSION.SDK_INT >= 26)
      email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
    EditText password =
        ui.field(
            card,
            "Password",
            signup ? "At least 10 characters" : "Enter your password",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    password.setFilters(new InputFilter[] {new InputFilter.LengthFilter(128)});
    password.setSaveEnabled(false);
    if (android.os.Build.VERSION.SDK_INT >= 26)
      password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
    ViewGroup.LayoutParams passwordLayout = password.getLayoutParams();
    card.removeView(password);
    FrameLayout passwordBox = new FrameLayout(this);
    passwordBox.setBackground(ui.shape(ui.surface, ui.line, 12, 2));
    password.setBackground(null);
    password.setPadding(ui.dp(16), ui.dp(12), ui.dp(90), ui.dp(12));
    passwordBox.addView(password, new FrameLayout.LayoutParams(-1, -2));
    TextView show = ui.text("Show", 15, true);
    show.setGravity(Gravity.CENTER);
    show.setFocusable(true);
    show.setContentDescription("Show password");
    FrameLayout.LayoutParams showLayout =
        new FrameLayout.LayoutParams(ui.dp(80), ui.dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
    passwordBox.addView(show, showLayout);
    card.addView(passwordBox, passwordLayout);
    show.setOnClickListener(
        v -> {
          boolean on = password.getTransformationMethod() != null;
          int at = password.getSelectionStart();
          password.setTransformationMethod(on ? null : PasswordTransformationMethod.getInstance());
          password.setSelection(Math.max(0, at));
          show.setText(on ? "Hide" : "Show");
          show.setContentDescription(on ? "Hide password" : "Show password");
        });
    LinearLayout submit =
        ui.action(
            card,
            signup ? "Create account" : "Log in",
            R.drawable.ic_constellate_arrow,
            true,
            () -> {});
    LinearLayout switchPage =
        ui.action(
            card,
            signup ? "Back to Log in" : "Sign up",
            signup ? 0 : R.drawable.ic_constellate_signup,
            false,
            () -> go(signup ? "login" : "signup"));
    submit.setOnClickListener(
        v -> {
          if (busy) return;
          String address = email.getText().toString().trim(),
              secret = password.getText().toString();
          if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
            email.setError("Enter a valid email address.");
            email.requestFocus();
            return;
          }
          if (secret.isEmpty() || (signup && secret.length() < 10)) {
            password.setError(signup ? "Use at least 10 characters." : "Enter your password.");
            password.requestFocus();
            return;
          }
          if (auth == null) {
            notifyUser("Firebase is not configured for this build.");
            return;
          }
          hideKeyboard();
          busy = true;
          ui.enable(submit, false);
          ui.enable(switchPage, false);
          notifyUser(signup ? "Creating your account…" : "Logging in…");
          com.google.android.gms.tasks.Task<AuthResult> task =
              signup
                  ? auth.createUserWithEmailAndPassword(address, secret)
                  : auth.signInWithEmailAndPassword(address, secret);
          authWaitNotice =
              () -> {
                if (!task.isComplete() && !isFinishing() && !isDestroyed()) {
                  notifyUser(
                      "Firebase has not responded yet. Check this phone's internet connection,"
                          + " including any VPN or proxy settings. The request is still pending;"
                          + " this does not confirm whether an account was created.");
                }
              };
          authHandler.postDelayed(authWaitNotice, 25000);
          task.addOnCompleteListener(
              result -> {
                if (authWaitNotice != null) {
                  authHandler.removeCallbacks(authWaitNotice);
                  authWaitNotice = null;
                }
                if (isDestroyed()) return;
                busy = false;
                if (result.isSuccessful()) {
                  password.setText("");
                  String chosenExperience = experience;
                  accountUid = auth.getCurrentUser().getUid();
                  preferences().edit().putString("experience", chosenExperience).apply();
                  loadAccount();
                  tutorialStep = 0;
                  page = signup ? "tutorial" : "home";
                  render();
                  scroll.scrollTo(0, 0);
                } else {
                  ui.enable(submit, true);
                  ui.enable(switchPage, true);
                  notifyUser(authError(result.getException()));
                }
              });
        });
    if (!signup)
      ui.action(
          card,
          "Forgot password?",
          0,
          false,
          () -> {
            if (busy) return;
            String address = email.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
              email.setError("Enter your email address first.");
              return;
            }
            if (auth == null) {
              notifyUser("Firebase is not configured for this build.");
              return;
            }
            auth.sendPasswordResetEmail(address)
                .addOnCompleteListener(
                    result ->
                        notifyUser(
                            result.isSuccessful()
                                ? "If this address has an account, a reset email will arrive"
                                    + " shortly."
                                : authError(result.getException())));
          });
  }

  private String authError(Exception e) {
    String diagnostic =
        e instanceof FirebaseAuthException
            ? ((FirebaseAuthException) e).getErrorCode()
            : e == null ? "UNKNOWN" : e.getClass().getSimpleName();
    // Record only the error identifier, never credentials or server response bodies.
    android.util.Log.w("ConstellateAuth", "Firebase authentication failed: " + diagnostic);
    if (e instanceof FirebaseNetworkException)
      return "Unable to reach Firebase. Check this phone's internet connection and VPN or proxy"
          + " settings, then try again. [" + diagnostic + "]";
    if (e instanceof FirebaseAuthWeakPasswordException)
      return "Choose a stronger password with at least 10 characters.";
    if (e instanceof FirebaseAuthUserCollisionException)
      return "Unable to create this account. Try logging in or resetting your password.";
    if (e instanceof FirebaseAuthException) {
      String code = ((FirebaseAuthException) e).getErrorCode();
      if (code.equals("ERROR_OPERATION_NOT_ALLOWED")
          || code.equals("ERROR_CONFIGURATION_NOT_FOUND"))
        return "Email sign-in is not enabled for this Firebase project yet.";
      if (code.equals("ERROR_TOO_MANY_REQUESTS"))
        return "Too many attempts. Please wait before trying again.";
    }
    return "Unable to complete authentication. Check your Firebase configuration or try again. ["
        + diagnostic + "]";
  }

  private void identity() {
    if (signedIn()) {
      heading("Choose your experience");
    } else {
      content.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout brand = ui.column();
      brand.setGravity(Gravity.CENTER);
      ui.add(content, brand, 0);
      LinearLayout mark = ui.row();
      mark.setGravity(Gravity.CENTER);
      mark.setBackground(ui.shape(ui.soft, ui.accent, 24, 2));
      mark.addView(ui.icon(R.drawable.ic_constellate_dots, 44, ui.accent));
      LinearLayout.LayoutParams markLayout =
          new LinearLayout.LayoutParams(ui.dp(76), ui.dp(76));
      markLayout.gravity = Gravity.CENTER;
      brand.addView(mark, markLayout);
      ui.gap(brand, 14);
      TextView name = ui.text("constellate.", 29, true);
      ui.display(name);
      name.setGravity(Gravity.CENTER);
      android.text.SpannableString wordmark = new android.text.SpannableString("constellate.");
      wordmark.setSpan(
          new android.text.style.ForegroundColorSpan(ui.accent),
          wordmark.length() - 1,
          wordmark.length(),
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      name.setText(wordmark);
      ui.add(brand, name, 0);
      ui.gap(content, 28);
      TextView title = ui.title(content, "Choose your experience", 28);
      title.setGravity(Gravity.CENTER);
      ui.gap(content, 20);
    }
    LinearLayout card = ui.panel(content);
    ui.choice(
        card,
        "I am the assistant",
        R.drawable.ic_constellate_assistant,
        () -> chooseExperience("assistant"));
    ui.choice(
        card, "I am the user", R.drawable.ic_constellate_person, () -> chooseExperience("user"));
    ui.body(
        card,
        "User mode enables dark high-contrast appearance, larger controls and no on-screen Braille"
            + " previews.");
    if (signedIn())
      ui.action(card, "Log out", R.drawable.ic_constellate_logout, false, this::confirmLogout);
  }

  private void chooseExperience(String mode) {
    experience = mode;
    userMode = mode.equals("user");
    if (signedIn()) preferences().edit().putString("experience", mode).apply();
    go(signedIn() ? "home" : "login");
  }

  private void home() {
    heading(userMode ? "Your board" : "A world at your fingertips");
    LinearLayout card = ui.panel(content);
    card.setBackground(ui.shape(ui.surface, ui.line, 18, 3));
    TextView label = ui.text("YOUR BRAILLE BOARD", 13, true);
    label.setLetterSpacing(.12f);
    label.setTextColor(ui.muted);
    ui.add(card, label, 0);
    ui.gap(card, 8);
    boardHeading = ui.title(
            card,
            SessionStore.isLoggedIn(this) ? SessionStore.getBoardName(this) : "Your next discovery",
            34);
    ui.display(boardHeading);
    ui.action(
        card,
        "Pair your board",
        R.drawable.ic_constellate_settings,
        true,
        () -> {
          fromSettings = true;
          go("pairing");
        });
    ui.gap(content, 20);
    LinearLayout connection = ui.panel(content);
    TextView caption = ui.text("BOARD CONNECTION", 11, true);
    caption.setLetterSpacing(.12f);
    ui.add(connection, caption, 0);
    boardConnectionStatus = ui.body(connection, "");
    boardConnectionStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    updateBoardConnection();
    LinearLayout microphone =
        ui.action(
            content,
            "Voice input\nSearch your word library",
            R.drawable.ic_constellate_mic,
            false,
            this::dictate);
    ImageView micIcon = (ImageView) microphone.getChildAt(0);
    micIcon.setBackground(ui.shape(ui.accent, ui.surface, 40, 2));
    micIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF121212));
    int micSize = userMode ? 80 : 54;
    micIcon.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(micSize), ui.dp(micSize)));
    ui.padding(micIcon, 10, 10);
  }

  private void updateBoardConnection() {
    if (!page.equals("home") || boardConnectionStatus == null) return;
    int state = BluetoothSession.state();
    boolean connected = state == BluetoothChatService.STATE_CONNECTED;
    String text = connected ? "Connected to " + BluetoothSession.connectedName()
        : state == BluetoothChatService.STATE_CONNECTING ? "Connecting to board…"
        : SessionStore.isLoggedIn(this) ? "Board linked · Bluetooth not connected"
        : "No board connected";
    boardConnectionStatus.setText(text);
    boardHeading.setText(connected ? BluetoothSession.connectedName()
        : SessionStore.isLoggedIn(this) ? SessionStore.getBoardName(this) : "Your next discovery");
  }

  private void tutorial() {
    heading(page.equals("help") ? "Help & voice guide" : "Getting started");
    guideTrack = ui.row();
    ui.add(content, guideTrack, 0);
    for (int i = 0; i < STEPS.length; i++) {
      final int index = i;
      TextView step = ui.text(String.valueOf(i + 1), 17, true);
      step.setGravity(Gravity.CENTER);
      step.setMinHeight(ui.dp(48));
      step.setContentDescription("Step " + (i + 1) + ": " + STEPS[i]);
      step.setFocusable(true);
      step.setOnClickListener(v -> changeGuideStep(index));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      if (i > 0) p.setMarginStart(ui.dp(10));
      guideTrack.addView(step, p);
    }
    ui.gap(content, 20);
    LinearLayout card = ui.panel(content);
    guideStepLabel = ui.body(card, "");
    ui.gap(card, 16);
    guideTitle = ui.title(card, "", 26);
    guideBody = ui.body(card, "");
    ui.gap(content, 16);
    LinearLayout voice = ui.panel(content);
    ui.title(voice, "Voice guide", 22);
    ui.body(voice, userMode
        ? "Instructions play automatically. Tap Replay to hear this step again."
        : "Listen to the instructions for this step.");
    ui.action(voice, userMode ? "Replay" : "Listen", R.drawable.ic_constellate_volume,
        false, () -> speak(STEPS[tutorialStep] + ". " + GUIDES[tutorialStep]));
    if (page.equals("tutorial"))
      ui.action(content, "Skip for now", 0, false, () -> go("home"));
    updateGuideStep();
  }

  private void changeGuideStep(int index) {
    if (index < 0 || index >= STEPS.length || index == tutorialStep) return;
    if (tts != null) tts.stop();
    tutorialStep = index;
    // Update the existing instruction views; do not recreate/reset the ScrollView.
    updateGuideStep();
  }

  private void updateGuideStep() {
    guideStepLabel.setText("STEP " + (tutorialStep + 1) + " OF " + STEPS.length);
    guideTitle.setText(STEPS[tutorialStep]);
    guideBody.setText(GUIDES[tutorialStep]);
    for (int i = 0; i < guideTrack.getChildCount(); i++) {
      TextView step = (TextView) guideTrack.getChildAt(i);
      boolean active = i == tutorialStep;
      step.setSelected(active);
      step.setBackground(ui.shape(active ? ui.ink : ui.surface, ui.line, 10, 2));
      step.setTextColor(active ? ui.bg : ui.ink);
    }
    guideControls.setVisibility(View.VISIBLE);
    guideControls.setBackgroundColor(ui.surface);
    guideControls.removeAllViews();
    LinearLayout row = ui.row();
    guideControls.addView(row);
    LinearLayout left = ui.column(), right = ui.column();
    row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 2);
    rightParams.setMarginStart(ui.dp(10));
    row.addView(right, rightParams);
    View back = ui.action(left, "Back", 0, false, () -> changeGuideStep(tutorialStep - 1));
    ui.enable(back, tutorialStep > 0);
    ui.action(right, tutorialStep < STEPS.length - 1 ? "Next step"
        : page.equals("help") ? "Back to dashboard" : "Pair your board", 0, true, () -> {
          if (tutorialStep < STEPS.length - 1) changeGuideStep(tutorialStep + 1);
          else if (page.equals("help")) go("home");
          else {
            fromSettings = false;
            go("pairing");
          }
        });
    maybeAutoSpeakGuide();
  }

  private void maybeAutoSpeakGuide() {
    if (!userMode || (!page.equals("help") && !page.equals("tutorial"))
        || !foreground || !ttsReady || tts == null || isFinishing() || isDestroyed()) return;
    String key = page + ":" + tutorialStep;
    if (key.equals(lastAutoGuide)) return;
    lastAutoGuide = key;
    speak(STEPS[tutorialStep] + ". " + GUIDES[tutorialStep]);
  }
  private void pairing() {
    heading("Pair your board");
    LinearLayout card = ui.panel(content);
    ui.action(
        card,
        "Bluetooth · set up Wi-Fi",
        R.drawable.ic_constellate_bluetooth,
        false,
        this::openWifi);
    ui.body(card, "NFC and QR pairing need a compatible tag or pairing code on the board.");
    ui.enable(
        ui.action(card, "NFC · unavailable", R.drawable.ic_constellate_nfc, false, () -> {}),
        false);
    ui.enable(
        ui.action(card, "QR code · unavailable", R.drawable.ic_constellate_scan, false, () -> {}),
        false);
    ui.action(
        content,
        fromSettings ? "Back to your board" : "Continue without a board",
        R.drawable.ic_constellate_arrow,
        true,
        () -> go("home"));
  }

  private void libraryScreen() {
    if (!userMode) {
      assistantLibrary().bind(content, ui, library, accountUid);
      return;
    }
    heading("Word Library");
    LinearLayout add = ui.panel(content);
    ui.title(add, "Add a new word", 21);
    newWord =
        ui.field(
            add,
            "Up to 8 English letters",
            userMode ? "Enter a word" : "A new word to discover…",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    newWord.setFilters(new InputFilter[] {new InputFilter.LengthFilter(8)});
    newWord.setText(draft);
    newWord.addTextChangedListener(watcher(value -> draft = value));
    ui.action(add, "Add word", R.drawable.ic_constellate_plus, true, this::addWord);
    ui.body(
        add,
        library != null && library.cloud
            ? "Words are saved to your linked board’s library."
            : "Saved on this phone for your account. Link a board to use its cloud library.");
    ui.gap(content, 20);
    LinearLayout list = ui.panel(content);
    wordCount = ui.title(list, "Your words", 21);
    ui.body(list, "Grade 1 Braille · Grade 2 unavailable");
    EditText search =
        ui.field(list, "Search words", "Search your words", InputType.TYPE_CLASS_TEXT);
    search.setText(query);
    search.addTextChangedListener(
        watcher(
            value -> {
              query = value;
              updateWordRows();
            }));
    ui.action(list, "Voice search", R.drawable.ic_constellate_mic, false, this::dictate);
    libraryState = ui.body(list, "");
    wordRows = ui.column();
    ui.add(list, wordRows, 12);
    if (!userMode) {
      ui.gap(content, 20);
      preview = ui.panel(content);
    }
    updateWordRows();
    updatePreview();
    if (!userMode) {
      ui.gap(content, 20);
      LinearLayout tip = ui.panel(content);
      ui.title(tip, "A tip for your fingertips", 17);
      ui.body(
          tip, "Take your time. Trace each letter from left to right, then listen to the word.");
    }
  }

  private AssistantLibrary assistantLibrary() {
    if (assistantLibrary == null) assistantLibrary = new AssistantLibrary(this, new AssistantLibrary.Host() {
      public void speak(String text) { ConstellateActivity.this.speak(text); }
      public void stopSpeech() { if (tts != null) tts.stop(); }
      public void hideKeyboard() { ConstellateActivity.this.hideKeyboard(); }
      public void send(String word) {
        sendToBrailleDisplay(word);
      }
    });
    return assistantLibrary;
  }

  private void sendToBrailleDisplay(String word) {
    hideKeyboard();
    if (BluetoothSession.sendWordIfConnected(word)) {
      notifyUser("Sending to Braille board…");
    } else {
      Intent intent = new Intent(this, MainActivity.class);
      intent.putExtra(MainActivity.EXTRA_WORD, word);
      startActivity(intent);
    }
  }

  private interface TextChange {
    void change(String text);
  }

  private TextWatcher watcher(TextChange action) {
    return new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      public void onTextChanged(CharSequence s, int start, int before, int count) {
        action.change(s.toString());
      }

      public void afterTextChanged(Editable s) {}
    };
  }

  private void updateWordRows() {
    if (wordRows == null || library == null) return;
    wordRows.removeAllViews();
    wordCount.setText("Your words  " + library.words.size());
    int found = 0;
    for (String word : library.words) {
      if (!word.contains(ConstellateRules.word(query))) continue;
      found++;
      LinearLayout card = ui.row();
      ui.padding(card, 8, 12);
      card.setBackground(
          ui.shape(
              selected.equals(word) ? ui.soft : ui.surface,
              selected.equals(word) ? ui.accent : ui.line,
              12,
              1));
      TextView label = ui.text(word, 22, true);
      label.setMinHeight(ui.dp(48));
      label.setGravity(Gravity.CENTER_VERTICAL);
      label.setContentDescription("Preview " + word);
      label.setFocusable(true);
      label.setOnClickListener(
          v -> {
            selected = word;
            updateWordRows();
            updatePreview();
          });
      LinearLayout metadata = ui.column();
      card.addView(metadata, new LinearLayout.LayoutParams(0, -2, 1));
      ui.add(metadata, label, 0);
      TextView count = ui.text(word.length() + " letters · Grade 1", 12, false);
      count.setTextColor(ui.muted);
      ui.add(metadata, count, 2);
      LinearLayout actions = ui.row();
      card.addView(actions, new LinearLayout.LayoutParams(-2, -2));
      iconButton(
          actions, "Send " + word + " to braille display", R.drawable.ic_constellate_bluetooth,
          () -> sendToBrailleDisplay(word));
      iconButton(
          actions,
          "Delete " + word + " from library",
          R.drawable.ic_constellate_trash,
          () -> confirmDelete(word));
      ui.add(wordRows, card, 10);
    }
    libraryState.setText(
        library.error != null
            ? library.error
            : !library.loaded
                ? "Loading board words…"
                : found == 0
                    ? (query.isEmpty()
                        ? "No words yet. Add a word to get started."
                        : "No matching words. Try another search.")
                    : "");
    libraryState.setVisibility(!library.loaded || found == 0 ? View.VISIBLE : View.GONE);
  }

  private void iconButton(LinearLayout row, String description, int icon, Runnable action) {
    ImageButton b = new ImageButton(this);
    b.setImageResource(icon);
    b.setImageTintList(android.content.res.ColorStateList.valueOf(ui.ink));
    b.setContentDescription(description);
    b.setBackground(ui.shape(ui.surface, ui.line, 10, 1));
    ui.padding(b, 12, 12);
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(ui.dp(userMode ? 64 : 48), ui.dp(userMode ? 64 : 48));
    p.setMarginEnd(ui.dp(10));
    row.addView(b, p);
  }

  private void updatePreview() {
    if (preview == null) return;
    preview.removeAllViews();
    if (selected.isEmpty() && library != null && !library.words.isEmpty())
      selected = library.words.get(0);
    if (selected.isEmpty()) {
      ui.title(preview, "Braille preview", 21);
      ui.body(preview, "Select or add a word to explore its dots.");
      return;
    }
    if (!userMode) ui.body(preview, "BRAILLE PREVIEW");
    ui.gap(preview, 14);
    ui.title(preview, selected, 36);
    if (!userMode) {
      ui.body(preview, selected.length() + " letters · Grade 1 Braille");
      ui.add(preview, new BrailleCellsView(ui, selected, false), 20);
    }
    ui.action(preview, "Speak word", R.drawable.ic_constellate_volume, true, () -> speak(selected));
    ui.action(preview, "Send to Braille board", R.drawable.ic_constellate_bluetooth,
        false, () -> {
          Intent intent = new Intent(this, MainActivity.class);
          intent.putExtra(MainActivity.EXTRA_WORD, selected);
          startActivity(intent);
        });
    if (!userMode)
      ui.body(
          preview,
          "This is an on-screen preview. Use your board’s controls to choose a word on the physical"
              + " display.");
  }

  private void addWord() {
    if (library == null) return;
    String value = ConstellateRules.word(newWord.getText().toString());
    if (!ConstellateRules.validWord(value)) {
      newWord.setError("Use 1–8 English letters, without spaces or numbers.");
      return;
    }
    if (library.words.contains(value)) {
      selected = value;
      updatePreview();
      notifyUser("This word is already in your library.");
      return;
    }
    ConstellateWords targetLibrary = library;
    targetLibrary.write(
        value,
        false,
        ok -> {
          if (isDestroyed() || targetLibrary != library) return;
          if (ok) {
            selected = value;
            draft = "";
            if (newWord != null) newWord.setText("");
            updateWordRows();
            updatePreview();
            notifyUser(value + " added to your library.");
          } else notifyUser("Could not save the word. Try again when connected.");
        });
  }

  private void confirmDelete(String word) {
    new AlertDialog.Builder(this)
        .setTitle("Delete word?")
        .setMessage("Remove “" + word + "” from this library?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Delete",
            (d, w) ->
                library.write(
                    word,
                    true,
                    ok -> {
                      if (ok) {
                        if (selected.equals(word)) selected = "";
                        updateWordRows();
                        updatePreview();
                        notifyUser(word + " removed.");
                      } else notifyUser("Could not delete the word. Please try again.");
                    }))
        .show();
  }

  private void practice() {
    waitingPractice = library == null || !library.loaded || library.words.isEmpty();
    heading("Practice & test");
    LinearLayout card = ui.panel(content);
    ui.title(card, userMode ? "Spell the word" : "What word do you feel?", 25);
    if (library == null || !library.loaded || library.words.isEmpty()) {
      ui.body(
          card,
          library != null && library.loaded
              ? "Add a word to your library to start practicing."
              : "Your board words are still loading. Open the library to check the connection.");
      ui.action(
          card, "Open word library", R.drawable.ic_constellate_book, true, () -> go("library"));
      return;
    }
    String target = library.words.get(practiceIndex % library.words.size());
    ui.body(
        card,
        userMode
            ? "Listen to the word, then type it below."
            : "Read the Braille below, then type the matching word.");
    if (!userMode) ui.add(card, new BrailleCellsView(ui, target, true), 20);
    EditText answer =
        ui.field(
            card,
            "Your answer",
            "Type the word",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    answer.setFilters(new InputFilter[] {new InputFilter.LengthFilter(8)});
    ui.action(
        card,
        "Check answer",
        R.drawable.ic_constellate_check,
        true,
        () -> {
          if (answer.getText().toString().trim().isEmpty()) {
            answer.setError("Type your answer first.");
            return;
          }
          if (ConstellateRules.correctAnswer(answer.getText().toString(), target)) {
            practiceIndex++;
            hideKeyboard();
            render();
            notifyUser("That’s right! Ready for your next word.");
          } else {
            answer.setError("Not quite. Take your time and try again.");
            notifyUser("Not quite. Try again.");
          }
        });
    ui.action(
        card,
        userMode ? "Listen to word" : "Listen to a hint",
        R.drawable.ic_constellate_volume,
        false,
        () -> speak(target));
  }

  private void settings() {
    heading("Settings");
    LinearLayout card = ui.panel(content);
    ui.title(card, userMode ? "High-contrast appearance" : "Dark appearance", 18);
    ui.body(card, userMode ? "Enabled for User mode." : "A softer screen for low-light learning.");
    androidx.appcompat.widget.SwitchCompat toggle =
        new androidx.appcompat.widget.SwitchCompat(this);
    toggle.setText("Dark appearance");
    toggle.setTextColor(ui.ink);
    toggle.setChecked(dark || userMode);
    toggle.setEnabled(!userMode);
    toggle.setMinHeight(ui.dp(54));
    toggle.setOnCheckedChangeListener(
        (b, on) -> {
          dark = on;
          preferences().edit().putBoolean("dark", on).apply();
          render();
        });
    ui.add(card, toggle, 12);
    ui.action(
        card,
        "Change your experience",
        R.drawable.ic_constellate_person,
        false,
        () -> go("identity"));
    ui.action(card, "Help & voice guide", R.drawable.ic_constellate_help, false, () -> go("help"));
    ui.rule(card);
    ui.body(card, auth.getCurrentUser().getEmail());
    ui.action(card, "Log out", R.drawable.ic_constellate_logout, false, this::confirmLogout);
  }

  private void openWifi() {
    if (!busy) startActivityForResult(new Intent(this, MainActivity.class), WIFI_SETUP);
  }

  private void confirmLogout() {
    new AlertDialog.Builder(this)
        .setTitle("Log out?")
        .setMessage("Your account’s saved preferences and local words will remain on this phone.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Log out",
            (d, w) -> {
              if (tts != null) tts.stop();
              if (library != null) {
                library.close();
                library = null;
              }
              BluetoothSession.disconnect();
              auth.signOut();
              accountUid = "";
              experience = "";
              userMode = false;
              dark = false;
              selected = "";
              query = "";
              draft = "";
              page = "identity";
              render();
            })
        .show();
  }

  private void speak(String text) {
    if (text == null || text.isEmpty()) return;
    if (!ttsReady || tts == null) {
      notifyUser(
          "Speech is unavailable. Install an English text-to-speech voice in Android settings.");
      return;
    }
    if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "constellate_speech") == TextToSpeech.ERROR)
      notifyUser("Unable to speak. Check your text-to-speech settings.");
  }

  private void dictate() {
    if(tts!=null)tts.stop();
    hideKeyboard();
    if(voiceSearch==null)voiceSearch=new VoiceSearch(this,this::acceptVoiceSearch);
    voiceSearch.start(VOICE_SEARCH);
  }

  private void acceptVoiceSearch(String text) {
    clearVoiceWordPrompt();
    String value=ConstellateRules.word(text).trim().replaceAll("^[^a-z]+|[^a-z]+$", "");
    if(value.isEmpty()){notifyUser("No English word heard. Try Voice search again.");return;}
    page="library";query=value;render();
    if(!userMode&&page.equals("library"))assistantLibrary().search(value);
    pendingVoiceWord = value;
    pendingVoiceLibrary = library;
    checkVoiceSearchResult();
  }

  private void clearVoiceWordPrompt() {
    pendingVoiceWord = "";
    pendingVoiceLibrary = null;
    if (voiceWordDialog != null) {
      voiceWordDialog.dismiss();
      voiceWordDialog = null;
    }
  }

  private void checkVoiceSearchResult() {
    if (pendingVoiceWord.isEmpty()) return;
    if (pendingVoiceLibrary != library || !page.equals("library")) {
      clearVoiceWordPrompt();
      return;
    }
    // Wait for the actual library; a loading or failed request is not an empty result.
    if (library == null || !library.loaded || library.error != null) return;
    String value = pendingVoiceWord;
    pendingVoiceWord = "";
    pendingVoiceLibrary = null;
    for (String word : library.words) if (word.contains(value)) return;
    if (!ConstellateRules.validWord(value)) {
      notifyUser("No matching words. To add a word, use 1–8 English letters without spaces or numbers.");
      return;
    }
    ConstellateWords target = library;
    AssistantLibrary assistant = userMode ? null : assistantLibrary();
    LinearLayout body = ui.column();
    ui.padding(body, 24, 24);
    body.setBackground(ui.shape(ui.surface, ui.line, 24, userMode ? 2 : 0));
    ui.title(body, "Add to word library?", userMode ? 24 : 22);
    ui.body(body, "No matching words for “" + value + "”. Would you like to add it to your word library?");
    AlertDialog dialog = new AlertDialog.Builder(this).setView(body).create();
    voiceWordDialog = dialog;
    LinearLayout add = ui.action(body, "Add word", R.drawable.ic_constellate_plus, true, () -> {
      if (library != target) { dialog.dismiss(); return; }
      dialog.dismiss();
      target.write(value, false, ok -> {
        if (isDestroyed() || target != library) return;
        if (ok) {
          selected = value;
          if (assistant != null) assistant.includeVoiceWord(value);
          if (page.equals("library")) changed();
          notifyUser(value + " added to your library.");
        } else notifyUser("Could not save the word. Try again when connected.");
      });
    });
    if (!userMode) {
      add.setBackground(ui.shape(ui.accent, ui.accent, 12, 0));
      ((ImageView) add.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
      ((TextView) add.getChildAt(1)).setTextColor(android.graphics.Color.WHITE);
    }
    ui.action(body, "Cancel", 0, false, dialog::dismiss);
    dialog.setOnDismissListener(d -> { if (voiceWordDialog == dialog) voiceWordDialog = null; });
    dialog.show();
    if (dialog.getWindow() != null)
      dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
  }

  @Override
  public void onRequestPermissionsResult(int request,@NonNull String[] permissions,@NonNull int[] grants){
    super.onRequestPermissionsResult(request,permissions,grants);
    if(request==VoiceSearch.MICROPHONE_PERMISSION&&voiceSearch!=null){
      voiceSearch.permissionResult(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED);
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == WIFI_SETUP && result == RESULT_OK) go("home");
    if (request == VOICE_SEARCH && result == RESULT_OK && data != null) {
      ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (values != null && !values.isEmpty()) {
        acceptVoiceSearch(values.get(0));
      }
    } else if(request==VOICE_SEARCH && result!=RESULT_CANCELED && result!=RESULT_OK){
      if(voiceSearch==null)voiceSearch=new VoiceSearch(this,this::acceptVoiceSearch);
      voiceSearch.startService();
    }
  }

  @Override
  public void changed() {
    if (isDestroyed()) return;
    if (library != null && !selected.isEmpty() && !library.words.contains(selected)) selected = "";
    if (page.equals("library")) {
      if (!userMode && assistantLibrary != null) assistantLibrary.changed();
      else { updateWordRows(); updatePreview(); }
    }
    if (page.equals("practice") && waitingPractice) render();
    checkVoiceSearchResult();
  }

  @Override
  public void failed(String message) {
    notifyUser(message);
    if (libraryState != null) {
      libraryState.setText(message);
      libraryState.setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("page", page);
    out.putString("experience", experience);
    out.putString("selected", selected);
    out.putString("query", query);
    out.putString("draft", draft);
    out.putInt("tutorial", tutorialStep);
    out.putInt("practice", practiceIndex);
    out.putBoolean("from_settings", fromSettings);
    if (assistantLibrary != null) assistantLibrary.save(out);
  }

  @Override
  public void onBackPressed() {
    if (busy) return;
    if (page.equals("library") && !userMode && assistantLibrary != null && assistantLibrary.back()) return;
    if (page.equals("signup")) go("login");
    else if (page.equals("login")) go("identity");
    else if (tabPage() && !page.equals("home")) go("home");
    else if (page.equals("pairing")) go("home");
    else if (page.equals("tutorial")) go("home");
    else if (page.equals("identity") && signedIn()) confirmLogout();
    else super.onBackPressed();
  }

  @Override
  protected void onStart() {
    super.onStart();
    BluetoothSession.observe(boardConnectionObserver);
    updateBoardConnection();
    foreground = true;
    if (auth != null) auth.addAuthStateListener(authListener);
    maybeAutoSpeakGuide();
    if (page.equals("library") && !userMode && assistantLibrary != null) render();
  }

  @Override
  protected void onStop() {
    clearVoiceWordPrompt();
    if(voiceSearch!=null)voiceSearch.stop();
    BluetoothSession.unobserve(boardConnectionObserver);
    if (assistantLibrary != null) assistantLibrary.stop();
    foreground = false;
    if (tts != null) tts.stop();
    lastAutoGuide = "";
    if (auth != null) auth.removeAuthStateListener(authListener);
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if(voiceSearch!=null)voiceSearch.stop();
    if (assistantLibrary != null) assistantLibrary.stop();
    root.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
    authHandler.removeCallbacksAndMessages(null);
    if (library != null) library.close();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    super.onDestroy();
  }
}

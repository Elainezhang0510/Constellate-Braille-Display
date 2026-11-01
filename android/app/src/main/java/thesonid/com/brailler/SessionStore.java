package thesonid.com.brailler;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Local session for logged-in board (no password stored). */
final class SessionStore {

    private static final String PREFS = "braillio_session";
    private static final String KEY_BOARD_ID = "board_id";
    private static final String KEY_BOARD_NAME = "board_name";
    private static final String KEY_REMEMBER_BOARD = "remember_board";
    private static final String KEY_REMEMBERED_BOARD_ID = "remembered_board_id";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private SessionStore() {
    }

    private static SharedPreferences prefs(Context context) {
        FirebaseUser account = FirebaseProvider.isConfigured(context)
                ? FirebaseAuth.getInstance().getCurrentUser() : null;
        // Never let a second email account inherit the previous account's board.
        String scope = account == null ? "signed_out" : account.getUid();
        return context.getApplicationContext().getSharedPreferences(PREFS + "_" + scope, Context.MODE_PRIVATE);
    }

    static void saveSession(Context context, String boardId) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(KEY_BOARD_ID, boardId);
        if (getRememberBoard(context)) {
            editor.putString(KEY_REMEMBERED_BOARD_ID, boardId);
        }
        if (!boardId.equals(getBoardId(context)) || TextUtils.isEmpty(prefs(context).getString(KEY_BOARD_NAME, null))) {
            editor.putString(KEY_BOARD_NAME, "Board " + boardId);
        }
        editor.apply();
    }

    static String getBoardId(Context context) {
        return prefs(context).getString(KEY_BOARD_ID, null);
    }

    static String getBoardName(Context context) {
        String name = prefs(context).getString(KEY_BOARD_NAME, null);
        String id = getBoardId(context);
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        return id == null ? "Board" : "Board " + id;
    }

    static void setBoardName(Context context, String name) {
        prefs(context).edit().putString(KEY_BOARD_NAME, name.trim()).apply();
    }

    static boolean isLoggedIn(Context context) {
        return !TextUtils.isEmpty(getBoardId(context));
    }

    static void clearSession(Context context) {
        prefs(context).edit().remove(KEY_BOARD_ID).apply();
    }

    static void setRememberBoard(Context context, boolean remember) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putBoolean(KEY_REMEMBER_BOARD, remember);
        if (!remember) {
            editor.remove(KEY_REMEMBERED_BOARD_ID);
        }
        editor.apply();
    }

    static boolean getRememberBoard(Context context) {
        return prefs(context).getBoolean(KEY_REMEMBER_BOARD, true);
    }

    static String getRememberedBoardId(Context context) {
        if (!getRememberBoard(context)) {
            return null;
        }
        return prefs(context).getString(KEY_REMEMBERED_BOARD_ID, null);
    }

    static boolean isOnboardingDone(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    static void setOnboardingDone(Context context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
    }
}

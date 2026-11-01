package thesonid.com.brailler;

import android.content.Context;
import android.text.TextUtils;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

final class FirebaseProvider {

    private FirebaseProvider() {
    }

    static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    static FirebaseDatabase getDatabase(Context context) {
        if (!isConfigured(context)) {
            throw new IllegalStateException(
                    "Firebase is not configured. Add app/google-services.json and sync Gradle.");
        }

        String databaseUrl = context.getString(R.string.firebase_database_url);
        if (!TextUtils.isEmpty(databaseUrl)) {
            return FirebaseDatabase.getInstance(databaseUrl);
        }
        return FirebaseDatabase.getInstance();
    }
}

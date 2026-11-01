package thesonid.com.brailler;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    private static final int[] TITLES = {
            R.string.onboarding_title_1,
            R.string.onboarding_title_2,
            R.string.onboarding_title_3
    };
    private static final int[] BODIES = {
            R.string.onboarding_body_1,
            R.string.onboarding_body_2,
            R.string.onboarding_body_3
    };

    private int page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        TextView title = findViewById(R.id.onboarding_title);
        TextView body = findViewById(R.id.onboarding_body);
        TextView pageLabel = findViewById(R.id.onboarding_page);
        Button next = findViewById(R.id.onboarding_next);

        Runnable render = () -> {
            title.setText(TITLES[page]);
            body.setText(BODIES[page]);
            pageLabel.setText(getString(R.string.onboarding_page_label, page + 1, TITLES.length));
            next.setText(page == TITLES.length - 1
                    ? R.string.onboarding_done
                    : R.string.onboarding_next);
        };
        render.run();

        next.setOnClickListener(v -> {
            if (page < TITLES.length - 1) {
                page++;
                render.run();
            } else {
                finishOnboarding();
            }
        });
        findViewById(R.id.onboarding_skip).setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        SessionStore.setOnboardingDone(this);
        startActivity(new Intent(this, MainActivityBraille.class));
        finish();
    }
}

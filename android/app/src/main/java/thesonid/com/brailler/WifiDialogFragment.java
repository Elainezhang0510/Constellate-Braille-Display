package thesonid.com.brailler;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

public class WifiDialogFragment extends AppCompatDialogFragment {

    private static final String TAG = "WifiDialogFragment";
    public static final String WIFI_RESULT_KEY = "wifi_config_result";

    private EditText ssidEditText;
    private EditText pwdEditText;
    private boolean isValidInput;

    public static WifiDialogFragment newInstance() {
        return new WifiDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_design, null);
        ssidEditText = view.findViewById(R.id.SSID);
        pwdEditText = view.findViewById(R.id.password);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(view)
                .setTitle(R.string.dialog_message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.start_configuration, null)
                .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            ssidEditText.setError(getString(R.string.error_ssid_empty));
            ssidEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    isValidInput = s.length() > 0;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isValidInput);
                    if (!isValidInput) {
                        ssidEditText.setError(getString(R.string.error_ssid_empty));
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!isValidInput) {
                    return;
                }
                Bundle result = new Bundle();
                result.putString("SSID", ssidEditText.getText().toString());
                result.putString("PWD", pwdEditText.getText().toString());
                FragmentManager manager = getParentFragmentManager();
                manager.setFragmentResult(WIFI_RESULT_KEY, result);
                dialog.dismiss();
            });
        });

        return dialog;
    }
}

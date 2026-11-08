package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment() {
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button microsoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button localButton = view.findViewById(R.id.button_local_authentication);
        Button elyByButton = view.findViewById(R.id.button_elyby_authentication);

        microsoftButton.setOnClickListener(v -> Tools.swapFragment(
                requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));

        localButton.setOnClickListener(v -> Tools.swapFragment(
                requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null));

        // Ely.by's game authentication needs its own Yggdrasil/Authlib integration.
        // Until that flow is implemented, open the official service rather than
        // presenting a non-functional in-app login form or collecting credentials.
        elyByButton.setOnClickListener(v -> Tools.openURL(
                requireActivity(), "https://ely.by/"));
    }
}

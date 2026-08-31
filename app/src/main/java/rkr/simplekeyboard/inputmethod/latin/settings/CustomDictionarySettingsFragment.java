package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.dict.CustomDictionaryManager;

public final class CustomDictionarySettingsFragment extends SubScreenFragment {
    private static final String TAG = "CustomDictSettings";

    private PreferenceCategory mInstalledCategory;
    private ActivityResultLauncher<String[]> mFilePickerLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onFilePicked
        );
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        final Context context = requireContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.setTitle(R.string.custom_dictionaries_title);

        mInstalledCategory = new PreferenceCategory(context);
        mInstalledCategory.setTitle(R.string.installed_custom_dictionaries_title);
        screen.addPreference(mInstalledCategory);

        final PreferenceCategory actionsCategory = new PreferenceCategory(context);
        actionsCategory.setTitle(R.string.dictionary_actions_title);
        screen.addPreference(actionsCategory);

        final Preference importPref = new Preference(context);
        importPref.setTitle(R.string.import_dictionary_title);
        importPref.setSummary(R.string.import_dictionary_summary);
        importPref.setOnPreferenceClickListener(p -> {
            launchFilePicker();
            return true;
        });
        actionsCategory.addPreference(importPref);

        final Preference downloadRepoPref = new Preference(context);
        downloadRepoPref.setTitle(R.string.download_repo_dictionaries_title);
        downloadRepoPref.setSummary(R.string.download_repo_dictionaries_summary);
        downloadRepoPref.setWidgetLayoutResource(R.layout.preference_external_link);
        downloadRepoPref.setOnPreferenceClickListener(p -> {
            openDownloadUrl(R.string.dictionaries_download_url);
            return true;
        });
        actionsCategory.addPreference(downloadRepoPref);

        final Preference downloadHeliumPref = new Preference(context);
        downloadHeliumPref.setTitle(R.string.download_helium314_dictionaries_title);
        downloadHeliumPref.setSummary(R.string.download_helium314_dictionaries_summary);
        downloadHeliumPref.setWidgetLayoutResource(R.layout.preference_external_link);
        downloadHeliumPref.setOnPreferenceClickListener(p -> {
            openDownloadUrl(R.string.dictionaries_helium314_download_url);
            return true;
        });
        actionsCategory.addPreference(downloadHeliumPref);

        setPreferenceScreen(screen);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshInstalledDictionaries();
    }

    private void launchFilePicker() {
        if (mFilePickerLauncher != null) {
            try {
                mFilePickerLauncher.launch(new String[]{"*/*"});
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch document picker", e);
                Toast.makeText(getContext(), R.string.file_picker_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onFilePicked(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }

        Toast.makeText(context, R.string.importing_dictionary_progress, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            final CustomDictionaryManager.ImportResult result =
                    CustomDictionaryManager.getInstance().importDictionary(context, uri, null);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (result.success) {
                        final String displayLang = getDisplayNameForLocale(result.languageCode);
                        final String msg = getString(R.string.import_dictionary_success, displayLang, result.wordCount);
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                        refreshInstalledDictionaries();
                    } else {
                        final String msg = getString(R.string.import_dictionary_failed, result.message);
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void openDownloadUrl(@StringRes final int urlResId) {
        try {
            final String url = getString(urlResId);
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open download url: " + urlResId, e);
        }
    }

    private void refreshInstalledDictionaries() {
        final Context context = getContext();
        if (context == null || mInstalledCategory == null) {
            return;
        }

        mInstalledCategory.removeAll();
        final List<CustomDictionaryManager.CustomDictInfo> list =
                CustomDictionaryManager.getInstance().getInstalledDictionaries(context);

        if (list.isEmpty()) {
            final Preference emptyPref = new Preference(context);
            emptyPref.setTitle(R.string.no_custom_dictionaries_title);
            emptyPref.setSummary(R.string.no_custom_dictionaries_summary);
            emptyPref.setSelectable(false);
            mInstalledCategory.addPreference(emptyPref);
            return;
        }

        for (final CustomDictionaryManager.CustomDictInfo info : list) {
            final Preference pref = new Preference(context);
            final String displayLang = getDisplayNameForLocale(info.languageCode);
            pref.setTitle(displayLang + " (" + info.languageCode + ")");
            final String sizeMb = String.format(Locale.US, "%.1f MB", info.fileSizeBytes / (1024.0 * 1024.0));
            pref.setSummary(getString(R.string.custom_dict_item_summary, info.wordCount, sizeMb));
            pref.setWidgetLayoutResource(R.layout.preference_chevron);
            pref.setOnPreferenceClickListener(p -> {
                showDeleteDialog(info);
                return true;
            });
            mInstalledCategory.addPreference(pref);
        }
    }

    private void showDeleteDialog(final CustomDictionaryManager.CustomDictInfo info) {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        final String displayLang = getDisplayNameForLocale(info.languageCode);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.remove_custom_dict_title)
                .setMessage(getString(R.string.remove_custom_dict_message, displayLang))
                .setPositiveButton(R.string.remove_custom_dict_confirm, (dialog, which) -> {
                    CustomDictionaryManager.getInstance().deleteCustomDictionary(context, info.languageCode);
                    Toast.makeText(context, getString(R.string.custom_dict_removed_toast, displayLang), Toast.LENGTH_SHORT).show();
                    refreshInstalledDictionaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getDisplayNameForLocale(final String languageCode) {
        try {
            final Locale loc = new Locale(languageCode);
            final String name = loc.getDisplayName(Locale.getDefault());
            if (name != null && !name.isEmpty()) {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        } catch (Exception ignored) {
        }
        return languageCode.toUpperCase(Locale.ROOT);
    }
}

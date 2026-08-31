/*
 * Copyright (C) 2026 Simple Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardLayoutSet;
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager;

public final class BackupSettingsFragment extends SubScreenFragment {
    private static final String TAG = BackupSettingsFragment.class.getSimpleName();

    private final AtomicBoolean mIsProcessing = new AtomicBoolean(false);
    private ActivityResultLauncher<String> mExportLauncher;
    private ActivityResultLauncher<String[]> mImportLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                this::onExportUriReceived
        );

        mImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onImportUriReceived
        );
    }

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.prefs_screen_backup, rootKey);

        final Preference exportPref = findPreference("pref_backup_export");
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(preference -> {
                if (mIsProcessing.get()) {
                    return true;
                }
                launchExportPicker();
                return true;
            });
        }

        final Preference restorePref = findPreference("pref_backup_restore");
        if (restorePref != null) {
            restorePref.setOnPreferenceClickListener(preference -> {
                if (mIsProcessing.get()) {
                    return true;
                }
                showRestoreConfirmationDialog();
                return true;
            });
        }
    }

    private void launchExportPicker() {
        final String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        final String defaultFilename = "simple-keyboard-backup-" + dateStr + ".json";
        try {
            mExportLauncher.launch(defaultFilename);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch export document picker", e);
            Toast.makeText(getContext(), R.string.file_picker_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRestoreConfirmationDialog() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.backup_confirm_restore_title)
                .setMessage(R.string.backup_confirm_restore_message)
                .setPositiveButton(R.string.pref_backup_restore_title, (dialog, which) -> {
                    try {
                        mImportLauncher.launch(new String[]{"application/json", "text/*", "*/*"});
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch import document picker", e);
                        Toast.makeText(context, R.string.file_picker_error, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onExportUriReceived(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }

        if (!mIsProcessing.compareAndSet(false, true)) {
            return;
        }

        Toast.makeText(context, R.string.backup_in_progress, Toast.LENGTH_SHORT).show();

        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            boolean success = false;
            String errorMsg = null;

            try (final OutputStream os = appContext.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    BackupHelper.exportToStream(getSharedPreferences(), os);
                    success = true;
                } else {
                    errorMsg = "Unable to open output destination stream.";
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to export backup", e);
                errorMsg = e.getMessage();
            } finally {
                final boolean finalSuccess = success;
                final String finalErrorMsg = errorMsg;

                final androidx.fragment.app.FragmentActivity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        mIsProcessing.set(false);
                        final Context activeContext = getContext();
                        if (!isAdded() || activeContext == null) {
                            return;
                        }
                        if (finalSuccess) {
                            Toast.makeText(activeContext, R.string.backup_export_success, Toast.LENGTH_SHORT).show();
                        } else {
                            final String displayMsg = getString(R.string.backup_export_error, finalErrorMsg != null ? finalErrorMsg : "Unknown error");
                            Toast.makeText(activeContext, displayMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    mIsProcessing.set(false);
                }
            }
        }).start();
    }

    private void onImportUriReceived(@Nullable final Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }

        if (!mIsProcessing.compareAndSet(false, true)) {
            return;
        }

        Toast.makeText(context, R.string.backup_in_progress, Toast.LENGTH_SHORT).show();

        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            BackupHelper.ValidationResult result;

            try (final InputStream is = appContext.getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    result = BackupHelper.validateAndParseStream(is);
                } else {
                    result = BackupHelper.ValidationResult.error("Unable to open input stream.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read import file", e);
                result = BackupHelper.ValidationResult.error(e.getMessage() != null ? e.getMessage() : "Unknown I/O error");
            }

            final BackupHelper.ValidationResult finalResult = result;

            if (finalResult.success) {
                BackupHelper.applyValidatedBackup(getSharedPreferences(), finalResult);
                RichInputMethodManager.getInstance().reloadSubtypes(appContext);
                KeyboardLayoutSet.clearKeyboardCache();
                Settings.getInstance().onSharedPreferenceChanged(getSharedPreferences(), null);
            }

            final androidx.fragment.app.FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    mIsProcessing.set(false);
                    final Context activeContext = getContext();
                    if (!isAdded() || activeContext == null) {
                        return;
                    }
                    if (finalResult.success) {
                        final String msg = getString(R.string.backup_restore_success, finalResult.validEntriesCount);
                        Toast.makeText(activeContext, msg, Toast.LENGTH_LONG).show();
                    } else {
                        final String errorMsg = finalResult.errorMessage != null ? finalResult.errorMessage : "Validation failed.";
                        final String displayMsg = getString(R.string.backup_restore_error, errorMsg);
                        new MaterialAlertDialogBuilder(activeContext)
                                .setTitle(R.string.backup_confirm_restore_title)
                                .setMessage(displayMsg)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                });
            } else {
                mIsProcessing.set(false);
            }
        }).start();
    }
}

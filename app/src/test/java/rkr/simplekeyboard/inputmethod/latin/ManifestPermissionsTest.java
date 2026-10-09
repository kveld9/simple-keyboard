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

package rkr.simplekeyboard.inputmethod.latin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression guard ported from the Morphe Gboard permission-strip idea: the
 * keyboard must stay offline and permission-lean. Fails on any newly declared
 * sensitive permission so additions require an explicit test update.
 */
@RunWith(JUnit4.class)
public class ManifestPermissionsTest {

    private static final String MANIFEST_PATH = "src/main/AndroidManifest.xml";
    private static final String BACKUP_RULES_PATH = "src/main/res/xml/backup_rules.xml";

    private static String readFile(final String path) throws Exception {
        final File file = new File(path);
        assertTrue(path + " must exist (working dir: "
                + new File(".").getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void testNoInternetPermission() throws Exception {
        assertFalse("INTERNET permission must never be declared",
                readFile(MANIFEST_PATH).contains("android.permission.INTERNET"));
    }

    @Test
    public void testNoSensitivePermissions() throws Exception {
        final String manifest = readFile(MANIFEST_PATH);
        final String[] denied = {
                "android.permission.RECORD_AUDIO",
                "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.GET_ACCOUNTS",
                "android.permission.READ_SMS",
                "android.permission.ACCESS_NETWORK_STATE"
        };
        for (final String permission : denied) {
            assertFalse(permission + " must never be declared", manifest.contains(permission));
        }
    }

    @Test
    public void testExpectedPermissionsPresent() throws Exception {
        final String manifest = readFile(MANIFEST_PATH);
        assertTrue(manifest.contains("android.permission.VIBRATE"));
        assertTrue(manifest.contains("android.permission.READ_MEDIA_IMAGES"));
    }

    @Test
    public void testClipboardExcludedFromCloudBackup() throws Exception {
        final String rules = readFile(BACKUP_RULES_PATH);
        assertTrue(rules.contains("clipboard_history.db"));
    }
}

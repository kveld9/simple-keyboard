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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class BackupFileRotationTest {

    @Test
    public void testGeneratedNameMatchesConvention() {
        final String name = BackupHelper.generateBackupFileName(0L);
        assertTrue(name.matches("simplekeyboard_backup_\\d{8}_\\d{6}\\.json"));
    }

    @Test
    public void testGeneratedNamesIncreaseWithTime() {
        final String first = BackupHelper.generateBackupFileName(1000L);
        final String second = BackupHelper.generateBackupFileName(1000000L);
        assertTrue(first.compareTo(second) < 0);
    }

    @Test
    public void testIsBackupFile() {
        assertTrue(BackupHelper.isBackupFile("simplekeyboard_backup_20260101_120000.json"));
        assertFalse(BackupHelper.isBackupFile("settings.json"));
        assertFalse(BackupHelper.isBackupFile(null));
        assertFalse(BackupHelper.isBackupFile("simplekeyboard_backup_notes.txt"));
    }

    @Test
    public void testRotationKeepsNewest() {
        final List<String> oldestFirst = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            oldestFirst.add("simplekeyboard_backup_202601" + (i < 10 ? "0" + i : i) + "_120000.json");
        }
        final List<String> victims =
                BackupHelper.selectBackupsToDelete(oldestFirst, BackupHelper.MAX_AUTO_BACKUPS);
        assertEquals(2, victims.size());
        assertEquals(oldestFirst.get(0), victims.get(0));
        assertEquals(oldestFirst.get(1), victims.get(1));
    }

    @Test
    public void testNoDeletionWithinLimit() {
        final List<String> names = Arrays.asList(
                "simplekeyboard_backup_20260101_120000.json",
                "simplekeyboard_backup_20260102_120000.json");
        assertTrue(BackupHelper.selectBackupsToDelete(names, BackupHelper.MAX_AUTO_BACKUPS).isEmpty());
        assertTrue(BackupHelper.selectBackupsToDelete(
                Collections.<String>emptyList(), BackupHelper.MAX_AUTO_BACKUPS).isEmpty());
        assertTrue(BackupHelper.selectBackupsToDelete(null, BackupHelper.MAX_AUTO_BACKUPS).isEmpty());
    }

    @Test
    public void testNonBackupFilesAreNeverSelected() {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            names.add("random_file_" + i + ".json");
        }
        names.add("simplekeyboard_backup_20260101_120000.json");
        assertEquals(1, BackupHelper.selectBackupsToDelete(names, 0).size());
        assertTrue(BackupHelper.selectBackupsToDelete(names, BackupHelper.MAX_AUTO_BACKUPS).isEmpty());
    }

    @Test
    public void testZeroKeepDeletesAllBackups() {
        final List<String> names = Arrays.asList(
                "simplekeyboard_backup_20260101_120000.json",
                "simplekeyboard_backup_20260102_120000.json");
        assertEquals(2, BackupHelper.selectBackupsToDelete(names, 0).size());
    }

    @Test
    public void testNegativeKeepThrows() {
        try {
            BackupHelper.selectBackupsToDelete(
                    Collections.singletonList("simplekeyboard_backup_20260101_120000.json"), -1);
            fail("expected IllegalArgumentException");
        } catch (final IllegalArgumentException expected) {
        }
    }
}

# Informe de Auditoría de Complejidad Ciclomática y Deduplicación
## Área 5: Data Persistence & Settings / Utils

### 1. Tabla de Complejidad Ciclomática (CC)

| Archivo | Método / Líneas | CC Actual | Objetivo |
|---|---|---|---|
| `UserDictionaryDatabase.java` | [`getNextWordPredictions`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/UserDictionaryDatabase.java#L369-L425) | 23 | <= 5 |
| `UserDictionaryDatabase.java` | [`onUpgrade`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/UserDictionaryDatabase.java#L93-L147) | 16 | <= 5 |
| `UserDictionaryDatabase.java` | [`learnBigram`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/UserDictionaryDatabase.java#L250-L291) | 11 | <= 5 |
| `ClipboardHistoryManager.java`| [`onPrimaryClipChanged`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryManager.java#L144-L173) | 10 | <= 5 |
| `ClipboardHistoryManager.java`| [`getLatestClipText`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryManager.java#L68-L99) | 10 | <= 5 |
| `Settings.java` | [`loadRestrictions`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java#L146-L211) | 10 | <= 5 |
| `ClipboardDatabase.java` | [`cleanupOldClips`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardDatabase.java#L103-L129) | 9 | <= 5 |
| `UserDictionaryDatabase.java` | [`learnWord`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/UserDictionaryDatabase.java#L166-L210) | 9 | <= 5 |

### 2. Causas Exactas de Complejidad y Duplicación

- **Duplicación directa de lógica en Clipboard:** `getLatestClipText()` y `onPrimaryClipChanged()` en `ClipboardHistoryManager.java` repiten ~30 líneas de lógica idéntica de extracción de texto y actualización en base de datos.
- **Nesting profundo de cursores en SQLite:** `getNextWordPredictions()` en `UserDictionaryDatabase` tiene tres bloques `try-with-resources` secuenciales con bucles `while (cursor.moveToNext())` duplicados.
- **Iteraciones manuales en SQLite:** `ClipboardDatabase.cleanupOldClips()` itera un cursor manualmente con un `StringBuilder` para armar un `IN (id1, id2...)`.

### 3. Propuesta Concreta de Refactorización

- **Consolidación en `ClipboardHistoryManager`:** Extraer la lógica común de lectura y purgado a `processPrimaryClip(ClipData clip)` compartido por `getLatestClipText` y `onPrimaryClipChanged`.
- **Modernización SQL en `ClipboardDatabase`:** Reemplazar el bucle manual en `cleanupOldClips` con un `DELETE FROM clips WHERE id NOT IN (SELECT id FROM clips ORDER BY timestamp DESC LIMIT 50)`.
- **Extracción en `UserDictionaryDatabase`:** Extraer la lectura de cursores en `getNextWordPredictions` a `fetchWordsFromQuery(query, args)`.

### 4. Checklist Interactiva de Seguimiento

- [x] `getNextWordPredictions` CORREGIDO (CC 23 -> 5)
- [x] `onUpgrade` en `UserDictionaryDatabase` CORREGIDO (CC 16 -> 3)
- [x] `learnBigram` y `learnWord` CORREGIDO (CC 11 -> 4 y CC 9 -> 4)
- [x] `onPrimaryClipChanged` y `getLatestClipText` CORREGIDO (Deduplicación, CC 10 -> 3)
- [x] `cleanupOldClips` CORREGIDO (CC 9 -> 2)
- [x] `loadRestrictions` CORREGIDO (CC 10 -> 3)

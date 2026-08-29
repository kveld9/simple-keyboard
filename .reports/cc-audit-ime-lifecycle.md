# Informe de Auditoría de Complejidad Ciclomática y Deduplicación
## Área 2: IME Lifecycle & Input Logic

### 1. Tabla de Complejidad Ciclomática (CC)

| Archivo | Método | CC Antes | CC Después | Estado | Enlace a Código |
|---|---|---|---|---|---|
| `LatinIME.java` | `onEvent(final Event event)` | 34 | 2 | CORREGIDO | [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java) |
| `LatinIME.java` | `updateSuggestions()` | 31 | 3 | CORREGIDO | [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java) |
| `LatinIME.java` | `onCodeInput(...)` | 7 | 1 | CORREGIDO | [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java) |
| `InputLogic.java` | `onCodeInput(...)` | 4 | 4 | OK (<= 5) | [InputLogic.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java) |
| `LatinIME.java` | `loadSettings()` | 2 | 2 | OK (<= 5) | [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java) |
| `InputLogic.java` | `onTextInput(...)` | 1 | 1 | OK (<= 5) | [InputLogic.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java) |

### 2. Causas Exactas de Complejidad y Deduplicaciones Aplicadas

- **Obtención de palabra previa (Cursor Handling):** Se deduplicó la obtención de la palabra previa del cursor mediante la función helper `getEffectivePreviousWord()` (CC 3), utilizada en `updateSuggestions()`, `onSuggestionClicked()`, `handleSpaceAutoCorrect()`.
- **Evaluación de BeamSearchDecoder y Dictionary (State checks):** Centralizado en helpers `getSuggestionsForWord()` (CC 5), `getDecoderBestCorrection()` (CC 2) y `resolveBestCorrection()` (CC 5).
- **Manejo de Base de Datos y Aprendizaje:** Centralizado en `learnWordAsync(word, prevWord)` (CC 3) y `learnBigramAsync(prevWord, word)` (CC 4).
- **Modularización de `onEvent`:** Desacoplado en `closeToolTrayIfOpen()`, `handleBackspaceRevert()` (CC 4) y `handleSpaceAutoCorrect()` (CC 2).

### 3. Detalle de Unidades Extraídas y Métricas

- **`LatinIME.onCodeInput` (CC 7 -> 1):**
  - `feedBeamSearchDecoder(codePoint, x, y)` (CC 4)
  - `isDecoderResetKey(codePoint)` (CC 3)
- **`LatinIME.onEvent` (CC 34 -> 2):**
  - `closeToolTrayIfOpen()` (CC 3)
  - `handleBackspaceRevert(event)` (CC 4)
  - `isBackspaceEvent(event)` (CC 2)
  - `isNonSpaceNormalKey(event)` (CC 2)
  - `isAutocorrectRevertible()` (CC 3)
  - `tryExecuteBackspaceRevert(event)` (CC 3)
  - `executeBackspaceRevert(event)` (CC 1)
  - `handleSpaceAutoCorrect(event)` (CC 2)
  - `isSpaceEvent(event)` (CC 2)
  - `shouldPerformAutoCorrection(word)` (CC 3)
  - `getBestCorrection(word, prevWord)` (CC 2)
  - `applySpaceAutoCorrection(word, prevWord)` (CC 3)
  - `recordCommittedWord(committedWord, prevWord)` (CC 2)
- **`LatinIME.updateSuggestions` (CC 31 -> 3):**
  - `isSuggestionsDisabled()` (CC 5)
  - `isPasswordField()` (CC 2)
  - `displayEmptyWordSuggestions(prevWord)` (CC 3)
  - `displayClipboardChipIfAvailable()` (CC 3)
  - `displayNextWordPredictionsIfAvailable(prevWord)` (CC 4)
  - `displayComposingSuggestions(word, prevWord)` (CC 2)
  - `getSuggestionsForWord(word, prevWord)` (CC 5)
  - `getDecoderBestCorrection(word, prevWord)` (CC 2)
  - `resolveBestCorrection(word, prevWord, hasMatches)` (CC 5)
  - `appendCorrectionAndCandidates(...)` (CC 5)
  - `appendMatchingSuggestions(...)` (CC 3)
- **Helpers de Deduplicación:**
  - `getEffectivePreviousWord()` (CC 3)
  - `isWordEmpty(word)` (CC 2)
  - `learnWordAsync(word, prevWord)` (CC 3)
  - `learnBigramAsync(prevWord, word)` (CC 4)

### 4. Checklist Interactiva de Seguimiento

- [x] `LatinIME.onEvent` CORREGIDO (CC 34 -> 2)
- [x] `LatinIME.updateSuggestions` CORREGIDO (CC 31 -> 3)
- [x] `LatinIME.onCodeInput` CORREGIDO (CC 7 -> 1)

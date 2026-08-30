# Auditoría Pro de Complejidad Ciclomática - Área 4

Este informe detalla las funciones con Complejidad Ciclomática (CC) crítica (>= 6) en los módulos de Subtypes, InputConnection y InputLogic (Área 4), sus causas raíz y las tácticas concretas para reducirlas a un CC seguro (<= 5) garantizando paridad total funcional.

## 1. Funciones Críticas Identificadas

| Archivo | Método | CC | Causa Exacta de la Complejidad |
|---------|--------|----|--------------------------------|
| [RichInputConnection.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java) | `getUnicodeSteps` | 22 | Lógica bidireccional anidada (`chars < 0` vs `chars > 0`) combinada con saltos y evaluación en línea de caracteres subrogados y `\u200d` en bucles. |
| [RichInputConnection.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java) | `reloadTextCache` | 14 | Múltiples ramas de API (SDK S+ vs pre-S) anidadas en un lambda asíncrono con múltiple validación de estado inline y logging de errores. |
| [RichInputConnection.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java) | `setSelection` | 10 | Chequeos múltiples de límites (bounds checking), validaciones del índice contra selecciones previas, y flujos if-else. |
| [RichInputConnection.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java) | `pasteClipboard` | 9 | Validación de portapapeles, extracciones múltiples para texto rico o simple, y bloque if-else con trim para reemplazo y cursor inline. |
| [RichInputConnection.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java) | `sendKeyEvent` | 9 | Validación de estado de conexión y dispatching de eventos. |
| [RichInputConnection.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java) | `getWordAfterCursor` | 7 | Bucle iterativo de extracción de texto y detección de separadores inline. |
| [RichInputMethodManager.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputMethodManager.java) | `getEnabledSubtypeInfoOfAllImes` | 8 | Instanciación inline de `Comparator`, múltiples iteraciones anidadas con validaciones continuas (auxiliar, implícito). |
| [RichInputMethodManager.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputMethodManager.java) | `showSubtypePicker` | 7 | Bucle `for` para crear títulos `Spannable` acoplado al listener que también usa otro bucle de búsqueda. |
| [RichInputMethodManager.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputMethodManager.java) | `switchToNextInputMethod` | 6 | Múltiples condiciones `if-else` concatenadas comprobando `Build.VERSION` y el token del IME, junto a búsqueda de IME inline. |
| [RichInputMethodManager.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputMethodManager.java) | `removeSubtype` | 6 | Doble bucle `for` anidado con checks en listas locales frente a listas compartidas. |
| [InputLogic.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java) | `handleFunctionalEvent` | 12 | Bloque `switch` gigante mapeando códigos funcionales a acciones del teclado. |
| [InputLogic.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java) | `performRecapitalization` | 7 | Acumulación de sentencias if defensivas y validaciones compuestas previas a la ejecución del reemplazo de texto. |
| [InputLogic.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java) | `handleBackspaceEvent` | 6 | Asignaciones ternarias compuestas combinadas con bloque `if-else` de tipos de deleción. |
| [AudioAndHapticFeedbackManager.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/AudioAndHapticFeedbackManager.java) | `performAudioFeedback` | 6 | Múltiples retornos tempranos seguidos de un bloque `switch` enumerando los `Constants.CODE_*`. |
| [AudioAndHapticFeedbackManager.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/AudioAndHapticFeedbackManager.java) | `vibratePredefined`, `performTickFeedback` | 6 | Validación defensiva encadenada (`mSettingsValues == null || !mSettingsValues.mVibrateOn || mVibrator == null`) combinada con saltos por API level. |

## 2. Tácticas Concretas de Refactorización (Target: CC <= 5)

### RichInputConnection.java
- **`getUnicodeSteps` (CC 22 -> 4):** Dividir la lógica de direcciones en `getUnicodeStepsLeft()` y `getUnicodeStepsRight()`. Extraer la evaluación condicional del carácter (`\u200d`, surrogates) a un método puro `isSkippableCharacter(char current, char adjacent, boolean rightDirection)`.
- **`reloadTextCache` (CC 14 -> 4):** 
  - Extraer la lógica completa del `Runnable` (lambda asíncrono) a una clase interna pura o método privado dedicado, ej. `runTextCacheReload()`.
  - Crear métodos separados `reloadTextCacheForSAndAbove()` y `reloadTextCacheLegacy()`. 
  - Centralizar el bloque defensivo `if (expectedSelStart != mExpectedSelStart)` en un método auxiliar `isSelectionValid()`.
- **`setSelection`, `pasteClipboard`, `sendKeyEvent`, `getWordAfterCursor`:** Centralizar aserciones previas de estado y límites a verificadores booleanos (`isValidSelectionBounds(...)`).

### RichInputMethodManager.java
- **`getEnabledSubtypeInfoOfAllImes` (CC 8 -> 4):**
  - Extraer el `Comparator<InputMethodInfo>` a una constante/clase separada o a un método privado generador.
  - Extraer el bucle interno de generación de `SubtypeInfo` a `buildVirtualSubtypesList(imi)` y `buildSystemSubtypesList(imi)`.
- **`showSubtypePicker` (CC 7 -> 3):** 
  - Separar la creación visual (`items[i] = ...`) a un método mapeador puro `formatSubtypeTitle(SubtypeInfo)`.
  - Modificar el bloque `OnClickListener` para usar `subtypeInfoList.get(position)` directamente, eliminando el bucle iterador de búsqueda interno.
- **`removeSubtype` y `switchToNextInputMethod`:** Aplicar *Guard Clauses* para retornos tempranos y extraer las búsquedas de IME a métodos de "búsqueda" dedicados en vez de hacerlo en línea.

### InputLogic.java
- **`handleFunctionalEvent` (CC 12 -> 3):** Reemplazar el inmenso bloque `switch(event.mKeyCode)` por un `Map<Integer, Consumer<InputTransaction>>` o estrategia de despachador funcional mapeada estáticamente en el inicializador.
- **`performRecapitalization` (CC 7 -> 3):** Consolidar los chequeos iniciales en un método `canRecapitalizeCurrentSelection()`. 
- **`handleBackspaceEvent` (CC 6 -> 3):** Extraer la resolución del `shiftUpdateKind` a `resolveBackspaceShiftUpdate(event)`.

### AudioAndHapticFeedbackManager.java
- **`performAudioFeedback` (CC 6 -> 2):** Implementar una tabla de mapeo unificada (ej. `SparseIntArray`) que ligue los `Constants.CODE_*` a `AudioManager.FX_*`, evitando el bloque switch.
- **`vibratePredefined` & `performTickFeedback` (CC 6 -> 3):** Consolidar los múltiples `||` de la validación inicial (null check de `mSettingsValues`, `mVibrateOn`, y `mVibrator`) en un único método `canHapticVibrate()`.

---

## 3. Checklist Interactiva de Implementación

- [x] CORREGIDO (CC 22 -> 3) `RichInputConnection.getUnicodeSteps` dividido en sub-métodos de navegación (`getUnicodeStepsLeft` [CC 3], `getUnicodeStepsRight` [CC 3], `shouldSkipCharLeft` [CC 4], `shouldSkipCharRight` [CC 4]).
- [x] CORREGIDO (CC 14 -> 2) `RichInputConnection.reloadTextCache` refactorizado aplicando Polimorfismo / delegación de API (`reloadTextCacheForSAndAbove` [CC 2], `reloadTextCacheLegacy` [CC 2], `fetchTextBeforeCursorLegacy` [CC 3], `fetchTextAfterCursorLegacy` [CC 3], `fetchSelectedTextLegacy` [CC 4], `isSelectionRangeModified` [CC 2]).
- [x] CORREGIDO (CC 6 -> 3) API Branches (SDK S+ vs Legacy) separados en métodos locales discretos en `RichInputConnection` (`shouldSkipReloadFromEditorInfo` [CC 2]).
- [x] CORREGIDO (CC 10 -> 4) `RichInputConnection.setSelection` refactorizado con guard clauses y helpers (`isInvalidSelectionBounds` [CC 4], `updateCachedTextForSelection` [CC 2], `canUpdateTextCachesForSelection` [CC 4]).
- [x] CORREGIDO (CC 9 -> 3) `RichInputConnection.pasteClipboard` refactorizado (`extractPrimaryClipText` [CC 4], `isTextMimeType` [CC 3]).
- [x] CORREGIDO (CC 9 -> 3) `RichInputConnection.sendKeyEvent` refactorizado (`handleKeyDownEvent` [CC 4], `handleEnterKeyDown` [CC 1], `handleUnknownKeyDown` [CC 2], `handleDeleteKeyDown` [CC 3], `handleDefaultKeyDown` [CC 1]).
- [x] CORREGIDO (CC 7 -> 3) `RichInputConnection.getWordAfterCursor` refactorizado (`isWordAfterCursorBoundaryChar` [CC 4]).
- [x] CORREGIDO (CC 12 -> 2) Bloques `switch` gigantes mapeados a colecciones u objetos Command (`InputLogic.handleFunctionalEvent` con tabla estática `SparseArray<FunctionalEventHandler>`).
- [x] CORREGIDO (CC 7 -> 3) `InputLogic.performRecapitalization` refactorizado (`canRecapitalize` [CC 3], `ensureRecapitalizeStarted` [CC 3], `applyRecapitalization` [CC 1]).
- [x] CORREGIDO (CC 6 -> 2) `InputLogic.handleBackspaceEvent` refactorizado (`resolveBackspaceShiftUpdateKind` [CC 3], `deleteCharacterBeforeCursor` [CC 3]).
- [x] CORREGIDO (CC 6 -> 2) Chequeos iterativos de `AudioAndHapticFeedbackManager.performAudioFeedback` centralizados con tabla estática `SparseIntArray` de FX de audio y `getSoundEffectForCode` [CC 1].
- [x] CORREGIDO (CC 6 -> 2 / 3) `AudioAndHapticFeedbackManager.vibratePredefined` y `performTickFeedback` centralizados en `canHapticVibrate()` [CC 3], `isTickThrottled()` [CC 1] y `triggerVibrationEffect()` [CC 3].
- [x] CORREGIDO (CC 8 -> 3) Comparadores anidados extraídos a la capa estática en `RichInputMethodManager.getEnabledSubtypeInfoOfAllImes` (`createImiComparator` [CC 4], `addCurrentImeSubtypes` [CC 2], `addOtherImeSubtypes` [CC 4]).
- [x] CORREGIDO (CC 7 -> 3) Eliminación de loop lineal para O(1) en el `OnClickListener` de `RichInputMethodManager.showSubtypePicker` (`formatSubtypeItem` [CC 2], `populateSubtypeItems` [CC 3], `onSubtypeSelected` [CC 2], `setupDialogWindow` [CC 2]).
- [x] CORREGIDO (CC 6 -> 2) `RichInputMethodManager.switchToNextInputMethod` refactorizado con guard clauses y extracción de `switchToNextSubtypeOrOtherIme` [CC 4].
- [x] CORREGIDO (CC 6 -> 3) `RichInputMethodManager.removeSubtype` refactorizado con extracción de `adjustIndexForRemovedSubtype` [CC 3].
- [x] CORREGIDO (100% Paridad) Verificación final de paridad funcional, inmutabilidad, ahorro de memoria y adherencia de reglas en Área 4.

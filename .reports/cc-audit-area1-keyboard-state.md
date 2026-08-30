# Auditoría de Complejidad Ciclomática - Área 1: Máquina de Estados y Parsers

Este informe detalla el análisis de Complejidad Ciclomática (CC) para el Área 1 del teclado. Se identificaron las funciones que superan el umbral recomendado de CC <= 5 y se refactorizaron exitosamente para cumplir con CC <= 5 sin alterar el comportamiento.

## Resumen de Hallazgos y Resultados de Refactorización

| Archivo | Función | CC Antes | CC Después | Estado | Causa Principal |
|---------|---------|----------|------------|--------|-----------------|
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `setShifted` | 12 | 4 | [x] CORREGIDO (12 -> 4) | If-else condicional múltiple y bloque Switch |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `setShiftLocked` | 8 | 3 | [x] CORREGIDO (8 -> 3) | Expresiones booleanas compuestas |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `onPressKey` | 13 | 3 | [x] CORREGIDO (13 -> 3) | If-else chains y condiciones lógicas complejas |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `updateAlphabetShiftState` | 9 | 4 | [x] CORREGIDO (9 -> 4) | If-else anidados con múltiples validaciones |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `onPressShift` | 10 | 3 | [x] CORREGIDO (10 -> 3) | If-else anidados y dependencias de estado múltiple |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `onReleaseShift` | 21 | 5 | [x] CORREGIDO (21 -> 5) | Larga cadena de if-else if y anidamiento profundo |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `onEvent` | 15 | 4 | [x] CORREGIDO (15 -> 4) | Switch chains anidados con if-else lógicos |
| [KeyboardState.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardState.java) | `switchStateToString` | 6 | 3 | [x] CORREGIDO (6 -> 3) | Switch chain simple |
| [KeyboardTextsSet.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardTextsSet.java) | `searchTextNameEnd` | 7 | 3 | [x] CORREGIDO (7 -> 3) | Bucle for con expresiones booleanas en condicional |
| [KeyboardTextsSet.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardTextsSet.java) | `resolveTextReference` | 15 | 5 | [x] CORREGIDO (15 -> 5) | Bucles while y for anidados, múltiples if-else if |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseKeyboard` | 13 | 5 | [x] CORREGIDO (13 -> 5) | Try-catch combinado con condicionales iterativos |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseKeyboardContent` | 16 | 5 | [x] CORREGIDO (16 -> 5) | If-else if anidados dentro de bucle while |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseRowContent` | 15 | 5 | [x] CORREGIDO (15 -> 5) | Larga cadena condicional dentro del parser XML |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseIncludeInternal` | 6 | 3 | [x] CORREGIDO (6 -> 3) | Bloques condicionales con try-catch |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseMerge` | 6 | 5 | [x] CORREGIDO (6 -> 5) | Validaciones de estado con if-else |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseSwitchInternal` | 11 | 5 | [x] CORREGIDO (11 -> 5) | Bucle con lógica if-else múltiple |
| [KeyboardBuilder.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java) | `parseCase` | 6 | 2 | [x] CORREGIDO (6 -> 2) | If-else if secuenciales de parseo |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `MoreKeySpec` (constructor) | 6 | 3 | [x] CORREGIDO (6 -> 3) | Condicionales de inicialización |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `equals` | 6 | 4 | [x] CORREGIDO (6 -> 4) | Múltiples chequeos de igualdad (Return expressions) |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `removeRedundantMoreKeys`| 6 | 4 | [x] CORREGIDO (6 -> 4) | Bucles anidados sobre arrays iterativos |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `splitKeySpecs` | 13 | 4 | [x] CORREGIDO (13 -> 4) | While loop con evaluación múltiple de comas y escapes |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `filterOutEmptyString` | 7 | 3 | [x] CORREGIDO (7 -> 3) | If clauses iterativas filtrando arrays |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `insertAdditionalMoreKeys` | 16 | 1 | [x] CORREGIDO (16 -> 1) | For loops anidados + múltiples chequeos de índice (anidamiento profundo) |
| [MoreKeySpec.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/MoreKeySpec.java) | `getIntValue` | 7 | 5 | [x] CORREGIDO (7 -> 5) | Switch case y bloque condicional simple |

## Análisis de Causas y Tácticas de Refactorización

### 1. KeyboardState.java
* **`onReleaseShift` (CC: 21 -> 5) y `onPressShift` (CC: 10 -> 3)**
  * **Causa**: Una inmensa cadena de `if - else if` que comprueba numerosas combinaciones de booleanos de estado.
  * **Tácticas aplicadas**: Guard clauses, extracción de métodos por modo (`handleDoubleTapShiftInAlphabetMode`, `handleSingleTapShiftInAlphabetMode`, `onPressShiftInSymbolMode`, `handleReleaseShiftInAlphabetMode`, `handleReleaseShiftChordingInAlphabetMode`, `handleReleaseShiftLockedInAlphabetMode`).
* **`setShifted` (CC: 12 -> 4)**
  * **Causa**: Cálculo del `prevShiftMode` con `if-else` seguido de un `switch` evaluando `shiftMode`.
  * **Tácticas aplicadas**: Extracción de `getPrevShiftMode()`, `applyShiftModeState()`, y `updateShiftKeyboardAction()`.
* **`switchStateToString` (CC: 6 -> 3)**
  * **Causa**: Switch block retornando strings.
  * **Tácticas aplicadas**: Lookup table con array estático inmutable `SWITCH_STATE_NAMES`.

### 2. KeyboardTextsSet.java
* **`resolveTextReference` (CC: 15 -> 5)**
  * **Causa**: Bucle anidado de `while` y `for` con múltiples ramas `if-else if`.
  * **Tácticas aplicadas**: Extracción de métodos `expandPass`, `findFirstReference`, `expandRemainder`, `expandAtPos`, `appendNonReferenceChar`, y `checkIndirectionLimit`.

### 3. KeyboardBuilder.java
* **`parseKeyboardContent` (CC: 16 -> 5) / `parseRowContent` (CC: 15 -> 5)**
  * **Causa**: Parsing imperativo iterando sobre nodos XML usando cadenas de `if-else if`.
  * **Tácticas aplicadas**: Descomposición en manejadores modulares por tag (`handleKeyboardContentStartTag`, `handleKeyboardContentEndTag`, `handleRowContentStartTag`, `handleRowContentEndTag`, `parseKeyOrSpacer`).

### 4. MoreKeySpec.java
* **`splitKeySpecs` (CC: 13 -> 4)**
  * **Causa**: Análisis léxico caracter a caracter manejando escape (backslash) mediante bucle `while`.
  * **Tácticas aplicadas**: Descomposición en métodos modulares `handleSingleCharKeySpec`, `addKeySpecToken`, `appendRemain`, `tokenizeKeySpecs`.
* **`insertAdditionalMoreKeys` (CC: 16 -> 1)**
  * **Causa**: For loop iterando sobre un array insertando keys mediante condicionales `if-else` extensivos.
  * **Tácticas aplicadas**: Extracción modular de `substituteMarkers`, `processMarkerKey`, `countMarkers`, `combineUnmatchedKeys`, `prependAdditionalKeys`, `appendRemainingKeys`, `toMoreKeysResult`.

## Checklist Interactiva de Seguimiento

- [x] CORREGIDO (21 -> 5) Refactorizar `KeyboardState::onReleaseShift` (Extraer `handleReleaseShiftInAlphabetMode`)
- [x] CORREGIDO (12 -> 4) Refactorizar `KeyboardState::setShifted` (Extraer calculo `prevShiftMode`)
- [x] CORREGIDO (6 -> 3) Aplicar *Lookup tables* en `KeyboardState::switchStateToString`
- [x] CORREGIDO (15 -> 5) Dividir `KeyboardTextsSet::resolveTextReference` delegando el parsing lógico
- [x] CORREGIDO (16 -> 5 / 15 -> 5) Descomponer handlers para `KeyboardBuilder::parseKeyboardContent` y `parseRowContent`
- [x] CORREGIDO (16 -> 1) Simplificar `MoreKeySpec::insertAdditionalMoreKeys` con predicados nombrados
- [x] CORREGIDO (0 advertencias en Área 1) Auditar nuevamente con `python3 -m lizard` para certificar `CC <= 5`

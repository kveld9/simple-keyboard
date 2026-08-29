# Informe de Auditoría de Complejidad Ciclomática y Deduplicación
## Área 4: Touch Tracking & Keyboard Architecture

### 1. Tabla de Complejidad Ciclomática (CC)

| Archivo | Método | CC Actual | Enlace a Código |
|---|---|---|---|
| `KeyboardBuilder.java` | `parseCaseCondition` | 18 | [KeyboardBuilder.java:L569-L626](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyboardBuilder.java#L569-L626) |
| `Key.java` | `Key()` (constructor) | 17 | [Key.java:L219-L362](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/Key.java#L219-L362) |
| `PointerTracker.java` | `onMoveEventInternal` | 16 | [PointerTracker.java:L618-L659](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/PointerTracker.java#L618-L659) |
| `KeyboardView.java` | `onDrawKeyboard` | 12 | [KeyboardView.java:L279-L330](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardView.java#L279-L330) |
| `PointerTracker.java` | `onUpEventInternal` | 11 | [PointerTracker.java:L689-L733](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/PointerTracker.java#L689-L733) |
| `PointerTracker.java` | `processMotionEvent` | 8 | [PointerTracker.java:L415-L452](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/PointerTracker.java#L415-L452) |
| `PointerTracker.java` | `callListenerOnCodeInput` | 6 | [PointerTracker.java:L225-L247](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/PointerTracker.java#L225-L247) |

### 2. Causas Exactas de Complejidad y Duplicación

- **`parseCaseCondition` (KeyboardBuilder.java):** Contiene más de 15 sentencias booleanas encadenadas (`&&`) evaluando flags de configuración de teclado XML.
- **`Key.Key()` (Key.java):** Constructor masivo que mezcla lectura de `TypedArray`, resolución de `KeyStyle`, cálculos de hitboxes y parseo de `MoreKeys`.
- **`onMoveEventInternal` (PointerTracker.java):** Mezcla detección de gestos *swipe* especiales con lógica estándar de arrastre y transición de teclas.
- **`onDrawKeyboard` (KeyboardView.java):** Combina dibujado de fondo, iteración de teclas y optimización de invalidación de rectángulos con llamadas duplicadas.

### 3. Propuesta Concreta de Refactorización

- **KeyboardBuilder.java:** Sustituir la concatenación booleana extrayendo `matchLocale(caseAttr)`, `matchDeviceState(caseAttr)` con early returns.
- **Key.java:** Extraer métodos privados de inicialización `parseLabels()`, `parseCodes()`, `parseMoreKeys()`.
- **PointerTracker.java:** Extraer `handleSpaceSwipe(x, oldKey)`, `handleDeleteSwipe(x, oldKey)` y `transitionToNewKey(...)`.
- **KeyboardView.java:** Extraer `drawBackgroundIfNecessary(...)` y `drawKeys(...)`.

### 4. Checklist Interactiva de Seguimiento

- [x] `KeyboardBuilder.parseCaseCondition` CORREGIDO (CC 18 -> 2; extraídos `matchCondition` CC 5, `matchLayoutAndTheme` CC 4, `matchNavigationAndInput` CC 4, `matchUiAndFlags` CC 4, `matchKeyVisibility` CC 2, `matchLocale` CC 3)
- [x] `Key.Key` (Constructor) CORREGIDO (CC 17 -> 2; extraídos `parseMoreKeysColumnAndFlags` CC 5, `parseMoreKeys` CC 4, `parseLabel` CC 4, `parseHintLabel` CC 3, `parseCodes` CC 4, `resolveUnspecifiedCode` CC 3, `resolveCodeFromOutputText` CC 2, `resolveCodeFromLabel` CC 3, `parseAltCode` CC 2)
- [x] `PointerTracker.onMoveEventInternal` CORREGIDO (CC 16 -> 3; extraídos `handleSpaceSwipe` CC 5, `handleDeleteSwipe` CC 4, `transitionToNewKey` CC 4, `handleValidNewKeyTransition` CC 4)
- [x] `KeyboardView.onDrawKeyboard` CORREGIDO (CC 12 -> 2; extraídos `applyCustomColorFilter` CC 4, `drawKeys` CC 4, `drawAllKeys` CC 4, `drawInvalidatedKeys` CC 3, `drawSingleInvalidatedKey` CC 2)
- [x] `PointerTracker.onUpEventInternal` CORREGIDO (CC 11 -> 4; extraídos `notifyUpWithActivePointer` CC 5, `handleMoreKeysPanelUp` CC 2, `isKeyActionSuppressed` CC 3, `isRepeatKeySuppressed` CC 4)
- [x] `PointerTracker.processMotionEvent` CORREGIDO (CC 8 -> 2; extraídos `processMoveMotionEvent` CC 4, `dispatchNonMoveMotionEvent` CC 3, `dispatchUpOrCancelEvent` CC 4)
- [x] `PointerTracker.callListenerOnCodeInput` CORREGIDO (CC 6 -> 3; extraídos `resolveEffectiveCode` CC 3, `dispatchCodeInput` CC 3)

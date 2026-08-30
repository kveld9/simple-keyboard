# 📊 Auditoría de Complejidad Ciclomática (CC)
**Área 3: LatinIME Lifecycle, Enrutamiento de Eventos e Insets**

Este informe detalla el análisis de complejidad ciclomática de métodos críticos en el Área 3. Se analizan funciones con CC >= 6, identificando la causa subyacente y proponiendo tácticas de refactorización concretas para lograr un CC <= 5 manteniendo paridad total (Zero-Regression).

---

## 📌 Resumen de Métodos Críticos

| Archivo | Método | CC Inicial | CC Final | Estado |
|---------|---------|:---:|:---:|:---:|
| `CapsModeUtils.java` | [`getCapsMode`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/CapsModeUtils.java#L280) | **46** | **5** | ✅ CORREGIDO |
| `Settings.java` | [`applySingleRestriction`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java#L236) | **20** | **2** | ✅ CORREGIDO |
| `LatinIME.java` | [`loadDictionaryForLocale`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L1026) | **23** | **5** | ✅ CORREGIDO |
| `LatinIME.java` | [`onComputeInsets`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L1439) | **18** | **5** | ✅ CORREGIDO |
| `LatinIME.java` | [`onStartInputViewInternal`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L811) | **15** | **3** | ✅ CORREGIDO |

---

## 🔍 Análisis Detallado y Tácticas de Refactorización

### 1. `getCapsMode` (CC: 46 -> 5)
- **Ubicación:** [CapsModeUtils.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/CapsModeUtils.java#L280)
- **Causa de la Complejidad:**
  - Uso masivo de iteraciones condicionales (`while` / `for`) acopladas con evaluaciones semánticas complejas (`&&`, `||`).
  - Máquina de estados embebida y monolítica basada en un bloque `switch(state)` con 5 casos, cada uno implementando lógica multi-ramificada (determinación de `isLetter`, `isWhitespace`, `isDigit`).
- **Táctica de Refactorización (Target CC <= 5):**
  - **Extracción Modular (Chain of Responsibility):** Dividida la búsqueda en fases lógicas (`skipStartPunctuation`, `skipSpacesAndTabs`, `getParagraphStartCaps`, `getSentenceCapsMode`).
  - **Patrón State:** Modularizado el bucle del analizador de abreviaturas/números con métodos por estado (`stepStartState`, `stepWordState`, `stepPeriodState`, `stepLetterState`, `stepNumberState`, `stepState`, `checkSentenceEndingPeriod`, `evaluateSentenceEnd`).
  - **Resultado:** CC 46 -> 5. Submétodos todos con CC <= 5. Cero asignaciones/boxing en hot path.

### 2. `loadDictionaryForLocale` (CC: 23 -> 5)
- **Ubicación:** [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L1026)
- **Causa de la Complejidad:**
  - Lógica profundamente anidada combinando validación de estado precondicional, iteración para resolver lenguajes de los *subtypes* y gestión de la carga en un Executor.
  - Sobrecarga de chequeos nulos (`!= null`) acoplados con lógica de control de estado (`&&`, `||`).
- **Táctica de Refactorización (Target CC <= 5):**
  - **Guard Clauses y Predicados:** Extraído `checkAndHandleSuggestionsDisabled()`, `isDictionaryAlreadyLoaded(...)` e `isExecutorAvailable()`.
  - **Single Responsibility (Extracción):** Extraído `resolveEnabledLanguages(...)` y `collectLanguagesFromSubtypes(...)`.
  - **Encapsular Workers:** Tarea de carga desacoplada en `executeDictionaryLoad(...)`, `loadDictionaryTask(...)`, `postDictionaryLoadResult(...)` y `applyLoadedDictionary(...)`.
  - **Resultado:** CC 23 -> 5. Submétodos todos con CC <= 5.

### 3. `applySingleRestriction` (CC: 20 -> 2)
- **Ubicación:** [Settings.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java#L236)
- **Causa de la Complejidad:**
  - Un enorme bloque `switch` actuando sobre el string `key`.
  - Mapeo manual agrupando "casos" por el tipo de dato subyacente (boolean, int, float, string) sin abstracción estructural.
- **Táctica de Refactorización (Target CC <= 5):**
  - **Map-based Dispatch:** Reemplazado el `switch` por una tabla estática e inmutable `Map<String, RestrictionApplier>` y la interfaz `@FunctionalInterface RestrictionApplier`.
  - **Resultado:** CC 20 -> 2. Despacho O(1) con CC = 2.

### 4. `onComputeInsets` (CC: 18 -> 5)
- **Ubicación:** [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L1439)
- **Causa de la Complejidad:**
  - Dependencia alta de condiciones booleanas combinadas (`&&` y `||`) para orquestar la visibilidad de diferentes overlays (Portapapeles, Emojis, Teclado Hardware, TopBar).
  - Cálculos dependientes de la jerarquía visual con uso frecuente del operador ternario `? :` encadenado con chequeos lógicos.
- **Táctica de Refactorización (Target CC <= 5):**
  - **Delegación de Layout:** Extraídos `computeVisibleViewHeight(...)`, `computeKeyboardWithTopBarHeight(...)`, `updateTouchableInsetsRegion(...)` y `applyInsets(...)`.
  - **Predicados Semánticos:** Extraídos `isViewVisible(...)`, `getVisibleKeyboardViewOrNull()`, `isAnyOverlayVisible()`, `isImeContentHiddenByHardware(...)` y `shouldExtendTouchToTop(...)`.
  - **Resultado:** CC 18 -> 5. Submétodos todos con CC <= 5. Cero allocs en llamadas de insets.

### 5. `onStartInputViewInternal` (CC: 15 -> 3)
- **Ubicación:** [LatinIME.java](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L811)
- **Causa de la Complejidad:**
  - Inicialización saturada de validaciones de estado inicial (flags de Debug, guard clauses para input connections vacíos).
  - Manejo secuencial sin segmentar: reseteo de UI, logging verboso, verificación de campos de texto cambiados y actualización de estado compartiendo la misma rutina.
- **Táctica de Refactorización (Target CC <= 5):**
  - **Extracción de Fases:** Descompuesto en fases atómicas: `resetInputViewUiState()`, `validateAndLogEditorInfo(...)`, `logEditorInfoDebug(...)`, `initializeInputLogicForEditor(...)`, `setupKeyboardForNewTextField(...)`, `updateKeyboardForEditor(...)` y `postStartInputView()`.
  - **Resultado:** CC 15 -> 3. Submétodos todos con CC <= 5.

---

## ✅ Checklist de Implementación Zero-Regression
- [x] CORREGIDO (CC 20 -> 2): Refactorizar `Settings.java` ([`applySingleRestriction`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java#L236)) mediante `RestrictionApplier` y Map-based dispatch.
- [x] CORREGIDO (CC 46 -> 5): Refactorizar `CapsModeUtils.java` ([`getCapsMode`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/CapsModeUtils.java#L280)) aplicando State Pattern modularizado y guard clauses (compatibilidad alemana y americana preservada al 100%).
- [x] CORREGIDO (CC 23 -> 5): Refactorizar `LatinIME.java` ([`loadDictionaryForLocale`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L1026)) extrayendo resolución de lenguajes, predicados y handlers de tareas.
- [x] CORREGIDO (CC 18 -> 5): Refactorizar `LatinIME.java` ([`onComputeInsets`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L1439)) delegando cálculos geométricos, visibilidad y regiones táctiles sin allocations.
- [x] CORREGIDO (CC 15 -> 3): Refactorizar `LatinIME.java` ([`onStartInputViewInternal`](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java#L811)) segmentando fases de inicialización, logging de debug y configuración de teclado.
- [x] CORREGIDO: Comprobar CC de todos los nuevos submétodos extraídos para asegurar que ninguno excede CC <= 5.

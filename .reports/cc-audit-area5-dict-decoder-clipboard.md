# Auditoría Pro de Complejidad Ciclomática (Área 5)

## 📋 Resumen Ejecutivo
Se ha analizado la Complejidad Ciclomática (CC) de los componentes del **Área 5: Diccionario, Decodificador Espacial y Portapapeles**. El objetivo es identificar las funciones con CC >= 6 y proveer tácticas concretas para reducirlas a CC <= 5 manteniendo paridad total de funcionalidades.

**Archivos analizados:**
- `PrefixDictionary.java`
- `BinaryTrieDictionary.java`
- `BeamSearchDecoder.java`
- `MultiWordSplitter.java`
- `ClipboardDatabase.java`
- `ClipboardHistoryManager.java`

---

## 📊 Tabla de Complejidad (Funciones con CC >= 6)

| Archivo | Función | Líneas | CC |
| :--- | :--- | :--- | :---: |
| `PrefixDictionary` | [getExactNormalizedCorrection](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L561-L601) | 561-601 | **22** |
| `PrefixDictionary` | [getNextWordPredictions](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L858-L908) | 858-908 | **18** |
| `PrefixDictionary` | `computeWeightedDistance` | 182-227 | **17** |
| `PrefixDictionary` | `getBestCorrection` | 611-655 | **13** |
| `BinaryTrieDictionary` | [searchFuzzy](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/binary/BinaryTrieDictionary.java#L205-L259) | 205-259 | **16** |
| `BeamSearchDecoder` | `getBestCorrection` | 188-205 | **14** |
| `MultiWordSplitter` | [findBestSplit](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/MultiWordSplitter.java#L29-L71) | 29-71 | **14** |
| `ClipboardDatabase` | [insertClip](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardDatabase.java#L69-L119) | 69-119 | **21** |
| `ClipboardHistoryManager`| [updateLatestScreenshotCache](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryManager.java#L343-L430) | 343-430 | **18** |

*(Nota: Existen funciones adicionales con CC entre 7 y 10 no detalladas profundamente aquí para enfocarnos en los casos críticos solicitados).*

---

## 🔍 Análisis y Tácticas de Refactorización

### 1. `PrefixDictionary.getExactNormalizedCorrection` (CC: 22)
**Causa exacta de la complejidad:**
La función consolida dos flujos distintos de validación semántica/diccionarios (nodo Trie y `mBinaryDict`). Mezcla de operadores condicionales `&&` y `||` anidados para evaluar frecuencias, mayúsculas y acentuaciones (ej. `!(StringUtils.hasAccents(word) && !StringUtils.hasAccents(bestWord))`), sumado a validaciones tempranas complejas.

**Tácticas de refactorización (Target CC <= 5):**
1. **Extraer Early Return Validation:** Crear un método privado `isValidForCorrection(word)` que agrupe nulos, largos cortos y saltos por configuración.
2. **Dividir por Fuente de Datos:** Extraer la lógica del trie en un método `getCorrectionFromTrie(word, norm)` y la del diccionario binario en `getCorrectionFromBinary(word, bin)`.
3. **Extraer Heurística de Acentuación y Frecuencia:** Un método de ayuda `isBetterCorrection(word, candidate, typedFreq, candidateFreq)` que encapsule la lógica de `typedFreq <= 0 || candidateFreq >= ... || Character.isUpperCase(...)`.

### 2. `BinaryTrieDictionary.searchFuzzy` (CC: 16)
**Causa exacta de la complejidad:**
Esta función implementa directamente la máquina de estados de distancia de Levenshtein (inserción, eliminación, sustitución) dentro de un bucle `for` que itera sobre nodos hijos, combinando la recursión con validaciones de límites de coste e índices del string.

**Tácticas de refactorización (Target CC <= 5):**
1. **Extraer Verificación de Nodos Terminales:** Mover la evaluación de `isTerminal` y agregado a `candidates` a un método `addCandidateIfTerminal(...)`.
2. **Extraer Cálculo de Coste:** Delegar los condicionales de cálculo de edición (inserción/sustitución/match) a un método puro `calculateEditCost(normC, target, targetIdx, remainingDistance)` que retorne una tupla `(cost, nextTargetIdx)` o un objeto auxiliar.
3. **Extraer Cuerpo del Bucle:** Mover la lógica interna completa del bucle `for` a un método `processFuzzyChild(...)`.

### 3. `ClipboardDatabase.insertClip` (CC: 21)
**Causa exacta de la complejidad:**
Manejo simultáneo de transacciones de base de datos (`try-catch-finally`), sanamiento de entradas, ternarios inline repetitivos para discriminar si la búsqueda es por URI o texto, y lógica superpuesta para mantener el estado "pinned" de un registro previo.

**Tácticas de refactorización (Target CC <= 5):**
1. **Extraer Limpieza de Inputs:** Mover la lógica inicial (`trim()`, `substring`, generador de timestamp) a un método `sanitizeClipInput()`.
2. **Separar Query Existente:** Extraer toda la lógica del `Cursor` a un método `isClipAlreadyPinned(db, text, uri)` para evitar mezclar la comprobación con el borrado/insertado en el flujo principal.
3. **Template Method o Wrapper para TX:** Crear un `runInTransaction(db, runnable)` en utils (si no existe) o segregar `insertClipInternal(...)` aislando los bloques `try-catch`.

### 4. `ClipboardHistoryManager.updateLatestScreenshotCache` (CC: 18)
**Causa exacta de la complejidad:**
Un bloque masivo `mExecutor.execute(() -> { ... })` anidado que encapsula consultas a `ContentResolver` (Cursor query), un bucle `while` que procesa hasta 10 entradas verificando tiempos de adición, chequeos de permisos, versionado de SO (`BuildCompatUtils.isAtLeastQ()`) y dispatch callbacks al `MainHandler` bajo múltiples niveles de `if` anidados.

**Tácticas de refactorización (Target CC <= 5):**
1. **Extraer el Runnable:** Aislar toda la función lambda en un método privado `scanRecentScreenshots()`.
2. **Extraer Proyecciones por Versión:** Mover la creación del array de proyección y obtención de los índices (`idIndex`, `nameIndex`, etc) a métodos factory `getMediaStoreProjection()` y `getColumnIndices(cursor)`.
3. **Extraer Validación de Fila:** Aislar el bloque interno de iteración a `processCursorRow(cursor) -> ScreenshotInfo` de tal forma que el `while` simplemente llame a este método.

### 5. `PrefixDictionary.getNextWordPredictions` & `MultiWordSplitter.findBestSplit`
- En **`findBestSplit`** (CC 14), se deben agrupar los condicionales pesados (frecuencias relativas mayores a 10 y reglas gramaticales) dentro de una sub-función `evaluateSplitIndex(dict, norm, index, prevWord)` que devuelva un objeto `SplitResult` aislando el flujo condicional de la iteración.

---

## ✅ Checklist Interactiva de Refactorización

Utiliza las siguientes tareas para realizar seguimiento progresivo del refactoring:

- [x] **PrefixDictionary**
  - [x] CORREGIDO: Implementar `isValidForCorrection()` / `isValidForExactCorrection()`
  - [x] CORREGIDO: Implementar `getCorrectionFromTrie()` y `getCorrectionFromBinary()`
  - [x] CORREGIDO: Refactorizar `getExactNormalizedCorrection` (CC 22 -> 3)
  - [x] CORREGIDO: Refactorizar `getNextWordPredictions` (CC 18 -> 4)
  - [x] CORREGIDO: Refactorizar `computeWeightedDistance` (CC 17 -> 4)
  - [x] CORREGIDO: Refactorizar `getBestCorrection` (CC 13 -> 4)
- [x] **BinaryTrieDictionary**
  - [x] CORREGIDO: Extraer cálculo de costo a `calculateCost()` y `calculateNextTargetIndex()`
  - [x] CORREGIDO: Aislar iteración en `processFuzzyChild()` y `searchFuzzyChildren()`
  - [x] CORREGIDO: Refactorizar `searchFuzzy` (CC 16 -> 4)
- [x] **ClipboardDatabase**
  - [x] CORREGIDO: Extraer validación a `isClipAlreadyPinned()`
  - [x] CORREGIDO: Aislar el `try-catch` y transacción SQLite en `executeInsertClipTransaction()`
  - [x] CORREGIDO: Refactorizar `insertClip` (CC 21 -> 4)
- [x] **ClipboardHistoryManager**
  - [x] CORREGIDO: Extraer `Runnable` a `scanRecentScreenshots()`
  - [x] CORREGIDO: Extraer evaluación de fila `processScreenshotRow()` y `ScreenshotColumnIndices`
  - [x] CORREGIDO: Refactorizar `updateLatestScreenshotCache` (CC 18 -> 2)
- [x] **MultiWordSplitter & BeamSearchDecoder**
  - [x] CORREGIDO: Refactorizar `MultiWordSplitter.findBestSplit` abstrayendo lógica de bigramas (CC 14 -> 4)
  - [x] CORREGIDO: Refactorizar `BeamSearchDecoder.getBestCorrection` aislando predicados de hipótesis (CC 14 -> 4)

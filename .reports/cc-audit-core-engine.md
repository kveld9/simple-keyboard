# Informe de Auditoría de Complejidad Ciclomática y Deduplicación
## Área 1: Core Engine & Decoders

### 1. Tabla de Complejidad Ciclomática (CC)

| Archivo | Método | CC Antes | CC Después | Enlace a Código |
|---|---|---|---|---|
| `PrefixDictionary.java` | `stripAccents` | 23 | 1 | [PrefixDictionary.java:L173-L175](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L173-L175) |
| `BeamSearchDecoder.java` | `removeAccents` | 23 | 2 | [StringUtils.java:L309-L311](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/common/StringUtils.java#L309-L311) |
| `BinaryTrieDictionary.java`| `removeAccents` | 23 | 2 | [StringUtils.java:L309-L311](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/common/StringUtils.java#L309-L311) |
| `PrefixDictionary.java` | `getBestCorrection` | 22 | 4 | [PrefixDictionary.java:L623-L640](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L623-L640) |
| `PrefixDictionary.java` | `getSuggestions` | 15 | 4 | [PrefixDictionary.java:L435-L452](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L435-L452) |
| `BeamSearchDecoder.java` | `onTouch` | 14 | 2 | [BeamSearchDecoder.java:L53-L61](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/decoder/BeamSearchDecoder.java#L53-L61) |
| `BeamSearchDecoder.java` | `getSuggestions` | 14 | 3 | [BeamSearchDecoder.java:L116-L125](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/decoder/BeamSearchDecoder.java#L116-L125) |
| `PrefixDictionary.java` | `searchFuzzy` | 14 | 5 | [PrefixDictionary.java:L750-L766](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L750-L766) |
| `BeamSearchDecoder.java` | `matchCase` | 9 | 1 | [StringUtils.java:L341-L352](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/common/StringUtils.java#L341-L352) |
| `PrefixDictionary.java` | `applyCasing` | 6 | 1 | [PrefixDictionary.java:L571-L573](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/dict/PrefixDictionary.java#L571-L573) |

### 2. Causas Exactas de Complejidad y Duplicación

- **Duplicación de Normalización de Cadenas (Accents):** Los métodos `removeAccents` (en `BeamSearchDecoder` y `BinaryTrieDictionary`) y `stripAccents` (en `PrefixDictionary`) hacían exactamente lo mismo utilizando extensas sentencias `switch` (CC 23).
- **Duplicación de Casing:** La lógica en `matchCase` (`BeamSearchDecoder`) y `applyCasing` (`PrefixDictionary`) era redundante y se encargaba repetitivamente de la corrección de capitalización de strings.
- **Lógica de Decisión Profunda (Deep Nesting):** Métodos como `getBestCorrection` y `onTouch` presentaban múltiples niveles de anidación (`if/else`), condicionales compuestos con evaluación de cortocircuito (`&&`, `||`), y bucles anidados.
- **Búsqueda Fuzzy Redundante:** El método `searchFuzzy` en `PrefixDictionary` acumulaba en una sola función recursiva la exploración de sustituciones, transposiciones e inserciones con alto CC.

### 3. Solución Aplicada y Refactorizaciones

- **Lookup Table O(1) / Shared Utils:** Extraída la eliminación de acentos a `StringUtils.removeAccents` (CC 2) y `StringUtils.stripAccents` (CC 3) mediante un array de lookup estático O(1) (`ACCENT_MAP`). Deduplicado en `BeamSearchDecoder`, `BinaryTrieDictionary` y `PrefixDictionary`.
- **Casing Unificado:** Extraída la normalización de mayúsculas/minúsculas a `StringUtils.applyCasing` (CC 4) y `StringUtils.capitalizeFirst` (CC 2). `PrefixDictionary.applyCasing` y `BeamSearchDecoder` ahora reutilizan esta implementación compartida.
- **Guard Clauses & Extract Function:**
  - `PrefixDictionary.getBestCorrection` (CC 22 -> 4): Extraídas `isSkipCorrection` (CC 4), `shouldSkipValidWord` (CC 3), `getBestFuzzyCorrection` (CC 3), `findBestFuzzyCandidate` (CC 2), `searchAndScoreFuzzyCandidates` (CC 3), `scoreFuzzyCandidates` (CC 2), `calcFuzzyCandidateScore` (CC 1), `getFuzzyBigramBonus` (CC 3), `isValidCorrection` (CC 3), `getMinCandidateScore` (CC 3), `getValidWordDelta` (CC 3), `isSuperiorToValidWord` (CC 1).
  - `PrefixDictionary.getSuggestions` (CC 15 -> 4): Extraídas `findPrefixNode` (CC 3), `scorePrefixWords` (CC 2), `calcPrefixWordScore` (CC 2), `getPrefixBigramBonus` (CC 3), `formatSuggestions` (CC 4).
  - `PrefixDictionary.searchFuzzy` (CC 14 -> 5): Extraídas `recordExactLengthMatches` (CC 3), `exploreFuzzyBranch` (CC 3), `exploreMatchOrEdit` (CC 3), `exploreTransposition` (CC 4).
  - `BeamSearchDecoder.onTouch` (CC 14 -> 2): Extraídas `getCurrentHypotheses` (CC 2), `getTouchCandidates` (CC 3), `expandHypotheses` (CC 4), `expandSingleHypothesis` (CC 2), `matchChildWithCandidates` (CC 3), `createHypothesis` (CC 2), `createFallbackHypotheses` (CC 2), `pruneAndPushState` (CC 2).
  - `BeamSearchDecoder.getSuggestions` (CC 14 -> 3): Extraídas `collectTerminalSuggestions` (CC 4), `collectPrefixSuggestions` (CC 4), `shouldQueryPrefixSuggestions` (CC 3), `addSuggestionIfAbsent` (CC 3).

### 4. Checklist Interactiva de Seguimiento

- [x] `stripAccents` / `removeAccents` CORREGIDO (CC 23 -> 2/3) - Deduplicado en `StringUtils` con tabla O(1).
- [x] `PrefixDictionary.getBestCorrection` CORREGIDO (CC 22 -> 4) - Extraídas funciones de validación, scoring y búsqueda fuzzy (`isSkipCorrection`, `getBestFuzzyCorrection`, `isValidCorrection`, etc.).
- [x] `PrefixDictionary.getSuggestions` CORREGIDO (CC 15 -> 4) - Extraídas funciones de búsqueda de nodo, scoring y formateo (`findPrefixNode`, `scorePrefixWords`, `formatSuggestions`).
- [x] `BeamSearchDecoder.onTouch` CORREGIDO (CC 14 -> 2) - Extraídas funciones para obtención de candidatos, expansión de hipótesis y poda (`getTouchCandidates`, `expandHypotheses`, `pruneAndPushState`).
- [x] `BeamSearchDecoder.getSuggestions` CORREGIDO (CC 14 -> 3) - Extraída colección de terminales y prefijos (`collectTerminalSuggestions`, `collectPrefixSuggestions`).
- [x] `PrefixDictionary.searchFuzzy` CORREGIDO (CC 14 -> 5) - Extraídas ramas de búsqueda (`recordExactLengthMatches`, `exploreFuzzyBranch`, `exploreMatchOrEdit`, `exploreTransposition`).
- [x] `matchCase` / `applyCasing` CORREGIDO (CC 9/6 -> 1/4) - Unificado en `StringUtils.applyCasing`.

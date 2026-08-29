# Auditoría de Vistas UI (Área 3) - Complejidad Ciclomática y Deduplicación

## 1. Tabla de Complejidad Ciclomática (CC)

| Método | Archivo | CC |
|---|---|---|
| `clearSystemClipboardIfMatches` | [ClipboardHistoryView.java:L357-L375](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryView.java#L357-L375) | 10 |
| `getOrCreateCardBackground` | [ClipboardHistoryView.java:L377-L410](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryView.java#L377-L410) | 9 |
| `bindSlot` | [TopBarView.java:L277-L299](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/topbar/TopBarView.java#L277-L299) | 9 |
| `displayClips` | [ClipboardHistoryView.java:L246-L270](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryView.java#L246-L270) | 7 |
| `init` | [ClipboardHistoryView.java:L82-L111](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryView.java#L82-L111) | 6 |
| `createCardView` | [ClipboardHistoryView.java:L272-L355](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/clipboard/ClipboardHistoryView.java#L272-L355) | 6 |
| `dismissKeyPreview` | [KeyPreviewChoreographer.java:L73-L97](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/internal/KeyPreviewChoreographer.java#L73-L97) | 6 |
| `setSuggestions` | [TopBarView.java:L207-L247](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/topbar/TopBarView.java#L207-L247) | 5 |
| `setClipboardSuggestion` | [TopBarView.java:L249-L275](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/topbar/TopBarView.java#L249-L275) | 5 |
| `onFinishInflate` | [InputView.java:L44-L58](file:///home/rot/Proyectos/simple-keyboard/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/InputView.java#L44-L58) | 4 |

## 2. Causas de Complejidad y Duplicación

- **Creación de Botones (`createIconButton`)**: Duplicado exactamente igual en `TopBarView.java` y `ClipboardHistoryView.java`. Ambos extraen el atributo de tema `selectableItemBackground` para añadir los efectos Ripple y configuran el padding/escala manualmente.
- **Extracción de Atributos (`init`)**: Ambos archivos inicializan la vista obteniendo colores, temas y fondos de los mismos atributos (`keyboardViewStyle`, `keyTextColor`).
- **Construcción Manual de LayoutParams**: Abundancia de lógica acoplada a cálculos matemáticos y conversiones (DP a PX) inline, aumentando las bifurcaciones y ruido visual.
- **Complejidad Ciclomática Alta**: Métodos como `clearSystemClipboardIfMatches` acumulan if-statements y bloques try-catch. `getOrCreateCardBackground` utiliza múltiples operadores ternarios anidados. `bindSlot` en `TopBarView` concentra demasiadas decisiones sobre estilos y condicionales para el texto en un solo método.

## 3. Propuesta de Refactorización

1. **Extraer `UIUtils` o `ViewFactory`**:
   - Mover la función `createIconButton` y extracción de estilos a una clase utilitaria compartida.
   - Centralizar el parseo de `dpToPx()` compartiendo recursos.
2. **Refactorización de `ClipboardHistoryView.java`**:
   - `clearSystemClipboardIfMatches`: Extraer el proceso de validación y extracción del texto del portapapeles (primaryClip) a un método independiente para reducir los niveles de anidación.
   - `getOrCreateCardBackground`: Separar la obtención de colores (Ripple, normal, stroke) en submétodos o variables autodescriptivas para erradicar los ternarios anidados.
3. **Refactorización de `TopBarView.java`**:
   - `bindSlot`: Dividir la aplicación del estilo visual (alpha/bold) del bloque que normaliza el texto y asigna el OnClickListener.
4. **Refactorización de `InputView.java`**:
   - Extraer y unificar la lógica redundante de reasignación de `Gravity.BOTTOM` entre `addView` y `onFinishInflate` en una única función utilitaria `ensureGravityBottom()`.

## 4. Checklist Interactivo de Seguimiento

- [x] `clearSystemClipboardIfMatches` CORREGIDO (CC 10 -> 3)
- [x] `getOrCreateCardBackground` CORREGIDO (CC 9 -> 2)
- [x] `bindSlot` CORREGIDO (CC 9 -> 2)
- [x] `displayClips` CORREGIDO (CC 7 -> 3)
- [x] `init` en `ClipboardHistoryView` CORREGIDO (CC 6 -> 2)
- [x] `createCardView` CORREGIDO (CC 6 -> 1)
- [x] `dismissKeyPreview` CORREGIDO (CC 6 -> 4)
- [x] `setSuggestions` CORREGIDO (CC 5 -> 2)
- [x] Extraer `createIconButton` compartido entre vistas (Deduplicación) CORREGIDO
- [x] Centralizar LayoutParams en `InputView` (Deduplicación) CORREGIDO

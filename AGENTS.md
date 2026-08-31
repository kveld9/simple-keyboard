# Directrices de Desarrollo (AGENTS.md)

Reglas de rendimiento y estabilidad para este repositorio:

## 1. Rendimiento y Memoria
- **Cero instanciaciones en rutas calientes**: No crees objetos temporales (`StringBuilder`, `ArrayList`, `HashSet`, `Paint`, `String`) en `MotionEvent.ACTION_MOVE`, `onDraw`, sugerencias o pulsación de teclas. Reutiliza buffers de instancia (`mScratch*`, `ThreadLocal`) con `.clear()`.
- **Cero singletons en rutas calientes**: No llames a `.getInstance()` dentro de `onDraw`, `onTouchEvent`, handlers de swipe o pulsación de tecla. Guarda la referencia como campo de instancia. Inicialízala en `onCreate`, `setKeyboard` o al inicio del gesto.
- **Configuración inmutable**: Mantén `SettingsValues` inmutable (`public final`). Esto permite lecturas concurrentes sin locks ni acceso a disco.
- **Colecciones primitivas**: Evita el boxing (`Integer`, `Float`). Usa `SparseArray`, `SparseIntArray` o arrays planos.

## 2. Higiene de I/O y Estado
- **Cero escrituras redundantes**: Antes de llamar a `SharedPreferences.apply()` o escribir en disco, verifica que el valor cambió (`oldValue != newValue`, o revisa el retorno de `set.add()`). Cada `apply()` dispara listeners globales y causa cascadas de recargas.
- **Cero fallos silenciosos**: No uses `if (x == null) return;` en silencio. Si falta un estado crítico, registra el error con `Log.w(TAG, "...")` o `Log.e(TAG, "...")` antes de salir. Esto hace visibles los bugs de ciclo de vida en debug. R8 limpia los logs en release.

## 3. Disciplina de Código
- **Corrige el origen**: Si un componente falla por falta de estado, no parches los consumidores finales con llamadas cruzadas. Busca dónde nace la entidad e inicialízala allí.
- **Respeta el motor nativo**: Este proyecto deriva de AOSP LatinIME. No refactorices pipelines centrales (decodificadores, parsers, renderers) a menos que el ticket lo exija.
- **Usa los parámetros**: Si un método recibe `Context` o `SharedPreferences`, úsalos. No ignores parámetros para consultar singletons globales.
- **Itera directo**: No uses `for (int i = 0; i < MAX; i++)` con colecciones dinámicas o dispersas. Itera directamente sobre los elementos activos.
- **Usa tags reales**: Registra logs con el `TAG` de la clase o `NombreClase.class.getSimpleName()`. No inventes tags como `"SlopSweep"` o `"AIFix"`.
- **PRs atómicos**: Envía un PR por bug o característica con el diff mínimo. Separa los cambios cosméticos de las correcciones funcionales.

## 4. Compatibilidad y Pruebas
- **Soporte para `TYPE_NULL`**: Si el editor reporta `InputType.TYPE_NULL`, despacha los caracteres mediante eventos físicos (`sendKeyChar` o `sendDownUpKeyEvent`).
- **Verificación**: Compila el código y asegúrate de pasar todos los tests:
  `./gradlew testDebugUnitTest assembleDebug assembleRelease`

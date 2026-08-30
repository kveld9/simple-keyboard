# Directrices de Desarrollo (AGENTS.md)

Principios fundamentales de rendimiento y estabilidad para cambios en este repositorio:

## 1. Rendimiento y Memoria en Rutas Críticas
- **Cero instanciaciones en eventos táctiles, renderizado y tipeo**: Prohibido crear objetos temporales (`StringBuilder`, `ArrayList`, `HashSet`, `Paint`, `String`) en `MotionEvent.ACTION_MOVE`, `onDraw`, sugerencias o pulsación de teclas. Reutilizar buffers de instancia (`mScratch*`, `ThreadLocal`) llamando a `.clear()`.
- **Inmutabilidad en configuración**: Mantener `SettingsValues` 100% inmutable (`public final`) para permitir lectura concurrente segura sin locks ni relecturas de disco.
- **Colecciones primitivas**: Evitar boxing (`Integer`, `Float`). Usar `SparseArray`, `SparseIntArray` o arrays planos.

## 2. Compatibilidad y Pruebas
- **Compatibilidad con terminales y emuladores (`TYPE_NULL`)**: Si el editor destino reporta `InputType.TYPE_NULL`, despachar caracteres mediante eventos físicos (`sendKeyChar` / `sendDownUpKeyEvent`).
- **Verificación obligatoria**: Todo cambio debe compilar y pasar el 100% de los tests:
  `./gradlew testDebugUnitTest assembleDebug assembleRelease`

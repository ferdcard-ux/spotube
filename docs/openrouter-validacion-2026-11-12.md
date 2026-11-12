# Comprobación de OpenRouter en el Moto g22

> Informe de la etapa anterior. La configuración en memoria y el botón de reintento descritos aquí fueron sustituidos por almacenamiento seguro por proveedor y el botón DJ para iniciar sets. El comportamiento y la validación actuales se documentan en [DJ y Mix](dj-mix-ajustes.md).

El usuario introdujo su clave directamente en Nightly y activó la IA con `openrouter/free`. La prueba se realizó usando la configuración que ya estaba en memoria, sin leer la clave, reiniciar la app ni extraer credenciales. No se incorporan al repositorio los títulos o las capturas de su sesión privada.

## Resultado inicial

El panel mostraba recomendaciones locales. Se generó un bloque nuevo con la configuración completa y volvió a mostrarse el mismo resultado. Ese mensaje agrupaba demasiadas causas y no permite atribuir el fallo a una clave inválida, una cuota, un timeout o un formato de respuesta concreto.

## Corrección del diagnóstico y de la petición

- Clasificación de errores HTTP 400/401/402/403/404/408/429/504 y otros errores del proveedor. Solo se muestran categorías locales y códigos numéricos, nunca mensajes remotos ni cabeceras con claves.
- Detección de errores dentro de respuestas HTTP 200, respuestas vacías, salida truncada, JSON ilegible e IDs inválidos o repetidos.
- Espera máxima de 30 segundos, conservando el audio actual mientras se prepara el bloque.
- Presupuesto de salida aumentado de 256 a 1024 tokens. En OpenRouter se solicita JSON explícitamente para que el router pueda seleccionar un modelo compatible.
- Botón «Reintentar bloque DJ»: requiere el DJ activo y genera un bloque nuevo para reemplazar la cola, manteniendo la clave en memoria. No se introducen reintentos automáticos de la misma petición.
- Revisiones de configuración y números de petición para descartar resultados obsoletos, incluidos cambios de configuración que vuelven al valor anterior. Una respuesta anterior tampoco puede sobrescribir el diagnóstico de la petición más reciente.
- Separación del transporte Ktor para probar los escenarios sin conexiones reales ni claves del usuario. La representación textual del estado omite clave, petición y modelo.

La documentación oficial confirma que el [router gratuito puede variar en disponibilidad y latencia](https://openrouter.ai/docs/guides/routing/routers/free-router). También describe [errores de proveedor y respuestas sin contenido por agotamiento de tokens](https://openrouter.ai/docs/api_reference/errors-and-debugging). Estas posibilidades justifican el diagnóstico, pero no prueban la causa del fallo observado en el teléfono.

## Verificación

Compilaron `assembleStableDebug` y `assembleNightlyDebug`. Aprobaron 23 pruebas enfocadas: 12 de IA (incluidos códigos HTTP, errores embebidos, espera, revisión de configuración y privacidad), 10 de transiciones y una de recursos fuera de Compose. Los escenarios de red usan un transporte sintético sin claves reales.

El APK con diagnóstico tiene SHA-256 `353FB6B99B579EE3FF7F3EF7F6CD41E34CF83FECB37E58E1FB2FE02E964F3679`. Después de actualizarlo hay que introducir la clave de nuevo en el teléfono, porque su almacenamiento es exclusivamente temporal. Una selección con estado READY y reproducción posterior constituye la prueba real de éxito; escuchar música mediante la alternativa local no la sustituye. La prueba real con este diagnóstico está pendiente de reconfigurar la clave en el dispositivo.

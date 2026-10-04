# NEXO Agent v3 — HONOR Pad X9a

NEXO es la evolución del proyecto Mía/AsistenteAndroid hacia un agente Android de nivel avanzado, pensado para operar la HONOR Pad X9a como dispositivo dedicado.

## Base existente que se conserva
- Escucha en segundo plano.
- Palabra de activación.
- TTS.
- Waze, Spotify, YouTube, WhatsApp, ChatGPT y otras apps.
- AccessibilityService para control visible de interfaces.
- Planificador con IA.
- GitHub Actions para generar APK.

## Objetivo NEXO
NEXO no será un launcher ni un asistente de comandos fijos. Tendrá una capa de agente capaz de interpretar intención, decidir habilidades, encadenar acciones y pedir confirmación cuando una acción sea sensible.

### Núcleos
1. **Voice Core** — hotword local, escucha continua, STT/TTS y estados de voz.
2. **Agent Brain** — intención, planificación, contexto y selección de herramientas.
3. **Skill Engine** — habilidades instalables/configurables.
4. **Android Control** — intents, AccessibilityService y acciones globales autorizadas.
5. **Vision** — cámara y análisis bajo demanda.
6. **Automation Hub** — rutinas, webhooks y Home Assistant.
7. **Device Panel** — Wi‑Fi, Bluetooth, audio, brillo, batería y conectividad.
8. **Car Mode** — interfaz simplificada, Waze, música y manos libres.
9. **Kiosk Mode** — experiencia de dispositivo dedicado.
10. **Security/Consent** — confirmaciones para acciones sensibles y registro visible de lo que NEXO ejecuta.

## Política de instalación
No se entregará un APK por cada cambio interno. Se generará un APK de instalación cuando una versión alcance un hito funcional verificable.

### Primer hito instalable: v3.0
- Identidad NEXO completa.
- Pantalla principal rediseñada para tablet.
- Escucha estable en MagicOS.
- Agente IA conectado al motor de habilidades.
- Control de apps actual preservado.
- Panel de permisos/estado.
- Modo carro.
- Registro de acciones.
- GitHub Actions compilando APK correctamente.

## Seguridad de claves
Las claves API no se guardan en el repositorio. Se introducen en la app y permanecen en almacenamiento privado del dispositivo. Para una versión de producción se migrarán a Android Keystore / backend seguro según la integración.

## Repositorio
Este branch parte de la versión funcional existente v2.4 y evita destruir el historial anterior mientras se desarrolla NEXO.


## Avance actual
- [x] Rama NEXO separada de producción.
- [x] Identidad y wake word migrados a NEXO.
- [x] Dashboard inicial para tablet.
- [x] Botón de configuración de inteligencia.
- [x] Historial local de acciones.
- [x] Modo carro inicial.
- [x] Fallback al planificador IA cuando un comando no coincide con reglas directas.
- [x] Motor de planes multiacción (hasta 6 acciones por orden).
- [ ] Wake word local dedicado.
- [ ] Visión.
- [ ] Automatizaciones externas.
- [ ] Hardening de credenciales.

- [x] Skill Registry inicial para resolver apps conocidas y futuras habilidades.


## Estándar de producto y diseño

NEXO se desarrollará con una barra de calidad de producto tipo producción, no como demo ni prototipo visual.

### Principios de experiencia
- Interfaz diseñada primero para tablet HONOR en horizontal y vertical.
- Jerarquía visual limpia, superficies con profundidad sutil y sin exceso de bordes.
- Animaciones con propósito: escuchar, pensar, ejecutar, confirmar, error y espera.
- Estados claramente distinguibles sin depender solo del color.
- Tipografía legible a distancia y controles cómodos para uso táctil.
- Transiciones fluidas entre panel principal, actividad, inteligencia, permisos y modos.
- Modo oscuro de alta calidad desde el inicio.
- Diseño consistente en todos los módulos; nada de pantallas “pegadas” con estilos distintos.
- Feedback inmediato en cada acción: qué entendió NEXO, qué está haciendo y qué terminó.
- El usuario siempre debe poder detener una acción en curso.
- Nada sensible debe ejecutarse sin confirmación cuando corresponda.

### Lenguaje visual
- Apariencia tecnológica premium, sobria y de dispositivo dedicado.
- Orb central dinámico como identidad viva del agente.
- Dashboard modular con tarjetas limpias, sombras suaves y estados.
- Microinteracciones para escucha, conexión, error, éxito y ejecución.
- Iconografía consistente y minimalista.
- Espaciado y tamaños definidos por un sistema de diseño, no valores improvisados por pantalla.
- Evitar apariencia de “app de prueba”, launcher genérico o panel administrativo.

### Arquitectura visual prevista
1. **Home / Orb** — estado del agente, escucha y respuesta.
2. **Now Running** — plan actual y pasos en ejecución.
3. **Skills** — capacidades disponibles y su estado.
4. **Activity** — historial legible de acciones y resultados.
5. **Modes** — carro, casa, trabajo, kiosco y perfiles futuros.
6. **Connections** — servicios, dispositivos e integraciones.
7. **Permissions & Security** — permisos, accesibilidad, claves y autorizaciones.
8. **Intelligence** — modelo, comportamiento y configuración avanzada.
9. **Device** — batería, red, audio, brillo y estado del sistema.

## Criterio para primer APK de pruebas

No se entregará NEXO para instalar hasta alcanzar una build candidata que cumpla, como mínimo:

- Compilación limpia en GitHub Actions.
- Sin cierres inesperados en los flujos principales.
- Identidad NEXO completa en UI, notificaciones y voz.
- Diseño coherente en todas las pantallas principales.
- Wake word estable.
- Planificador multiacción estable.
- Motor de habilidades integrado.
- Control de apps funcional.
- Modo carro funcional.
- Historial y registro de acciones.
- Configuración de IA clara y segura.
- Pantalla de permisos y diagnóstico.
- Manejo visible de errores y recuperación.
- Prueba de instalación limpia y actualización sobre versión previa.
- Checklist de pruebas documentado antes de entregar el APK.

La primera instalación en la HONOR será tratada como **release candidate**, no como experimento.

- [x] Sistema visual premium inicial: paleta, espaciado, tarjetas y tema oscuro.
- [x] Dashboard con estado operativo, salud del sistema y resumen de capacidades.
- [x] Pantalla System Health con diagnóstico de permisos, IA y dispositivo.
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

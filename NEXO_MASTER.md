# NEXO — Documento Maestro

> **Fuente principal de verdad del proyecto.**
> Antes de continuar desarrollo, cambios de arquitectura, diseño, seguridad o releases, consultar este documento primero.
> Los documentos especializados (`NEXO_ARCHITECTURE.md`, `NEXO_DESIGN_SYSTEM.md`) amplían esta información, pero este archivo conserva el estado vigente, decisiones, reglas, pendientes y criterio de release.

**Última actualización:** 07-10-2026 · Panamá
**Repositorio:** `ejga24/AsistenteAndroid`  
**Rama de desarrollo:** `nexo-agent-v3`  
**Versión en desarrollo:** `3.0-rc11` · rostro gráfico nativo, wake de pantalla + “Dime”, voz/media y Spotify; QA físico pendiente  
**Dispositivo objetivo principal:** HONOR Pad X9a · MagicOS 10 · Android 16 · Snapdragon 685

---

## 1. Visión del producto

NEXO es un **Agent OS para Android**, no un launcher ni un asistente de comandos fijos.

La HONOR Pad debe comportarse como un dispositivo dedicado capaz de:
- escuchar y conversar;
- entender intención;
- crear planes de varias acciones;
- ejecutar herramientas y apps autorizadas;
- mostrar qué está haciendo;
- pedir confirmación para acciones sensibles;
- operar en modos especializados;
- incorporar visión/cámara;
- integrarse con automatizaciones y dispositivos externos;
- mantener un estándar visual y de seguridad de producto tipo producción.

El objetivo es que NEXO se sienta como un producto construido por un equipo serio de software, no como una demo.

---

## 2. Reglas permanentes de desarrollo

1. **Consultar este documento antes de modificar el proyecto.**
2. No destruir funciones ya estables para introducir una mejora nueva.
3. Nuevas funciones deben integrarse a los núcleos existentes; evitar lógica duplicada o aislada.
4. Ninguna pantalla nueva puede crear su propio lenguaje visual.
5. Usar siempre el Design System de NEXO: colores, estilos, espaciados, radios, tipografía y componentes compartidos.
6. Seguridad y consentimiento se resuelven de forma centralizada, no con reglas dispersas.
7. Las credenciales no se incluyen en GitHub ni en el APK fuente.
8. Los permisos se solicitan solo cuando la capacidad los necesita.
9. Toda acción del agente debe producir feedback visible.
10. Los procesos multiacción deben poder detenerse cuando técnicamente sea posible.
11. Las acciones sensibles deben usar confirmación explícita.
12. No entregar APK por cambios internos; solo cuando exista un hito suficientemente completo.
13. Antes de una release candidate: compilación limpia, checklist de QA y coherencia funcional/visual.
14. Mantener este documento actualizado después de decisiones o cambios relevantes.
15. **Autonomía continua:** una vez definido el objetivo y las reglas, avanzar de forma autónoma por los gates técnicos (código → CI → release firmado → verificación → artefacto) sin esperar una nueva orden del usuario entre pasos. Detenerse únicamente ante una decisión real del usuario, una acción física que solo él pueda realizar, un secreto/permiso no disponible o un bloqueo técnico que impida continuar.

---

## 3. Arquitectura vigente

### Voice Core
Responsable de wake word, escucha, STT, TTS y estados de voz.

Estado:
- Wake word lógico: **NEXO**.
- Foreground exige wake word antes de ejecutar órdenes; conversación ambiental sin “NEXO” se ignora.
- Ventana corta de comando después de decir únicamente “NEXO”.
- Fallback actual: reconocimiento de voz Android.
- Contrato `NexoWakeEngine` creado para sustituir el fallback sin reescribir el agente.
- Próximo objetivo: hotword local dedicado.

### Agent Brain
Responsable de interpretar solicitudes y decidir acciones.

Estado:
- Planificador IA integrado con **Gemini**; integración endurecida con diagnóstico de API, esquema compatible y modelos actuales.
- Los comandos directos/skills locales no dependen de Gemini; la IA se usa para planificación avanzada/fallback.
- Fallback a IA cuando una orden no coincide con comandos directos.
- Planes de hasta **6 acciones**.
- Respuesta final resumida del plan.

### Skill Engine
Responsable de capacidades ejecutables.

Estado:
- `NexoSkillRegistry` creado.
- Resolución de aplicaciones por aliases y nombre instalado.
- Skills base: apps, Waze, Spotify, YouTube, ChatGPT, control de pantalla, volumen, brillo y modo carro.

### Android Control
Estado:
- Intents nativos.
- AccessibilityService para acciones autorizadas sobre interfaces.
- AccessibilityService usa cola persistente de acciones para evitar sobrescritura en planes multiacción.
- Control global de home/back.
- Integración ChatGPT existente.

### Security / Consent
Estado:
- `NexoSafetyPolicy` central.
- Niveles: **SAFE / CONFIRM / BLOCK**.
- Confirmación previa para acciones de pantalla potencialmente sensibles.
- Security & Privacy Center.
- Registro local de acciones.
- Acciones de alto riesgo preparadas para bloqueo.

### Onboarding
Estado:
- `NexoOnboardingState` controla la presentación inicial.
- Si faltan capacidades esenciales en primer arranque, NEXO abre Setup Center una sola vez.
- Setup Center permanece disponible manualmente después.

### Runtime Health / Recovery
Estado:
- `NexoSystemHealth` centraliza READY / DEGRADED / BLOCKED.
- `NexoRuntimeState` conserva incidentes transitorios recientes.
- `NexoRecoveryPolicy` convierte errores técnicos en recuperación y mensajes claros.
- Dashboard, Setup Center y System Health consumen la misma fuente de estado.

### Visual / UX
Estado:
- Tema oscuro premium.
- Design System compartido.
- Orb semántico.
- Dashboard.
- Now Running.
- Activity.
- Intelligence.
- System Health.
- Security & Privacy.
- Diseño orientado a tablet.

### Device / Modes
Estado:
- `NexoModeManager` centraliza el modo activo.
- Modos definidos: Normal, Carro, Casa, Trabajo y Kiosco.
- Producción habilitada actualmente: Normal y Carro.
- Modo carro persiste el perfil activo.
- Dashboard muestra el modo vigente.
- Volumen y brillo desde planes.
- Información de batería/sistema en diagnóstico.

### Vision
Estado:
- CameraX integrado.
- Cámara activada solo bajo demanda y permiso.
- Captura local temporal.
- Motor de análisis desacoplado mediante `NexoVisionEngine`.
- Resultado visual integrado al Design System.
- Vision elimina la captura temporal después de analizar o fallar.
- La captura solicitada se envía al motor de análisis configurado cuando corresponde.

### Automation Hub
Estado:
- **Pendiente.**
- Futuro: rutinas, webhooks, Home Assistant y conectores autorizados.

### Kiosk Mode
Estado:
- **Pendiente.**

---

## 4. Experiencia visual obligatoria

Documento especializado: `NEXO_DESIGN_SYSTEM.md`.

Reglas principales:
- fondo `nexo_bg`;
- superficies `nexo_surface` / `nexo_surface_alt`;
- acento reservado para foco/ejecución;
- estados semánticos success/warning/error;
- estilos compartidos en `styles.xml`;
- espaciados y radios compartidos;
- tarjetas limpias, sin bordes decorativos innecesarios;
- misma jerarquía en todas las pantallas;
- soporte vertical y horizontal;
- estados de loading, éxito, error, permiso y configuración cuando apliquen.

**No se acepta una función como terminada si funciona pero rompe el diseño.**

---

## 5. Estado funcional actual

### Completado
- [x] Rama separada de NEXO.
- [x] Identidad NEXO.
- [x] Dashboard premium inicial.
- [x] Orb con estados: idle / listening / processing / success / error.
- [x] Escucha en foreground/background con fallback Android.
- [x] TTS.
- [x] Planificador IA.
- [x] Multiacción hasta 6 pasos.
- [x] Now Running con progreso.
- [x] Cancelación de plan.
- [x] Skill Registry.
- [x] Skills Center para activar/desactivar capacidades.
- [x] Abrir apps.
- [x] Waze.
- [x] Spotify.
- [x] YouTube.
- [x] WhatsApp/contactos.
- [x] ChatGPT.
- [x] Volumen.
- [x] Brillo.
- [x] Modo carro inicial.
- [x] Modes Center con estado actual y perfiles futuros claramente desactivados.
- [x] Activity log cifrado en reposo, con minimización/redacción de contenido sensible.
- [x] System Health.
- [x] Security & Privacy Center.
- [x] Política SAFE / CONFIRM / BLOCK.
- [x] Premium authorization surface para acciones sensibles.
- [x] Planes se detienen ante paso desconocido/fallido en vez de continuar en estado incierto.
- [x] Validador de planes IA antes de ejecución: máximo 6 pasos, parámetros obligatorios y acciones terminales.
- [x] Vision y automatización sensible requieren dispositivo desbloqueado.
- [x] Main UI permanece detrás del bloqueo normal de Android.
- [x] Design System Contract.
- [x] Layout landscape dedicado para dashboard de tablet.
- [x] GitHub Actions compilando la rama NEXO.

### En desarrollo / siguiente cola
- [ ] Hotword local dedicado **NEXO**.
- [x] Confirmaciones sensibles categorizadas por dinero, destrucción, comunicación, cuenta, instalación y reservas; ampliación futura según nuevas skills.
- [x] Vision/cámara — CameraX, captura local temporal, análisis IA, UI de resultados, limpieza segura y pantalla protegida contra capturas.
- [x] Skills configurables desde UI con enforcement en planes y comandos directos principales.
- [ ] Modes: centro premium creado; Normal/Carro funcionales; Casa/Trabajo/Kiosco pendientes de comportamiento final.
- [ ] Automation Hub.
- [ ] Integraciones externas / webhooks / Home Assistant.
- [ ] Persistencia de contexto de agente.
- [x] Hardening inicial de credenciales con Android Keystore y migración automática desde almacenamiento legado.
- [x] Recuperación centralizada ante errores con estado degradado temporal.
- [ ] QA responsive vertical/horizontal — layout landscape dedicado ya implementado; validación en dispositivo pendiente.
- [ ] Accesibilidad visual y font scaling.
- [x] Setup Center inicial + presentación guiada en primer arranque.
- [x] Checklist formal de Release Candidate creado.
- [x] Foreground wake-word gating para evitar comandos por conversación ambiental.
- [x] Cola de Accessibility para preservar acciones multi-step.

---

## 6. Wake word — decisión técnica vigente

La arquitectura ya permite sustituir el reconocedor actual mediante `NexoWakeEngine`.

Dirección preferida de evaluación: **motor de keyword spotting on-device**, sin enviar audio continuo a Internet.

Criterios obligatorios:
- Android ARM64.
- Funcionamiento local/offline.
- Consumo razonable para escucha continua.
- Wake phrase personalizada: **NEXO**.
- Integración Kotlin/Android.
- Licencia del runtime y del modelo compatible con el uso previsto.
- Fallback automático al motor Android si el motor local no está disponible.
- Pausa durante llamadas/uso incompatible del micrófono.
- Recuperación automática tras reinicio cuando Android lo permita.

No incorporar un modelo a producción hasta confirmar expresamente su licencia de redistribución.

---

## 7. Seguridad y confianza

NEXO debe diferenciar entre:
- **SAFE:** acción directa reversible o de bajo impacto.
- **CONFIRM:** puede enviar, confirmar, borrar, comprar, transferir, cambiar información o producir un efecto relevante.
- **BLOCK:** acción que no debe automatizarse en ese contexto o que pueda afectar críticamente el dispositivo.

La UI de confirmación debe indicar:
- qué acción se va a ejecutar;
- por qué requiere confirmación;
- Autorizar / Cancelar;
- registro posterior en Activity.

Futuro: confirmación por voz con ventana temporal y protección contra confirmación accidental.

---

## 8. Política de credenciales

Actual:
- API key cifrada con AES/GCM y clave protegida por Android Keystore.
- Credencial Gemini cifrada con Android Keystore; la migración conserva la credencial ya almacenada en el dispositivo.
- Planner RC2 usa Gemini con salida JSON estructurada y límites de salida. Vision mantiene su motor desacoplado y su migración a Gemini se valida por separado.
- Migración automática de la credencial legacy si existe.
- La UI no vuelve a mostrar la clave almacenada.
- La credencial puede desconectarse explícitamente.
- Nunca versionada en GitHub.

Estado de release:
- Android Keystore ya protege credenciales y almacenamiento privado cifrado;
- la UI no muestra claves completas;
- la credencial puede borrarse/desconectarse;
- se registra estado, nunca el secreto;
- secretos y patrones de credenciales se excluyen o redactan de logs/diagnósticos.

---

## 9. Política de builds y APK

No entregar APK por cada commit.

### Gate para primer APK que se instalará en la HONOR
- [x] Wake path RC1 definido con fallback Android + gating exacto; validación física pendiente.
- [x] Multiacción.
- [x] Skills base.
- [x] Control de apps.
- [x] Modo carro base.
- [x] Activity.
- [x] System Health.
- [x] Security Center.
- [x] Diseño coherente en pantallas principales.
- [x] Setup Center inicial implementado; onboarding guiado final pendiente de QA.
- [ ] Pruebas de orientación — layout portrait/landscape implementado; validación física pendiente.
- [ ] Pruebas de permisos.
- [ ] Pruebas de background/foreground en MagicOS.
- [ ] Prueba de instalación limpia.
- [ ] Prueba de actualización desde versión anterior.
- [x] Checklist QA documentado; ejecución/aprobación pendiente sobre dispositivo.

La primera versión entregada al usuario será una **Release Candidate**, no una alpha para probar “por probar”.

---

## 10. Documentos relacionados

- `NEXO_MASTER.md` — fuente principal de verdad.
- `NEXO_ARCHITECTURE.md` — detalle de arquitectura y evolución.
- `NEXO_DESIGN_SYSTEM.md` — contrato visual obligatorio.
- `PROJECT_GOVERNANCE_STANDARD.md` — estándar reutilizable para otros repositorios y sistemas web.
- `NEXO_QA_CHECKLIST.md` — gate formal para Release Candidate.
- `NEXO_RELEASE_PLAN.md` — estimado, ruta crítica y criterio de entrega del primer APK.
- `NEXO_INSTALLATION_GUIDE.md` — instalación y QA físico en HONOR Pad.
- `NEXO_SIGNING_SETUP.md` — configuración segura de la identidad estable de firma Android.
- `NEXO_RC1_REPOSITORY_REVIEW.md` — revisión estática, seguridad, CI y release del candidato.
- `NEXO_KNOWN_LIMITATIONS.md` — limitaciones aceptadas y bloqueos no aceptables del RC1.
- `NEXO_MIGRATION_NOTES.md` — comportamiento de migración de datos locales y ruta de actualización.
- Código fuente y GitHub Actions — implementación vigente.

---

## 11. Próxima secuencia de trabajo

**Avance verificable hacia el RC de repositorio: 95%.**

Estado actualizado: la identidad estable de firma ya fue configurada y validada; el RC1 firmado se generó correctamente. RC3 incorpora robustez Gemini, overlay de estado de voz, mejora de detección y protección de reproducción multimedia; artefacto firmado generado, QA físico pendiente.

1. Mantener CI verde en el SHA candidato.
2. Configurar la identidad estable de firma mediante GitHub Secrets.
3. Ejecutar el workflow de RC firmado desde `nexo-agent-v3`.
4. Verificar firma, identidad/versionado, SHA-256 y manifiesto de procedencia.
5. Entregar únicamente ese RC para validación física en HONOR.
6. Ejecutar QA MagicOS: permisos, foreground/background, wake real, orientación/font scaling, instalación/actualización y batería/térmica.

El hotword local dedicado queda fuera de la ruta crítica de RC1: Android Speech + gating exacto es el fallback explícito y seguro del candidato.

---

## 12. Regla para futuros chats/sesiones

Si se retoma NEXO en otra conversación:

**Primero consultar `NEXO_MASTER.md`.**  
Después consultar los documentos especializados solo cuando el cambio afecte arquitectura o diseño.

No reconstruir el estado desde recuerdos sueltos si este documento está disponible.


---

## 13. Estado de primera Release Candidate

**RC de repositorio: 95%.**

No se mantiene un estimado horario rodante. El porcentaje solo cambia cuando se cierra un gate verificable.

Pendiente para producir el APK RC firmado:
- identidad/secretos de firma estable;
- ejecutar workflow de release firmado;
- verificar firma, checksum SHA-256 y manifiesto de procedencia del artefacto.

Después del artefacto, la validación física de MagicOS/HONOR es un gate separado y necesariamente requiere el dispositivo.

### Gate actual de release

La identidad estable de firma Android **ya está configurada y validada** mediante el workflow firmado. El RC1 firmado cerró ese gate.

Gate vigente para RC3:
- CI verde del Planner Gemini;
- versionCode 19 / versionName 3.0-rc3;
- generar y verificar APK firmado con la misma identidad;
- instalar como actualización sobre RC1 para conservar datos/credencial;
- ejecutar checklist físico HONOR/MagicOS y registrar PASÓ / FALLÓ / AJUSTAR.

No desinstalar RC1 antes de instalar RC2, salvo que una prueba específica de instalación limpia lo requiera posteriormente.


## 14. RC3 — 06-10-2026
- Gemini: modelo por defecto gemini-2.5-flash-lite, normalización de nombre, fallback a gemini-2.5-flash y errores HTTP con detalle seguro.
- Voice UX: overlay cuadrado/compacto mediante AccessibilityService para estados escuchando/procesando/ejecutando/listo.
- Comandos de voz autorizados intentan mantener visible la app que el usuario estaba usando en lugar de dejar NEXO al frente.
- Wake fallback: ajustes de endpointer, más resultados y feedback temprano con resultados parciales.
- Media coexistence: el fallback SpeechRecognizer no inicia captura continua mientras Android reporta reproducción musical activa, evitando interferencia observada con Spotify. Esto limita temporalmente el wake por voz durante reproducción hasta sustituir el fallback por hotword local dedicado.
- Release: versionCode 19, versionName 3.0-rc3. Workflow firmado #12 (run 37461966363) completado correctamente; firma, identidad, checksum y artefacto validados.


## RC6 — 06-10-2026 · voz/media
- Corregido bloqueo de escucha en segundo plano cuando había música activa: el wake recognizer ya no se suspende por `isMusicActive`.
- Al detectar “NEXO” con música activa, baja temporalmente el volumen multimedia para escuchar la orden y luego lo restaura.
- Overlay de voz ahora refleja estado real: Atento → Te escucho → Procesando → Ejecutando/Listo, con respuesta visual dinámica.
- Al volver el recognizer de segundo plano a estado listo, el overlay deja de quedarse en “Procesando”.
- Spotify: se limpia “música/canciones de <artista>”, se intenta reproducción nativa y, si Spotify solo abre búsqueda, Accessibility continúa hacia el artista y botón Reproducir/Play.
- Release: versionCode 22 / versionName 3.0-rc6.
- QA físico requerido: wake con Spotify reproduciendo, restauración de volumen, comandos en segundo plano, overlay y autoplay de Spotify.


## 15. RC11 y política CI — 07-10-2026
- Release vigente: versionCode 27 / versionName 3.0-rc11.
- Overlay: NEXO usa un renderer gráfico nativo para rostro/ojos/boca y animación, sustituyendo la cara basada en caracteres.
- Wake: al detectar NEXO se solicita encender la pantalla sin omitir el bloqueo; NEXO responde “Dime” y luego abre la ventana de escucha del comando sin exigir repetir el wake word.
- CI: `.github/workflows/release-apk.yml` es la señal válida de Release Candidate para `nexo-agent-v3`.
- El workflow general/legacy `.github/workflows/build-apk.yml` ya no se ejecuta automáticamente en pushes a `nexo-agent-v3`; conserva ejecución en `main` y manual mediante `workflow_dispatch`.
- Motivo: ese workflow conserva validaciones/versiones históricas y podía producir falsos fallos sobre NEXO 3.
- QA físico HONOR/MagicOS continúa pendiente; avance verificable se mantiene en 95%.

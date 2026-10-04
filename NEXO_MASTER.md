# NEXO — Documento Maestro

> **Fuente principal de verdad del proyecto.**
> Antes de continuar desarrollo, cambios de arquitectura, diseño, seguridad o releases, consultar este documento primero.
> Los documentos especializados (`NEXO_ARCHITECTURE.md`, `NEXO_DESIGN_SYSTEM.md`) amplían esta información, pero este archivo conserva el estado vigente, decisiones, reglas, pendientes y criterio de release.

**Última actualización:** 04-10-2026 12:42 p. m. · Panamá  
**Repositorio:** `ejga24/AsistenteAndroid`  
**Rama de desarrollo:** `nexo-agent-v3`  
**Versión en desarrollo:** `3.0-alpha1`  
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
- Planificador IA integrado.
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
- [x] Activity log.
- [x] System Health.
- [x] Security & Privacy Center.
- [x] Política SAFE / CONFIRM / BLOCK.
- [x] Design System Contract.
- [x] GitHub Actions compilando la rama NEXO.

### En desarrollo / siguiente cola
- [ ] Hotword local dedicado **NEXO**.
- [ ] Confirmaciones sensibles más completas por categoría de acción.
- [x] Vision/cámara — CameraX, captura local temporal, análisis IA, UI de resultados y limpieza segura.
- [ ] Skills configurables desde UI.
- [ ] Modes: centro premium creado; Normal/Carro funcionales; Casa/Trabajo/Kiosco pendientes de comportamiento final.
- [ ] Automation Hub.
- [ ] Integraciones externas / webhooks / Home Assistant.
- [ ] Persistencia de contexto de agente.
- [x] Hardening inicial de credenciales con Android Keystore y migración automática desde almacenamiento legado.
- [x] Recuperación centralizada ante errores con estado degradado temporal.
- [ ] QA responsive vertical/horizontal.
- [ ] Accesibilidad visual y font scaling.
- [x] Setup Center inicial para preparación de permisos y capacidades.
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
- Migración automática de la credencial legacy si existe.
- La UI no vuelve a mostrar la clave almacenada.
- La credencial puede desconectarse explícitamente.
- Nunca versionada en GitHub.

Antes de release final:
- evaluar Android Keystore;
- evitar mostrar claves completas;
- permitir borrar/desconectar credenciales;
- registrar estado, nunca el secreto;
- no incluir secretos en logs.

---

## 9. Política de builds y APK

No entregar APK por cada commit.

### Gate para primer APK que se instalará en la HONOR
- [ ] Wake word suficientemente estable.
- [x] Multiacción.
- [x] Skills base.
- [x] Control de apps.
- [x] Modo carro base.
- [x] Activity.
- [x] System Health.
- [x] Security Center.
- [x] Diseño coherente en pantallas principales.
- [x] Setup Center inicial implementado; onboarding guiado final pendiente de QA.
- [ ] Pruebas de orientación.
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
- Código fuente y GitHub Actions — implementación vigente.

---

## 11. Próxima secuencia de trabajo

1. Consolidar documento maestro y referencias.
2. Completar arquitectura/selección del motor local de wake word.
3. Implementar hotword local detrás de `NexoWakeEngine`.
4. Ampliar confirmaciones sensibles.
5. Conectar motor de análisis a Vision.
6. Completar resultados y estados premium de Vision.
7. Modes y Automation Hub.
8. Hardening / onboarding / QA.
9. Release Candidate para HONOR Pad X9a.

---

## 12. Regla para futuros chats/sesiones

Si se retoma NEXO en otra conversación:

**Primero consultar `NEXO_MASTER.md`.**  
Después consultar los documentos especializados solo cuando el cambio afecte arquitectura o diseño.

No reconstruir el estado desde recuerdos sueltos si este documento está disponible.

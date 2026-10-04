# NEXO Design System Contract

> Project source of truth: `NEXO_MASTER.md`. This file defines the mandatory visual contract.

This document is the UI contract for every current and future NEXO screen. New features must inherit this system rather than introduce independent visual rules.

## Product character
NEXO must feel like a premium dedicated Agent OS: calm, technical, polished, readable at a distance, and visually consistent. It must never look like a collection of Android sample screens.

## Core rules
1. Use the shared NEXO palette from `colors.xml`; do not introduce one-off screen colors.
2. Use shared spacing/radius tokens from `dimens.xml`.
3. Use shared typography and button styles from `styles.xml`.
4. New screens use the same dark surface hierarchy:
   - background: `nexo_bg`
   - primary surface: `nexo_surface`
   - secondary surface: `nexo_surface_alt`
   - subtle outline: `nexo_outline`
5. Accent color is reserved for active states, current execution, important focus, and branded details.
6. Success/warning/error states use the semantic NEXO colors and must also include text/icon/state labels so color is never the only signal.
7. Buttons use the standardized NEXO button families. Avoid random native button appearance.
8. Every new feature must define:
   - idle state
   - loading/working state
   - success state
   - recoverable error state
   - permission/configuration state where relevant
9. Every action performed by the agent should provide immediate visible feedback.
10. Any long or multi-step operation must be cancellable where technically possible.

## Standard screen anatomy
- Page title
- Short explanatory subtitle
- Primary content surface/cards
- Contextual state or diagnostics
- Actions at the bottom or within the relevant card
- No decorative controls with no function

## Motion
Motion must communicate state, not decorate for decoration's sake.
- Orb: idle / listening / thinking / executing / success / error
- Plans: current step changes should be visible without abrupt screen resets
- Cards: subtle appearance/disappearance for contextual surfaces
- Avoid constant unnecessary animation that can distract in car mode

## Responsive targets
Primary target: HONOR tablet.
- Must work in portrait and landscape.
- Main interactive targets should remain comfortably tappable.
- Do not rely on phone-only narrow layouts.
- Avoid text overlap when Android font scaling is increased.

## Release visual gate
A feature is not considered complete merely because it works. Before a release candidate:
- all user-facing screens must use NEXO styles;
- there must be no legacy “Mía” branding visible;
- no unstyled default Android screen may be part of a primary workflow unless it is a system settings screen outside our control;
- visual states must match actual functional states;
- spacing, typography, cards, controls and terminology must be consistent across the product.

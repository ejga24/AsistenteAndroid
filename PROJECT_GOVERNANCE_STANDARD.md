# Project Governance Standard

Reusable engineering standard for the user's software/web repositories.

## Required project documents
Every actively developed system should maintain:

1. **PROJECT_MASTER.md**
   - source of truth;
   - current state only;
   - objectives;
   - architecture summary;
   - business rules;
   - security rules;
   - current pending work;
   - release/install/deploy gates;
   - last update.

2. **ARCHITECTURE.md**
   - modules;
   - data flow;
   - integrations;
   - infrastructure;
   - technical decisions.

3. **DESIGN_SYSTEM.md**
   - colors/tokens;
   - spacing;
   - typography;
   - components;
   - interaction states;
   - responsive rules;
   - accessibility rules.

4. **SECURITY.md**
   - permissions/roles;
   - secrets;
   - sensitive actions;
   - confirmation policy;
   - audit/logging;
   - data handling.

## Development rules
- Consult PROJECT_MASTER.md before changes.
- Do not reconstruct project state from long chat history when the master exists.
- Do not break stable flows to add a new feature.
- New features must fit the existing architecture and design system.
- Do not create one-off visual styles.
- Sensitive actions need centralized authorization/confirmation logic.
- Secrets never belong in source control.
- Every meaningful action/error needs observable feedback/logging.
- Update the master document after important decisions or milestones.

## Web-specific rules
- Responsive behavior is mandatory.
- Components must share one design system.
- Forms require validation, loading, success and recoverable error states.
- Role/permission checks must exist server-side when applicable, not only in UI.
- APIs and database access require least privilege.
- Destructive actions require confirmation.
- Audit trails are required for administrative/sensitive changes.
- Environment-specific URLs/keys belong in configuration, not hard-coded UI.
- Production deployments require build/test gates.

## Release gate
A release is not ready merely because it compiles.

Before production:
- build succeeds;
- primary flows tested;
- responsive layouts checked;
- role/permission tests pass;
- errors are handled visibly;
- no secrets in repository/logs;
- design consistency reviewed;
- master document updated;
- rollback or recovery path known.

## Rule for future work
When entering another repository, first look for its PROJECT_MASTER.md.
If missing, create it before large changes and populate it from the current project state.

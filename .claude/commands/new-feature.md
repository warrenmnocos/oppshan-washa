Plan the implementation of a new feature: $ARGUMENTS

Follow the feature recipe (frontend `src/main/angular/CLAUDE.md` §B.8 + backend `src/main/java/CLAUDE.md` §A), adapted to the feature described above.

Produce a checklist covering:

**Backend**
- [ ] Java `MessageCode` entries needed (errors + info codes)
- [ ] Service method signature + shape
- [ ] Repository method(s) needed
- [ ] Endpoint path + HTTP method
- [ ] Flyway migration needed? (yes/no — if yes, outline the schema change)

**Frontend**
- [ ] `ApplicationEventType` entries (`*Initiated`, `*Confirmed`, `*Cancelled`, `*Succeeded`, `*Failed`)
- [ ] Command interface(s) in `models/`
- [ ] Outcome interface(s) in `models/` (bare past tense)
- [ ] TS `MessageCode` entries + `en.json` keys
- [ ] Domain HTTP service method (pure HTTP wrapper)
- [ ] Listener class name + which event(s) it handles
- [ ] `app.config.ts` registration reminder
- [ ] Dialog component: which payload it reads, what it fires
- [ ] Parent page `@if` gate condition
- [ ] Trigger location (which component, which action)

Flag any cross-stack `MessageCode` values that must be kept in sync. Do not implement yet — this is the planning checklist only.

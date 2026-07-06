---
name: feedback_diagnose_artifacts_not_theories
description: For deploy/native failures, collect artifact-level evidence (download the shipped zip, zipinfo for bootstrap/arch, CloudWatch INIT_REPORT) before proposing causes — and when a theory is disproven, retract it explicitly rather than bending it
type: feedback
---

During the 2026-07-05 prod outage I confidently blamed the PGO build twice ("default to non-PGO")
and then pivoted to a native-image-pruning/Neon-TLS theory — both wrong. The evidence chain that
settled it: the failed CD run's steps all succeeded except the smoke test; CloudWatch showed
`Runtime.InvalidEntrypoint` with 0.04–0.24 ms init (dead before any Java ran); downloading the
actual shipped `function.zip` from the run showed a JVM fast-jar (Main-Class
`io.quarkus.runner.GeneratedMain`, 1,248 entries, **no `bootstrap`**) where a healthy artifact has an
executable arm64 ELF `bootstrap`. Root cause: the quarkus-maven-plugin's auto-bound default JVM
build overwrote `function.zip` between the PGO reactor's normal-build and its save step — nothing to
do with PGO-the-optimizer, whose zips were the correct ones. Warren's questions ("What causes flaky
CD native build", "Do you mean all this time we've been running normal load tests against JVM-based
artifact?") were answerable only from artifacts, not theories.

**Rule:** when a deploy or native binary misbehaves, gather artifact-level evidence FIRST: `gh run
download` the exact shipped zip and `zipinfo`/`file` it (bootstrap present? executable? right arch?),
pull the CloudWatch `INIT_START`/`INIT_REPORT` lines for the failure window, and read the run log's
actual step/selection output. Sub-millisecond init failure = packaging, not code or environment.
Only then propose causes or remediations.

**Why:** two plausible-sounding wrong theories nearly drove wrong remediations (dropping PGO would
not have prevented the outage). The artifacts were retrievable in minutes and were conclusive.

**How to apply:** budget the evidence pass before writing any root-cause message. When new evidence
contradicts an earlier statement, open with the correction ("I was wrong to pin it on X — walking
that back") instead of reframing; Warren responds well to explicit retractions backed by data.
Related: [[feedback_docs_vs_actual]] (verify against reality, not assumptions).
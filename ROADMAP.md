# Roadmap

This is the forward-looking complement to DESIGN.md. DESIGN.md describes intent
and what exists today; this file records work that is intended but not yet
planned in detail.

A roadmap item is a direction, not a commitment. It is moved into a dated
implementation plan when its time comes and removed from here.

## Authorization

### OPA-backed authorizer

Ship an OPA-backed `Authorizer` SPI implementation alongside the existing Cedar
default and the planned AuthZEN adapter. Same SPI seam, operator-bound like
every other authorizer, no special privilege.

The motivation is reach. OPA / Rego is more widely deployed across the industry
than Cedar; offering OPA lowers the barrier for organizations that already run
an OPA fleet and would otherwise refuse a Cedar-only proxy.

Cedar remains the default and the reference implementation. The asymmetry is
deliberate and documented honestly:

- Cedar policies are statically analyzable. The registration-time security gate
  — reject self-permissive rules, reject rules granting field read without an
  identity predicate (spec Inv. 11, §5.3) — is enforced by Cedar symbolic
  analysis before a bundle is admitted. The gate is load-bearing.
- OPA / Rego is Turing-complete and has no comparable symbolic-analysis story.
  An OPA-backed registration gate must use weaker, best-effort checks
  (Rego-AST linting against an allow-list of patterns, property tests against a
  deny-by-default corpus) and accept residual risk a Cedar gate does not have.

The OPA implementation will state this trade-off in its module README. Choosing
OPA is a deployment-time decision with known weaknesses, not a free swap.

Not yet planned; tracked here so it is not lost.

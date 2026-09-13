
---
Task ID: anikoto-session-58
Agent: Main Agent (Z.ai Code)
Task: v16.12 RELEASE — user feedback on v16.13 test build: fix Google-engine title extraction (bracket convention [{[Title]}]), fix Test-Connection HTTP 400, model list reorder (3.1 Flash Lite default, no Recommended label), defaults (Smart Search ON / Google engine), new "Copy response" toggle, Details section rewrite, publish as 16.12 (not 16.13).

Work Log:
- Test key experiments: 3.x models REJECT thinkingConfig.thinkingBudget (bare 400 INVALID_ARGUMENT — the exact user-reported Test-Connection error); without it, payloads pass validation (geo-block FAILED_PRECONDITION only); bogus model 404s → all three 3.x ids exist. thinkingConfig now gated to gemini-2.5* + retry-on-400.
- Google-engine failure root-caused from the user's pasted response: S2's 80-char cap included the "(Japanese title: …)" parenthetical (118-char sentence never matched); S7 skipped " is " lines. Fix: S0 lenient [{[ ]}] parser (both engines bracket-instructed; Google gets a SHORT query suffix — live-verified AI Mode compliance via scrapling), S1/S2 stop at "(" + cap 100, S2c + S6b added, S7 parenthetical-first + cap 120. Validator: 3/3 PASS on real responses.
- Settings: model list 3.1/3.5/3.8+custom (3.1 default, top, no Recommended text), Smart Search ON default, engine default google, "Copy response" toggle (default OFF; query+title on success, query+error+raw on failure), Details category with the user's exact usage text (live phrase substitution).
- Release chain: commit 1153651 → tag v16.12 → Actions run 34767453979 SUCCESS → dist repo 0ff5f25 (repo branch, surgical) + 8cb181d (main/Pages) → distro Release v16.12 with APK asset.
- Verified: APK sha256 e014d92d… == staging, cert B4:67:CA:…:6A:5A == repo.json, manifest 16.12, new strings in dex, index.min.json code=12, old APKs still 200.
- Docs: session log 58, module 06 update, worklogs, AGENT_ONBOARDING, TOOLS.md session-58 tool report.

Stage Summary:
- v16.12 RELEASE is LIVE (index code 12 / version 16.12). Both user-reported bugs fixed with hard evidence; all UI changes per the user's verbatim spec. Testers on the v16.13 test build must sideload 16.12 manually (13 > 12 so no in-app update prompt).

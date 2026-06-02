---
trigger: always_on
---

# Language rule

All content communicated with me should be in Chinese, including the thought process
全部使用中文表述，全部都使用中文，思考过程也使用中文

# ArcartX Skills Auto-Load

This repository is an ArcartX / AXS (ArcartXSuite) project.
Always consult the following skill references when the task touches the listed areas.
They live under the Windsurf global skills directory as the single source of truth.

## Skill Files

- `c:\Users\杨骋达\.codeium\windsurf\skills\arcartx\references\arcartxpacket-local.md`
  - Local AXPM project knowledge: build/runtime, module list, bridge classes
    (`ArcartXPacketBridge`, `ArcartXClientBridge`, `ClientPacketGuard`),
    packet flow conventions, admin/player commands, PAPI prefixes, testing guidance.
- `c:\Users\杨骋达\.codeium\windsurf\skills\arcartx\references\ui-yaml-patterns.md`
  - UI YAML conventions: common `ui:` flags, `packetHandler` shape, client-to-server
    `Packet.send(...)` patterns, control types/fields, HUD / TAB / Chat / inventory notes.
- `c:\Users\杨骋达\.codeium\windsurf\skills\arcartx\references\official-docs-map.md`
  - Map of `wiki.arcartx.com` docs: UI chapter, Server API, Shimmer, Chronos, lookup rules.
  - Prefer `https://wiki.arcartx.com/llms.txt` as the freshest index.

## When To Load Which File

Use `read_file` on the relevant skill file(s) at the start of a task when it involves:

- **Editing UI YAML** under `src/main/resources/arcartx/ui/**` or any ArcartX `ui/*.yml`:
  → read `ui-yaml-patterns.md` first. If packet payload shape is involved, also read
    `arcartxpacket-local.md` (Packet Flow Conventions section).
- **Java module / bridge / guard / packet handler** work under
  `src/main/java/xuanmo/arcartxpacket/**`:
  → read `arcartxpacket-local.md`. If the change affects a client-originated route,
    also skim the packet flow + guard sections.
- **Shimmer scripts** (inline `|-` blocks inside UI YAML, or `.shimmer` helpers):
  → read `ui-yaml-patterns.md` (Script Objects) and use `official-docs-map.md`
    to locate the correct Shimmer page on the wiki.
- **Looking up an official API, attribute, control, or event**:
  → open `official-docs-map.md`, pick the right page, then fetch with `read_url_content`
    (or `https://wiki.arcartx.com/llms.txt` for the latest full index).
- **MythicMobs / BossBar / Chronos / Keys / Controller** tasks:
  → cross-reference `official-docs-map.md` Core Feature Areas with
    `arcartxpacket-local.md` Module List.

## Working Style Reminders

- Preserve Chinese filenames, control IDs, and UI IDs — they are referenced by commands,
  `Screen.open(...)`, `match`, and packet routes.
- Prefer editing the closest existing UI file over creating a new style from scratch.
- Keep `plugin.yml` resource protection intact; non-`plugin.yml` YAML is encrypted into
  `arcartx/internal/protected/*.axb` at build time — do not bypass without explicit ask.
- Before packaging or changing Gradle/resource behavior, run `.\gradlew.bat build`.
  For shared-contract changes (configs, packet builders, guards, placeholders, storage),
  run `.\gradlew.bat test`.

## Updating The Skills

The authoritative copies live at:
`c:\Users\杨骋达\.codeium\windsurf\skills\arcartx\references\`

Update them there. This rule file only points at them; it does not duplicate content,
so context size stays small while still auto-loading on every chat in this project.

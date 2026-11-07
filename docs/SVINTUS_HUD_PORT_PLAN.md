# Svintus HUD port plan

Goal: port the remaining Svintus HUD visuals and interactions into PvPVisuals while retaining PvPVisuals module/config bindings and Fabric 1.21.11 Mojang mappings.

## Reference elements
- Watermark
- Animated module list
- Coordinates (XYZ, direction, alternate-dimension coordinates)
- Armor and held items (slots, durability bars and percentages)
- Target HUD (head/entity preview, health/absorption, held items, armor, hurt animation)
- Notifications (stacking, glass cards and enter/leave animations)
- HUD editor (dragging, scaling, snapping/guides, context settings, persisted positions and alpha)

## Progress
- [x] Audited Svintus widget geometry, typography, palette, and interaction behavior.
- [x] Coordinates HUD: XYZ, compass direction, optional Nether/Overworld conversion, shared glass panel.
- [x] Module List: width sorting, eased enter/leave, horizontal slide, optional glow, left/right and top/bottom anchor alignment.
- [x] Watermark: compact brand block plus independently configurable nickname, FPS, ping, and local time segments.
- [x] Armor HUD: armor slots, optional main/off-hand items, durability bars, percentage/count labels, and shared Layout controls.
- [x] Target HUD: 1.5-second target linger, eased show/hide, health/absorption and hurt treatment, optional items/armor and distance.
- [x] Notifications: shared glass surface, accent wash, eased fade and edge-directed slide.
- [x] HUD editor: existing drag/resize/snap/guides/context-settings/save behavior retained; chrome restyled with shared glass and typography.
- [x] Inventory, HUD Binds, and Combo Counter use the common HUD surface and compact Inter typography.
- [x] All new widget settings are stored in PvpConfig with backward-compatible defaults and exposed in Click GUI.
- [ ] Final in-game visual comparison for spacing, icon shapes, font weights, and edge-case screen sizes.
- [ ] Gradle build and in-game test on Fabric 1.21.11 / Java 21.

Implementation note: all widgets continue to use AbstractHudModule for persisted position, anchor, scale, alpha, and editor integration. The new Armor HUD is separate from Inventory HUD so existing inventory settings and behavior remain intact.

Build and in-game verification have not yet been performed.

## Completion criteria
- Match reference geometry, palette, typography, icons, glass/blur, animation timing and interaction behavior.
- Bind visuals to existing PvPVisuals modules and settings without breaking existing configuration.
- Build with Gradle and test in Fabric 1.21.11 / Java 21.
- Keep the tracking pull request in Draft until reviewed; do not merge without user approval.

The coordinates and module-list implementation is committed to this draft branch. Build and in-game verification have not yet been performed.

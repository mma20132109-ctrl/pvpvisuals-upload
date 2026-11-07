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
- [x] Source audit for coordinates and module-list reference geometry/behavior.
- [x] Added PvPVisuals coordinates widget with XYZ, optional facing direction and optional Nether/Overworld conversion.
- [x] Added animated enabled-module list, width sorting, compact rows and optional accent glow.
- [x] Registered both widgets in the HUD manager and exposed their toggles/options in the Click GUI.
- [ ] Finish exact Svintus watermark segments (player, FPS, ping, time, iconography and typography).
- [ ] Match module-list alignment and all animation details against the reference.
- [ ] Port remaining armor/held-items behavior and compare with existing inventory HUD.
- [ ] Finish Target HUD parity and notification stacking/animations.
- [ ] Review HUD editor interactions and persisted settings against Svintus.
- [ ] Gradle build and in-game test on Fabric 1.21.11 / Java 21.

## Completion criteria
- Match reference geometry, palette, typography, icons, glass/blur, animation timing and interaction behavior.
- Bind visuals to existing PvPVisuals modules and settings without breaking existing configuration.
- Build with Gradle and test in Fabric 1.21.11 / Java 21.
- Keep the tracking pull request in Draft until reviewed; do not merge without user approval.

The coordinates and module-list implementation is committed to this draft branch. Build and in-game verification have not yet been performed.

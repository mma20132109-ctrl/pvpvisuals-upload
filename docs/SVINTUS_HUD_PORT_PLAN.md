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

## Completion criteria
- Match reference geometry, palette, typography, icons, glass/blur, animation timing and interaction behavior.
- Bind visuals to existing PvPVisuals modules and settings without breaking existing configuration.
- Build with Gradle and test in Fabric 1.21.11 / Java 21.
- Keep the tracking pull request in Draft until reviewed; do not merge without user approval.

Initial source audit completed. Implementation and build verification are still pending.

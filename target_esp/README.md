# Target ESP artwork

Keep the PNG artwork in this folder at the project root.

Recommended primary texture names:
- `ghost.png`
- `ghost_glow.png`
- `ghost_particle.png`
- `spirit.png`

The Ghost ESP renderer searches the packaged `assets/pvpvisuals/textures/target_esp/` resources. It prefers the names above and otherwise uses the first PNG found.

Gradle copies only `*.png` files from this folder into the mod resources when `processResources` runs. The PNGs themselves should be committed to this folder if they need to be present in GitHub Actions builds or in a JAR built on another computer.

# PvP Visuals (Fabric, Minecraft 1.21.11, client-only)

## Сборка (Windows PowerShell)

1. Нужен JDK 21 (`java -version`).
2. В архиве нет `gradlew`. Скопируйте его из официального примера (команды выполнять в папке проекта):
   ```powershell
   Invoke-WebRequest -Uri https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/1.21.11.zip -OutFile $env:TEMP\ex.zip
   Expand-Archive $env:TEMP\ex.zip $env:TEMP\ex -Force
   $src = "$env:TEMP\ex\fabric-example-mod-1.21.11"
   Copy-Item "$src\gradlew", "$src\gradlew.bat" .
   Copy-Item "$src\gradle\wrapper\gradle-wrapper.jar" .\gradle\wrapper\
   ```
3. `.\gradlew.bat build` -> `build\libs\pvpvisuals-1.0.0.jar`; `.\gradlew.bat runClient` — запуск клиента.

## Меню

Правый Shift открывает меню: 5 колонок (Visual, World, Particles, Utils, Client), поиск (Ctrl+F),
верхняя плашка. ЛКМ по модулю — on/off (сохраняется в `config/pvpvisuals.json`), стрелка справа — подмодули,
колесо мыши — прокрутка колонки, Esc — закрыть.

## Перемещение HUD в чате

Откройте чат (T или /). Все включённые HUD-элементы (Target HUD, Combo) появятся прямо в чате
с демо-данными и рамкой. Зажмите ЛКМ на элементе и перетащите; позиция сохраняется при отпускании.
Отключить режим: `Client -> Chat HUD Edit`. Сбросить позиции: `Client -> Reset HUD Positions`.
Отключённый модуль (например, Target HUD) в чате не показывается, сначала включите его в меню.

Реализация: `hud/HudChatEditor` на событиях Fabric `ScreenEvents.AFTER_INIT / afterRender` и
`ScreenMouseEvents.allowMouseClick / allowMouseDrag / allowMouseRelease` (без mixin).
Пока открыт чат, `HudManager` сам не рисует элементы, чтобы не было дубля.

## World Particles (категория Particles)

Выключен по умолчанию. Включить галочку «World Particles», стрелка справа раскрывает настройки:
Style (список: Stars / Ring / Spiral / Rays), R/G/B, Size, Speed, Radius, Turbulence, On Target
и On Self (частицы на вас; выключите - останутся только на цели). Ползунки тянутся мышью.
Код: `render/world/*`. Extraction - `WorldRenderEvents.END_EXTRACTION`, рисование - `END_MAIN`.
Проверить при ошибках: ``RenderTypes.debugQuads()` (пакет `renderer.rendertype`), `mouseDragged/mouseReleased(MouseButtonEvent...)`.

## Структура

`gui/rival/*` — меню, `hud/*` — HUD и редактор в чате, `render/*` — частицы, числа урона, траектория, крит,
`event/CombatEvents` — события боя, `config/PvpConfig` — JSON-конфиг.
Новый модуль: поле в `PvpConfig` + строка в `ModuleRegistry`.

## Отладка (код не компилировался в среде автора)

Сигнатуры событий экрана сверены по исходникам Fabric API 1.21.11 (fabric-screen-api-v1).
Сигнатуры самого Minecraft (Mojang mappings) даны по памяти — при ошибке компиляции выполните `.\gradlew.bat genSources`
и проверьте в IDE. Первыми:

- `RivalScreen`: `MouseButtonEvent` (`x()`, `y()`, `button()`), `KeyEvent` (`key()`, `modifiers()`), `CharacterEvent` (`codepoint()`).
- `TopBar`: `Minecraft#getFps()`. `HitParticles`: конструктор `DustParticleOptions(int, float)`.
- `DamageNumbers`: `ClientLevel#entitiesForRendering()`. `HudManager`: `Minecraft#crosshairPickEntity`.
- Блюр: фон рисует ванильный `renderBackground`; он зависит от «Menu Background Blurriness» в Video Settings.
- Скруглённые углы рисуются построчно без сглаживания — на больших GUI-масштабах края слегка «лесенкой».

## Trajectory (категория Visual)

`render/trajectory/*`. Симуляция: гравитация и сопротивление ванили для стрел, трезубца, жемчуга, снежка, яйца, зелий, бутылочки
опыта; огненные шары и череп Визера - прямая по текущей скорости. Блоки - `Level#clip`, сущности - пересечение хитбоксов.
Линия: Solid/Dashed/Dotted/Gradient, прозрачность вдаль, маркеры старта/падения/столкновения/пика, прогресс-точки, подсветка грани блока
и хитбокса, вертикальная линия вниз. Текст (дистанция, время, урон стрелы) - у конечной точки в HUD.
Источник: Hand (предмет в руке) / Players / All (снаряды в полёте). Не реализовано: настоящий additive glow (сейчас широкая
полупрозрачная лента) и отключение depth test (нужен свой RenderPipeline), сопротивление воды, ускорение fireball.

## Inventory HUD (категория Utils)

`hud/inventory/*`. Выключен по умолчанию. Рисует 27 слотов инвентаря, хотбар, 4 слота брони и оффхенд с количеством и прочностью
(полоска / число / оба); активный слот хотбара подсвечен. Стиль как у Target HUD (панель в цветах темы), двигается мышью в чате, как другие HUD-элементы; масштаб в меню. Броня слева / сверху / отдельной
строкой. Скрытие при чате, инвентаре (E), меню мода и F1. Прозрачность действует на фон, рамки и текст; сами иконки предметов
рисует ваниль и они всегда непрозрачные. Проверить: `GuiGraphics#renderItem(ItemStack,int,int)`, `Inventory#getSelectedSlot()`.

## HUD editor (масштаб и перемещение HUD)

- Все HUD-модули наследуют `AbstractHudModule` (x, y, scale 0.5–3.0, alpha 0–1, anchor, enabled; размеры считаются с учётом scale).
- Открыть редактор: **Right Ctrl** (клавиша меняется в Controls) или кнопка **Edit HUD** в подменю **Layout** модуля. Те же перетаскивания работают в чате.
- ЛКМ — переместить, угол 8×8 — масштаб, ПКМ — настройки модуля, Shift+колесо — прозрачность, Esc — сохранить.
- Кнопки: Reset All, Reset Selected, Grid, Snap (5 px), Guides.
- Настройки хранятся в `config/pvpvisuals.json` (`hud`: id -> x/y/anchor/scale/alpha). Старые конфиги (`hudPositions`) мигрируют автоматически.
- Новый модуль: наследуйте `AbstractHudModule`, добавьте в `HudManager.COMPONENTS` и `HudModuleUi.layout(...)` в GUI.

## Импорт / экспорт конфига

- Основной файл: `config/pvpvisuals/config.json` (старый `config/pvpvisuals.json` читается и переносится автоматически). Экспорты: `config/pvpvisuals/exports/*.json`.
- В меню (Right Shift) внизу справа кнопки **Import / Export / Reset**: Import — список экспортов + From Clipboard; Export — имя файла (по умолчанию дата-время) + Clipboard; Reset — с подтверждением. Результат показывается toast-уведомлением справа сверху.
- Сохранение: при каждом изменении в меню (запись в фоновом потоке, атомарно через .tmp), при закрытии меню/редактора HUD, при выходе из игры и раз в 5 минут, если есть несохранённые изменения.
- Формат версионируется (`configVersion`), миграции — `ConfigMigrator`. Неизвестные поля игнорируются, отсутствующие берут значения по умолчанию. Повреждённый файл копируется в `config.json.broken`.

## Логотип

PNG лежат в `src/main/resources/assets/pvpvisuals/textures/gui/logo/echo_visual_{128,512,2048}.png` (рядом `.png.mcmeta` с `"blur": true` — линейная фильтрация). В репозитории стоят **заглушки** — замените их своими файлами с теми же именами. Рендер — `render/LogoRenderer`, настройки — Client → Logo.

## Cape

Текстура: `src/main/resources/assets/pvpvisuals/textures/gui/cape.png` (64×32, как ванильный плащ; плащ — область 0,0 размером 10×16 + боковые грани; чёрный фон рисуется в самой текстуре). Включается в меню: Client → Cape. Вариант A (только вы) работает сразу. Вариант B (Sync + On Others) требует серверного ретранслятора канала `pvpvisuals:cape_sync`: ванильный сервер не пересылает payload'ы между клиентами.

## Kill Effect

Particles → Kill Effect (по умолчанию выключен). Стили: Lightning, Explosion, Skull, Star Burst, Shockwave; цели Players/Mobs/All; Only My Kills; длительность, цвет, размер, Glow, Sound; кнопка Preview для проверки без убийства. Текстуры не нужны — геометрия процедурная (череп — пиксельная сетка).

## Fullbright

Visual → Fullbright (по умолчанию выключен), клавиша G (переназначается в Controls). Режим GAMMA: гамма поднимается до Brightness (1–15) с плавным переходом; запись идёт через mixin-аксессор поля `OptionInstance.value`, потому что `OptionInstance#set` принимает для гаммы только 0..1. Исходная гамма хранится в конфиге (`fullbright.savedGamma`) и восстанавливается при выключении, выходе из игры и при следующем запуске после сбоя. Режим LIGHTMAP и «Affect UI» не реализованы (точка впрыска не проверена).

## Keybinds

- Every toggle module gets an unbound key "Toggle <Module>" (Fullbright defaults to G) in Controls → "Echo Visual — Modules".
- Actions live in "Echo Visual — Client": Open GUI (Right Shift), Open HUD Editor (Right Ctrl), Toggle All Modules, Panic (both unbound).
- In the mod menu use the "Bind: ..." row of a module: click, press a key (Esc cancels, Delete/Backspace clears, Mouse3+ allowed); "Clear Bind" removes it.
- Toasts: Client → Bind Toasts (show, duration, position).
- Keys are stored by Minecraft in options.txt; a copy is kept in the mod config (`keys`) for export/import. Config reset clears module binds and restores GUI/HUD editor defaults.

## HUD Binds

Utils → "HUD Binds" (off by default): a Target HUD-style panel (theme colors) listing modules that have a key (disabled ones stay in the list, dimmed) ("Fullbright [G]" or "G Fullbright"). Only modules with a bound key are shown.
It shows 4 rows per page (setting "Rows Per Page"); the header shows the page number. Flip pages with the keys "HUD Binds: Next/Previous Page" (Controls → Echo Visual — Client, unbound by default), the "Next Page"/"Previous Page" buttons in the menu, or "Auto Flip" (seconds).
Sort: Width / Alphabetical / Custom. Draggable/resizable in the HUD editor.
The list refreshes every 250 ms and immediately after a bind is changed from the mod menu.

## Custom Crosshair

Visual → "Custom Crosshair" (off by default). Replaces the vanilla crosshair through Fabric's `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, ...)`; when disabled, the vanilla element is rendered as usual (no mixin).
Styles: Cross, Dot, Circle, Cross + Dot, Square, Arrows. Static or gradient color (center → edge), alpha, thickness, length, gap, dot size, circle radius, outline.
Hit animation (Expand / Shrink / Flash / Rotate) plays on attacking an entity; Dynamic Spread widens the crosshair while drawing a bow/crossbow, moving or jumping and narrows it while sneaking.
Note: the vanilla attack-cooldown indicator is part of the vanilla crosshair and is not drawn while the custom one is on.

## GUI (EchoScreen, Right Shift)

New purple/neon GUI (package `com.pvpvisuals.gui.echo`): centered window (~75% x 80%) over a blurred background, header with logo, "Echo Visual" title, live search and minimize/close buttons; sidebar with the real categories (Visual, World, Particles, Utils, Client) and Import/Export/Reset; a scrollable grid of module cards (icon, description, key pill, toggle switch).
- Click the toggle to enable/disable; click the key pill to bind (Esc cancels, Delete/Backspace clears, mouse buttons 3+ allowed; right-click the pill clears it).
- Click a card body or the "···" to open the module's settings (sliders, toggles, groups); "‹" or Esc goes back.
- Search filters modules of all categories in real time (also matches settings names); just start typing or press Ctrl+F.
- Minimize collapses the window to its header. Import/Export/Reset open the config dialog.

## Unified HUD style

All HUD elements (Target HUD, Combo Counter, Watermark text plate, Inventory HUD, HUD Binds, the status island, toast notifications and the HUD editor chrome) share one look defined in `hud/HudStyle.java` + `gui/echo/EchoTheme.java`: dark purple rounded panel, thin neon outline, soft glow, white/violet text. To change the look of every HUD at once, edit `HudStyle.panel` and the `EchoTheme.HUD_*` colors.

## Smooth GUI

See SMOOTH_GUI.md (SmoothRenderer + Inter font).

## Now Playing (Island HUD)

Client -> Island -> Now Playing (+ Position, Cover Art, Timecode, Artist, Hide When Paused, Cover Size). Shows cover, `MM:SS / MM:SS`, title and artist of whatever Windows reports in its media overlay (Spotify, Yandex Music, browser, ...).
Source: `media/SmtcMediaProvider` — one persistent `powershell.exe` that polls WinRT `GlobalSystemMediaTransportControlsSessionManager` every 500 ms (no extra libraries, no DLLs). Not Windows / no PowerShell -> the widget silently stays hidden.
Other sources (Spotify/Yandex) = implement `media/MediaProvider`.

Debug: if Now Playing shows nothing, run `powershell -ExecutionPolicy Bypass -File tools\nowplaying-test.ps1` (prints the raw SMTC data once per second) and check `logs/latest.log` for lines with "SMTC bridge" / "powershell".

## Island HUD: Now Playing (two rows)
When a track plays, the Island grows taller (~350 ms, ease-out-cubic) and a music row (title / timecode / artist, optional 16x16 cover) slides up with a small bounce. The bottom row (time, logo, FPS) is unchanged. Settings: Island -> Now Playing, Track Time, Track Artist, Cover Art, Hide When Paused, Max Track Width. Background blur is not implemented.

## Shader Sky (World)
Replaces the vanilla sky with a GLSL shader. Menu: World -> Shader Sky (off by default). Options: shader dropdown
(Void / Gradient / Stars / Synthwave), Brightness 0.5-2.0, Speed 0.0-2.0, Time Of Day Lock.
Shaders: `assets/pvpvisuals/shaders/core/echo_sky.vsh` + `sky_*.fsh`. Pipelines are built in code
(`render/sky/SkyRenderer.java`); 1.21.11 has no JSON shader definitions. Disabled automatically when Iris/OptiFabric is
present. Config: `shaderSky` in pvpvisuals.json. Nether has no sky pass in vanilla, so it is only drawn if the game calls it.

## Freelook (Visual)
Free orbit camera around the player. Key: "Freelook" (default Left Alt, category "Echo Visual — Modules"; change it in
Controls). Menu: Visual -> Freelook (off by default): Mode Hold/Toggle, Invert Y, Smooth Return, Sensitivity,
Force Third Person, Allow Actions. While free, the mouse only turns the camera; the player body, WASD direction and
collision stay as they were. Releasing the key eases the camera back behind the player and restores the previous
perspective. Config: `freelook` in pvpvisuals.json. Startup log lists Camera/MouseHandler methods ("pvpvisuals/freelook").

## Themes (GUI + HUD)
Menu: Client -> Theme (dropdown): Purple (default), Blue, Cyan, Green, Red, Orange, Pink, Graphite. The whole menu, HUD
panels, Island and HUD editor recolor at once. Implemented as hue rotation of the base palette (`gui/echo/EchoTheme.java`,
`EchoPalette.java`); config: `gui.palette`. Colors hard-coded elsewhere can use `EchoTheme.shift(argb)`.

## Loading Screen

Custom Echo Visual loading screen instead of the Mojang one (Client -> Loading Screen): code-drawn logo, no image files (fade-in + scale 800 ms, soft pulse), smooth purple progress bar with glow, status text, optional spark particles, 500 ms fade-out. Background: Gradient / Animated / Dark. Disable "Custom Splash" to get the vanilla screen back. If drawing ever fails, it switches itself off and logs an error to `pvpvisuals/splash`.

## Main Menu

Custom Echo Visual title screen (Client -> Main Menu): code-drawn logo, animated Singleplayer / Multiplayer / Settings buttons, exit and language icons, optional particles and version text. It opens the vanilla world list, server list and options screens. Turn off "Custom Menu" to get the vanilla menu back; if the custom menu ever throws, it disables itself and logs to `pvpvisuals/menu`.

### World screens style

Client -> Main Menu -> World Screens Style: the world list, the Create/Edit World screens , the world generation/loading screen and the saving/preparing screens get the Echo Visual look (gradient background with particles, dark buttons with purple outline, purple selection frame). Vanilla widgets are restyled by mixins (`ScreenBackgroundMixin`, `AbstractButtonMixin`, `AbstractSelectionListMixin`); text fields, checkboxes and tabs keep the vanilla look for now.

## Block Overlay

Visual -> Block Overlay (off by default): shader highlight of the block under the crosshair instead of the vanilla black outline. Styles: Pulse, Grid, Scanline, Glow, Solid and the space styles Void, Nebula, Starfield, Galaxy (second color, star density, render quality); color/alpha, animation speed, line thickness, Through Walls, Outline Only. Own `RenderPipeline` + GLSL (`assets/pvpvisuals/shaders/core/block_overlay*.vsh/.fsh`); if the shader fails it falls back to a plain fill and logs to `pvpvisuals/blockoverlay`.

## HitBox

Visual -> HitBox (off by default): custom 3D boxes around players and mobs (not the vanilla F3+B boxes). The box smoothly changes from the base color to the target color while you aim at the entity. Options: players/mobs, colors and alpha, line thickness (pixels, constant at any distance), transition speed, through walls, max distance, corners only, glow. Own `RenderPipeline` (`core/hitbox.vsh/.fsh`), logs to `pvpvisuals/hitbox`.

## Radial Menu

Utils -> Radial Menu (off by default): hold the key (default R, rebindable on the module card), point the mouse at one of 5 sectors and release the key (or click) to send its phrase to chat; Esc cancels. Phrases are edited in the module's settings (click a Phrase row, type, Enter to apply). A phrase starting with `/` is sent as a command. Radius, dead zone and colors are configurable.
#   e c h o v i s u a l  
 #   p v p v i s u a l n e w  
 
# Плавный рендер GUI (SmoothRenderer) — патч

Распакуйте архив ПОВЕРХ проекта (пути внутри совпадают с `src/main/...`). Исходники проекта в песочнице пропали,
поэтому это патч, а не весь проект: новые файлы + заменяемые.

## Что внутри
Новые: `gui/smooth/SmoothRenderer.java`, `gui/smooth/Typo.java`, `assets/pvpvisuals/font/inter.json`, `inter.ttf`
(Inter, лицензия SIL OFL — `OFL-Inter.txt`).
Заменяются: `gui/echo/{Gfx,EchoIcons,ToggleSwitch,SidebarButton,ModuleCard,EchoSearchBar,SettingsView,EchoScreen}.java`,
`gui/rival/RoundedRender.java`, `config/GuiSettings.java`.

## Подход
Кастомные шейдеры в GUI-слое 1.21.11 не нужны. Fabric API даёт рабочий путь (тест `GuiRendererNonQuadsTest`):
свой `GuiElementRenderState` + свой `RenderPipeline` (на `GUI_SNIPPET`, вершины POSITION_COLOR, режим TRIANGLES,
`withUsePipelineDrawModeForGui(true)`), отправляемый через `g.guiRenderState.submitGuiElement(...)`.
- скругления: дуги по 2..16 сегментов (зависит от радиуса в физических пикселях);
- сглаживание: полоса шириной в 1 физический пиксель (1/guiScale) с альфой 1 -> 0 по краю (аналог smoothstep SDF);
- обводка: толщина 1.5–2 px, меньше пикселя — «волосок» с пониженной альфой;
- градиенты: цвет в вершинах (билинейно по 4 углам), интерполирует видеокарта — без ступенек;
- свечение: 8 полос наружу, альфа (1-k)^2;
- иконки: линии/круги/кольца/скруглённые прямоугольники (`EchoIcons`), без пикселей;
- текст: Inter через TrueType-провайдер (oversample 8), подключается стилем компонента (`Typo`).
Blend/Depth/Cull: в 1.21.11 состояние GL задаётся самим pipeline (GUI_SNIPPET: translucent blend, без depth),
`GlStateManager`-вызовов больше нет; cull выключен в pipeline. Отдельной настройки при открытии экрана не требуется.

## ПРОВЕРИТЬ при компиляции (сверено по исходникам Fabric API 1.21.11 / по памяти)
Сверено по Fabric API: `RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)`, `withLocation`, `withUsePipelineDrawModeForGui`,
`withVertexFormat`, `GuiElementRenderState` (buildVertices/textureSetup/pipeline/bounds/scissorArea), `TextureSetup.noTexture()`,
`g.guiRenderState.submitGuiElement`, `addVertexWith2DPose(...).setColor(argb)`, `ScreenRectangle#transformMaxBounds/intersection`.
По памяти (не подтверждено): 
1. `RenderPipeline.Builder#withCull(boolean)` — если нет, удалите строку `.withCull(false)` в `SmoothRenderer`.
2. `g.scissorStack.peek()` (поле открыто Fabric-виджетом, метод `ScissorStack#peek`) — если нет, передавайте scissor `null`.
3. `Style#withFont(FontDescription)` + `new FontDescription.Resource(Identifier)` в `Typo` (единственная строка STYLE).
4. `addVertexWith2DPose(Matrix3x2f, float, float)` — принимает float; если int, округляйте координаты.
5. `Minecraft#getWindow().getGuiScale()` (double).

## Если что-то пошло не так
`config/pvpvisuals.json` -> `"gui": { "smoothGui": false, "smoothFont": false }` возвращает старый рендер и ванильный шрифт.
Шрифт: размер/сдвиг правятся в `font/inter.json` (`size`, `shift`).

## Как проверить
1. Откройте меню (Right Shift). Video Settings -> GUI Scale = 4 (или Auto на большом мониторе).
2. Углы карточек, тумблеры, кнопки, кружки и иконки должны быть ровными, без «лесенки»; обводка мягкая; градиент плавный.
3. Наведите на карточку: свечение плавно нарастает, тумблер ездит без рывков (позиция дробная).
4. Текст — Inter, читаемый на любом масштабе; при проблемах — `smoothFont:false`.

## Шрифт
`inter.ttf` — статический Inter Regular (вариативный файл не грузился, текст был квадратами). В `inter.json` добавлен fallback `reference -> minecraft:include/default`: если глиф не найден, рисуется ванильный.

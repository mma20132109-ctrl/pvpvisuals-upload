# Zenith Client — Main Menu & Account Manager Package

В этой папке сохранены все исходные файлы и ресурсы, относящиеся к главному меню и менеджеру аккаунтов клиента Zenith.

## Структура папки

### 1. `src/screens/override/main/`
- **`MainMenuScreen.java`**: Основной экран главного меню (параллакс фона, верхняя плашка пользователя с ником, нижний док разделов, боковая панель с переключением шейдеров/фонов, переходы страниц).
- **`MainMenuBlurRenderer.java`**: Рендерер размытия по технологии Kawase Blur для frosted glass эффекта интерфейса.
- **`MenuTheme.java`**: Палитра цветов, адаптивные стили, цвета стекла (`glassBackground()`, `glassBorder()`).
- **`MenuSurface.java`**: Поверхности стекла, блюр, скругленные панели и тени.
- **`MenuIcon.java` / `MenuIcons.java`**: Иконки меню (SVG глифы).
- **`MenuSkin.java`**: Константы размеров элементов.

#### Подпапка `page/`:
- **`AccountManagerPage.java`**: 3D Cover-Flow карусель аккаунтов, поддержка скинов, плавное затемнение дальних карточек, клик-анимации, выбор/удаление, раскрывающиеся кнопки Microsoft/Случайный/Добавить/Очистить.
- **`SingleplayerMenuPage.java`**: Страница одиночной игры.
- **`MultiplayerMenuPage.java`**: Страница сетевой игры.
- **`SettingsMenuPage.java`**: Страница настроек клиента.
- **`HomeMenuPage.java`**, **`ProxyMenuPage.java`**, **`MainMenuPage.java`**: Базовый интерфейс и страницы.

#### Подпапка `widget/`:
- **`MenuButton.java`**: Кнопки меню с анимацией нажатия (-8% spring bounce) и иконками.
- **`MenuTextField.java`**: Поле ввода текста (для ника).
- **`MenuSlider.java`**, **`MenuScroll.java`**, **`MenuWidget.java`**: Базовые виджеты UI.

#### Подпапка `component/`:
- **`SkinPreview.java`**: Загрузчик и кэш скинов (Mojang API + локальные оффлайн скины).
- **`MenuNavigationBar.java`**, **`VanillaOptionStyle.java`**: Вспомогательные компоненты.

---

### 2. `src/accounts/`
- **`Account.java`**: Модель данных аккаунта (Оффлайн / Microsoft).
- **`Accounts.java`**: Менеджер аккаунтов, сохранение/загрузка JSON списка.
- **`MicrosoftAuth.java`**: Device Code авторизация Microsoft OAuth2.

---

### 3. `src/font_and_icons/`
- **`SvgIcon.java`**: SVG иконки профиля, настроек, шестеренки, крестика и т.д.

---

### 4. `assets/`
- **`menu/`**: Фоновые изображения (`sadnes.png`, `sky.png`, `sky_hd.png`, `background.png`, `wallpaper.png`).
- **`skins/`**: Встроенные оффлайн скины игроков (`technoblade`, `captainsparklez`, `dinnerbone`, `ph1lza`, и др.).
- **`fonts/`**: MSDF шрифты и векторные OTF шрифты интерфейса.
- **`shaders/menu_background/`**: Шейдер процедурного анимированного фона.

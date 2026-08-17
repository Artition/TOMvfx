# AGENTS.md

Инструкции для AI-агентов (Claude, Copilot и т.п.), работающих в этом репозитории.

## Что это за проект

Fabric-мод для Minecraft ~26.1 (клиентская VFX-библиотека). Java-исходники раздельны по сайдам:

```
src/main/java/com/tom/vfx/        — общий код (сервер + клиент): API, команды, датапак-эффекты, сеть
src/client/java/com/tom/vfx/client/ — только клиент: рендер, пост-обработка, шейдеры, тряска камеры
src/main/resources/               — fabric.mod.json, mixins, lang, assets (общие)
src/client/resources/             — client mixins, шейдеры (assets/tompfx/shaders)
```

Полное описание доменной модели (эффекты, таймлайны, датапаки, сетевой протокол) — в `docs/GUIDE.md`.

## Сборка и проверка

```bash
./gradlew build          # компиляция + джар в build/libs/
./gradlew runClient      # тестовый клиент
./gradlew runServer      # тестовый сервер
```

Требуется JDK 25 в `JAVA_HOME` (проект компилируется с `--release 25`, см. `build.gradle`). Если сборка падает с `error: release version 25 not supported` — значит Gradle подхватил не тот JDK, а не баг кода.

После любых правок в `src/` обязательно гоняй `./gradlew build` перед коммитом — задача агента не считается выполненной, если сборка не проходит.

## Стиль кода

- Отступы — табы, не пробелы.
- Параметры методов и локальные переменные, которые не переприсваиваются — помечать `final` (см. любой класс в `effect/` или `client/`).
- Публичные классы и нетривиальные публичные методы — с Javadoc (описание + `@param`/`@return`, где не очевидно из сигнатуры).
- Утилитные классы без состояния — `final class` с приватным конструктором (см. `SimplexNoise`, `VFXShaderPrograms`, `VFXWorldBindings`).
- Одиночки (managers) — приватный конструктор + статический `get()` (см. `VFXEffectManager`, `VFXDefinitionManager`, `VFXPostProcessingManager`).
- Любая коллекция, растущая от внешнего/сетевого/датапак-ввода, должна быть ограничена константой (см. `MAX_ACTIVE_EFFECTS`, `MAX_SCHEDULED_EFFECTS`, `MAX_COLLECTION_DEPTH` в `VFXEffectManager`) — не добавляй новые неограниченные списки/мапы без явного лимита.
- Парсинг датапаков (`VFXDefinition.parse`, `VFXDefinitionManager.prepare`): любое новое исключение при разборе одного файла обязано быть перехвачено в `catch` внутри `prepare()`, иначе один битый JSON уронит загрузку вообще всех эффектов (уже наступали на эти грабли — см. git log).

## Что нельзя ломать без обсуждения

- Формат JSON датапак-эффектов (`data/<namespace>/vfx/<effect>.json`) и сетевой протокол `tompfx:vfx_trigger` — обратная совместимость важна, версия протокола (`VFXTriggerPayload.PROTOCOL_VERSION`) обязана бампаться при breaking change.
- Публичный Java API (`VFXAPI`) — используется другими модами.

## Документация

- Пользовательский гайд (команды, типы эффектов, датапаки, Java API) — `docs/GUIDE.md`. При изменении поведения эффектов/команд/API обновляй его changelog внизу файла (см. существующий формат `**vN**: ...`).
- Соглашения по коммитам и веткам — `CONTRIBUTING.md`.

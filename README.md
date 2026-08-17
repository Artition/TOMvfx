# TOM Post Effects (tompfx)

Клиентская VFX-библиотека для Minecraft на Fabric: пост-обработка экрана (хроматическая аберрация, цветокоррекция, дисторсия, блюр, пикселизация и др.), тряска камеры, мировые оверлеи блоков (тинт/обводка), датапак-эффекты, сетевые триггеры сервер→клиент и публичный Java API для других модов.

## Требования

| | |
|---|---|
| Minecraft | ~26.1 |
| Fabric Loader | >=0.19.3 |
| Fabric API | обязателен |
| Java | 25+ (JDK 25, см. ниже) |

## Быстрый старт

```bash
git clone https://github.com/Artition/TOMvfx.git
cd TOMvfx
./gradlew build
```

Собранный джар — в `build/libs/`. Для сборки нужен JDK 25 в `JAVA_HOME` (или `org.gradle.java.home` в `gradle.properties`) — старший JDK не подхватит `--release 25`.

Запустить тестовый клиент/сервер прямо из проекта:

```bash
./gradlew runClient
./gradlew runServer
```

## Использование

Полный гайд по командам (`/vfx play`, `/vfx playat`, `/vfx stop`, `/vfx list`), встроенным типам эффектов и формату датапаков (`data/<namespace>/vfx/<effect>.json`) — в **[docs/GUIDE.md](docs/GUIDE.md)**.

Минимальный пример из Java API:

```java
// Сервер → клиент
VFXAPI.sendEffect(serverPlayer, Identifier.of("tompfx", "screen_flash"), Map.of(), null);

// Локально на клиенте
VFXAPI.playEffect(Identifier.of("tompfx", "camera_shake"), 20, Map.of("amplitude_x", 0.2F), null);
```

## Документация

| Файл | Что внутри |
|---|---|
| [docs/GUIDE.md](docs/GUIDE.md) | Команды, типы эффектов, формат датапаков, привязки к миру/камере |
| [docs/API.md](docs/API.md) | Java API (`VFXAPI`), сетевой протокол `tompfx:vfx_trigger` |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Как это устроено под капотом: пайплайн рендера, дата-флоу, лимиты нагрузки |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | История изменений по версиям |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Соглашения по веткам и коммитам |
| [AGENTS.md](AGENTS.md) | Инструкции для AI-агентов, работающих в этом репозитории |

## Лицензия

MIT — см. заголовок `fabric.mod.json` (`"license": "MIT"`).

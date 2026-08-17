# Архитектура

## Контекст и цели

tompfx — клиентская VFX-библиотека для Fabric-мода: сервер триггерит эффекты по сети (или другой мод — напрямую через `VFXAPI` на клиенте), клиент их проигрывает и рендерит. Цели: (1) декларативные эффекты через датапак-JSON без перекомпиляции, (2) ограниченная нагрузка на рендер даже при большом числе одновременных эффектов, (3) отказоустойчивость — один битый эффект/датапак-файл не должен ронять остальные.

## Основные системы

```
                     ┌──────────────────────┐
 датапак JSON  ─────▶│ VFXDefinitionManager │  (common: main + client-как-solo-игрок)
                     └──────────┬───────────┘
                                │ VFXDefinition (типобезопасная модель)
                                ▼
 /vfx play, VFXAPI ──▶  VFXEffectManager (client)  ──▶ VFXActiveEffect (таймлайн + позиции + fade)
                                │
              ┌─────────────────┼─────────────────────┐
              ▼                 ▼                     ▼
   VFXPostProcessingManager  VFXWorldOverlayRenderer  CameraShakeManager / CameraMixin
   (шейдерные пост-эффекты)  (block_tint/outline)     (тряска камеры, FOV)
```

- **`VFXDefinitionManager`** (main) — реестр определений: встроенные (`registerBuiltIns()`) + датапак (`data/<ns>/vfx/<name>.json`, перезагружается через `SimplePreparableReloadListener`). Регистрируется и на сервере, и на клиенте (для одиночной игры).
- **`VFXEffectManager`** (client, синглтон) — единственный источник правды о том, что сейчас проигрывается: список `active` (`List<VFXActiveEffect>`) и `scheduled` (отложенные дочерние эффекты коллекций), общий «эффектный» таймер `clock` в тиках.
- **`VFXActiveEffect`** — один экземпляр проигрывания: `VFXTimeline` (анимированные параметры + мировые привязки) + fade-in/out вес + список позиций (для мировых оверлеев).
- **`VFXPostProcessingManager`** (client) — прогоняет активные пост-эффекты через ping-pong `TextureTarget`ы каждый кадр.
- **`VFXWorldOverlayRenderer`** (client) — рисует `block_tint`/`block_outline` поверх geometry блока через `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`.
- **`CameraShakeManager`/`CameraMixin`** (client) — суммирует шум всех активных `camera_shake` в оффсет позиции/поворота, применяется миксином к `Camera`.
- **`VFXWorldBindings`** (main, но данные только на клиенте) — вычисляет `bind`-параметры (`screen_x`, `proximity`, `look`, `camera_yaw_delta`/`pitch_delta`) относительно текущего кадра камеры.

## Дата-флоу за один кадр

1. `GameRendererMixin.tompfx$render` (инжект перед `FogRenderer.endFrame`) — вызывается один раз за кадр:
   - обновляет `VFXWorldBindings` из текущей камеры (позиция, yaw/pitch, view-rotation-projection матрица);
   - продвигает `VFXEffectManager.clock` на `deltaTicks` (`DeltaTracker.getGameTimeDeltaTicks()`, 0 на паузе);
   - `VFXEffectManager.update()` — чистит завершённые эффекты, триггерит должные сработать дочерние эффекты коллекций, продвигает таймлайны;
   - `VFXPostProcessingManager.process(...)` — прогоняет цепочку шейдерных пассов.
2. `CameraMixin` (инжекты в `Camera.calculateFov`/`update`) — читает уже обновлённый `VFXEffectManager` для FOV-дельты и шейка камеры.
3. `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN` — `VFXWorldOverlayRenderer` рисует мировые оверлеи для активных `block_tint`/`block_outline`.

## Пост-обработка (пайплайн)

Хук — прямо перед `FogRenderer.endFrame()` в `GameRenderer.render`, то есть после мира и ванильного пост-чейна, но до GUI. Каждый активный пост-эффект раскладывается в один или несколько шейдерных пассов (`VFXShaderPrograms.getPrograms(type)`, например `blur` = X+Y). Копия `mainTarget` → `pingPong[0]`, дальше цепочка пассов чередует `pingPong[0]`/`pingPong[1]`, последний пасс пишет обратно в `mainTarget`. Каждый пасс — ortho-проекция + UBO `SamplerInfo` (размеры in/out) + опциональный UBO `Config` (параметры эффекта, смешанные с нейтральным значением по текущему fade-весу — `VFXEffectType.neutralValue`), оба через `MappableRingBuffer` (маппится и ротируется каждый кадр).

## Мировые оверлеи

`block_tint`/`block_outline` рисуются не шейдерным пассом, а геометрией: запечённые квады модели блока (`ModelManager.getBlockStateModelSet()`, фолбэк — полный куб), трансформированные в `PoseStack` относительно камеры. `block_outline` поддерживает два режима (`shell` параметр): `0` — стенки (каждая грань выдавливается наружу вдоль нормали, физически не может закрыть сам блок), `1` — классическая расширенная оболочка задними гранями с back-face culling, обрезаемая depth-буфером самого блока.

## Ограничения нагрузки (защита от спама эффектами)

| Константа | Значение | Где |
|---|---|---|
| `MAX_ACTIVE_EFFECTS` | 64 | `VFXEffectManager` — при превышении удаляется самый старый активный, с warning в лог |
| `MAX_SCHEDULED_EFFECTS` | 128 | `VFXEffectManager` — лишние дочерние эффекты коллекций отбрасываются |
| `MAX_COLLECTION_DEPTH` | 4 | `VFXEffectManager` — глубже вложенные коллекции игнорируются |

Любая новая коллекция/мапа, растущая от сетевого или датапак-ввода, должна получить аналогичный лимит (см. `AGENTS.md`).

## Отказоустойчивость

- `VFXDefinitionManager.prepare()` — одна поломанная запись датапака логируется и пропускается, остальные грузятся нормально (см. `docs/CHANGELOG.md`, фикс с `IllegalArgumentException`).
- `VFXWorldOverlayRenderer.render()` — рендер каждого эффекта обёрнут в try/catch с логом; ошибка одного эффекта не блокирует остальные и не роняет кадр.
- `VFXClient.handleTrigger` — пакет с несовпадающей `protocolVersion` молча игнорируется вместо падения.

---
Смотри также: [API.md](API.md) — публичный Java API и сетевой протокол, [../docs/GUIDE.md](GUIDE.md) — пользовательский гайд.

# Java API

Публичный API для других модов, взаимодействующих с tompfx. Обратная совместимость важна — не ломай сигнатуры без веской причины (см. `AGENTS.md`).

## `com.tom.vfx.api.VFXAPI`

Класс без состояния (все методы статические), точка входа для остальных модов.

### Сервер → клиент (по сети)

```java
// Резолвит датапак/встроенное определение effectId, мёржит его дефолтные константные
// параметры с overrides, берёт длительность/easing из определения (если не заданы явно),
// и шлёт VFXTriggerPayload игроку. Возвращает false, если effectId неизвестен.
boolean sendEffect(ServerPlayer player, Identifier effectId, Map<String, Float> overrides, @Nullable EasingType easing);

// Явный вариант без обращения к реестру определений — все поля пакета задаются вручную.
void sendEffect(ServerPlayer player, Identifier effectId, int durationTicks, Map<String, Float> params, EasingType easing);

// Останавливает эффект на клиенте игрока.
void sendStop(ServerPlayer player, Identifier effectId);
```

### Клиент (локально, без сети)

```java
// durationTicks: 0 = дефолт определения, отрицательное = persistent.
// Возвращает false, если вызвано не на клиенте (например, на dedicated-сервере) —
// в этом случае нужно использовать sendEffect().
boolean playEffect(Identifier effectId, int durationTicks, Map<String, Float> params, @Nullable EasingType easing);
boolean playEffect(Identifier effectId, int durationTicks, Map<String, Float> params); // linear easing

boolean stopEffect(Identifier effectId);
boolean stopAllEffects();
```

### `VFXLocalDispatcher`

Мост, который клиентский энтрипоинт (`VFXClient`) регистрирует через `VFXAPI.setLocalDispatcher(...)`, чтобы `playEffect`/`stopEffect`/`stopAllEffects` могли выполниться без сетевого пакета. Другим модам реализовывать его не нужно — это внутренняя часть связи common ↔ client кода мода.

## Реестр определений — `VFXDefinitionManager`

```java
VFXDefinitionManager.get().get(effectId);        // VFXDefinition или null
VFXDefinitionManager.get().contains(effectId);   // есть ли эффект (встроенный или из датапака)
VFXDefinitionManager.get().getDefinitions();      // Map<Identifier, VFXDefinition> — снимок всех известных эффектов
```

Обновляется на каждом `/reload` (см. `VFXDefinitionManager.prepare`/`apply`); одна поломанная запись в датапаке логируется и пропускается, остальные эффекты продолжают грузиться нормально.

## Сетевой протокол

Пакет `tompfx:vfx_trigger` (`VFXTriggerPayload`), направление — clientbound play.

| Поле | Тип | Описание |
|---|---|---|
| `protocolVersion` | byte | Текущее значение — `VFXTriggerPayload.PROTOCOL_VERSION`. Клиент **молча игнорирует** пакет при несовпадении версии (см. `VFXClient.handleTrigger`). |
| `effectId` | `Identifier` | ID эффекта (встроенный или датапак) |
| `action` | `VFXAction` (`PLAY`/`STOP`) | |
| `durationTicks` | varint | 0 = дефолт определения, отрицательное = persistent (только для `PLAY`) |
| `params` | `Map<String, Float>` | Переопределения констант, только числа |
| `easing` | `EasingType` (строка) | |

Бампай `PROTOCOL_VERSION` при любом breaking-изменении формата пакета — иначе старые клиенты будут молча игнорировать новые пакеты без единого предупреждения в лог.

---
Смотри также: [../docs/GUIDE.md](GUIDE.md) — пользовательский гайд (команды, датапаки), [ARCHITECTURE.md](ARCHITECTURE.md) — как это рендерится под капотом.

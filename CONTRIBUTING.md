# Contributing

## Ветки

Модель — GitHub Flow: `main` всегда собирается и готова к релизу, вся работа — в короткоживущих ветках от `main`, мёрж через PR.

| Префикс | Когда использовать | Пример |
|---|---|---|
| `feature/` | новая функциональность | `feature/block-outline-shell-mode` |
| `fix/` | исправление бага | `fix/vfx-definition-reload-crash` |
| `chore/` | рутина: зависимости, конфиги, CI, форматирование | `chore/bump-fabric-loader` |
| `refactor/` | изменение структуры кода без изменения поведения | `refactor/effect-manager-split` |
| `docs/` | только документация | `docs/update-guide-v12` |
| `release/` | подготовка релиза (версия, changelog) | `release/1.1.0` |
| `hotfix/` | срочный фикс прямо в проде/релизной ветке | `hotfix/network-payload-oom` |

`main` защищена: прямые пуши запрещены, только PR с зелёной сборкой (`./gradlew build`).

## Коммиты

[Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <описание>`. `scope` — опционален, обычно пакет/подсистема (`effect`, `network`, `render`, `command`, `resource`, `client`).

| Тип | Значение |
|---|---|
| `feat` | новая функциональность, видимая пользователю/API |
| `fix` | исправление бага |
| `docs` | только документация (README, GUIDE, AGENTS, комментарии-доки) |
| `style` | форматирование, отступы, точки с запятой — без изменения логики |
| `refactor` | изменение кода без изменения поведения и без новой функциональности |
| `perf` | изменение, направленное на производительность |
| `test` | добавление/правка тестов |
| `build` | система сборки, зависимости (`build.gradle`, `gradle.properties`) |
| `ci` | конфигурация CI (`.github/workflows/*`) |
| `chore` | прочая рутина, не подпадающая под остальные типы |
| `revert` | откат предыдущего коммита |

Breaking change — восклицательный знак после типа/scope (`feat!:` или `feat(network)!:`) и/или футер `BREAKING CHANGE: <описание>`.

### Примеры (из истории проекта)

```
feat(effect): add shell mode to block_outline

Adds a boolean `shell` param selecting between wall-extrusion and
scaled-shell outline rendering, per GUIDE.md v11.
```

```
fix(resource): don't abort VFX definition reload on one bad file

prepare() only caught JsonParseException/IllegalStateException/IOException,
but VFXDefinition.parse() throws IllegalArgumentException for unknown
effect types and malformed positions. A single bad datapack file was
aborting the whole reload instead of being skipped.
```

```
fix(network)!: cap VFXTriggerPayload params map size

BREAKING CHANGE: bumps PROTOCOL_VERSION to 2; clients on protocol 1
will ignore packets from servers running this version.
```

```
docs: move usage guide to docs/GUIDE.md, add README/AGENTS/CONTRIBUTING
```

```
chore(build): require JDK 25 toolchain via gradle.properties
```

```
refactor(client): extract CameraShakeManager from VFXEffectManager
```

## Pull Request

- Один PR — одна логическая единица работы (не смешивай `feat` и `refactor` без необходимости).
- PR должен собираться: `./gradlew build` зелёный.
- Если меняется поведение эффектов, команд или Java API — обнови `docs/GUIDE.md` (добавь запись в changelog внизу файла) в том же PR.
- Правки в форматирование/стиль кода — см. `AGENTS.md`.

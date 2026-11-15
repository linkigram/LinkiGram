# Аудит LinkiGram — 2026-11-15

## Итог и границы

Проверка **не подтверждает, что все функции работают**. Выполнены общий статический
проход и доступные host-тесты; полного Android end-to-end покрытия нет. Исходники
были проверены без удаления данных аккаунтов, тестовой отправки личных сообщений
или изменений пользовательских настроек. Desktop, лендинг и сетевые обходы не менялись.

Автоматический инвентарь: [FEATURE_HOOK_AUDIT.md](FEATURE_HOOK_AUDIT.md).
Он перечисляет 302 изменяемые скалярные настройки `NimarkoConfig` и их прямые
подключения по 2840 Java/Kotlin-файлам. Для 144 полей не найдено прямой ссылки
вне preferences. Это **кандидаты**, а не 144 доказанные поломки: некоторые настройки
читаются accessor-методами, через ключи preferences или другими конфигами.

## Исправлено в этом проходе

1. **Исчезновение селектора нижней панели.** `onDetachedFromWindow` сбрасывал
   `selectorInitialized`, но обратного восстановления не было. Добавлено
   восстановление по выбранной вкладке при attach и после раскладки.
2. **Ложный счётчик отложенных сообщений.** Архив удалённых сообщений
   подмешивался в любую `messagesDidLoad`, включая служебную загрузку scheduled.
   Теперь подмешиваются только страницы того же режима, что открыт, и только
   default/saved. Обновление scheduled-счётчика также требует `MODE_SCHEDULED`.
3. **Linki-Share перенесён в основную группу.** Добавлена выключенная по умолчанию
   настройка «На всех экранах LinkiGram»: обмен работает при открытом приложении
   и разблокированном телефоне, а не только на экране Linki-Share.
4. **NFC lifecycle/privacy.** Режимы reader/HCE не конкурируют с выделенным экраном;
   пауза приложения отключает обмен; блокировка запрещает выдачу уже выбранного
   NDEF-файла; смена аккаунта/сессии сбрасывает кэш APDU. Приём вне экрана
   предлагает подтверждение перед открытием профиля и имеет cooldown.
5. **NFC профиль.** Убрано условие, которое принимало первого пользователя
   ответа resolveUsername даже при несовпадении ID. Поздние callbacks проверяют
   сессию. Убрано повторное URL-декодирование имён (`+`/`%`).
6. **Выделенные сообщения You.** Обычный и выделенный пузыри получали один цвет.
   Выделение теперь имеет отдельный тон с сохранением контраста текста.
7. **Тест темы.** Обновлены устаревшие Android-фейки; проверка выполняется на
   production-палитре, а не заменена проверкой ожидаемого текста исходника.

## Выполненные проверки

| Проверка | Результат | Что не покрывает |
| --- | --- | --- |
| Java syntax parser | 2825 файлов: 2824 без ошибок; неизменённый `MediaController.java` содержит прежний NUL в строке XMP | Classpath, Android API, поведение |
| XML parse | Все 1162 XML ресурсов парсятся | Все правила aapt и визуальное оформление |
| `Tools/test_history_and_dates.py` | 41 проверка + запрос DAO после переоткрытия SQLite, PASS | Android Room, реальное удаление вторым аккаунтом |
| `Tools/test_linkishare.py` | 30 проверок production payload/HCE, PASS | Реальный NFC, discovery, две антенны, порядок Android lifecycle |
| `Tools/test_main_tabs_glass.py` | SkSL компилируется; 48 комбинаций темы/масштаба/света/нажатия, PASS | Android AGSL/RenderNode, FPS и внешний вид на телефоне |
| `Tools/test_coloros17.py` | 48 проверок ColorOS/You, PASS | Визуальная раскладка и переходы |
| `Tools/test_lrcmux_words.py` | Offline word-timing fixtures, PASS | Доступность живых lyrics-провайдеров |
| Python suite, 247 тестов | 225 PASS, 7 FAIL, 14 ERROR, 1 SKIP | Не является полностью зелёным набором |
| Изолированный `test_plugin_lifecycle_isolation.py` | 15 PASS | Полная последовательность импорта остальных тестов |

В первом запуске suite были ещё две ошибки из-за отсутствия PyYAML в системном
Python; запуск повторён в окружении с PyYAML. Итоговая таблица содержит результаты
повторного запуска, не смешивает их с первым.

### Незелёные / неактуальные тесты

- 14 ERROR преимущественно связаны с поиском отсутствующих сигнатур в исходниках
  (`ValueError: substring not found`): exact-callback retirement, pip lifecycle,
  plugin transactions и install authorization. Один тест ожидает отсутствующий
  внешний плагин NimarkoPrivacy: тест обращается за пределы репозитория и получает
  отказ доступа. Это требует сверки контрактов, а не удаления assertions.
- 6 source-inspection FAIL: SDK/Pine guard, идентичность ошибок Python callback,
  replacement runtime guard, mailbox публикация, startup diagnostics и deferred
  удаление overlay. По наличию/отсутствию строки нельзя объявить реальный путь
  безопасным или сломанным; эти контракты остаются на отдельную проверку.
- 1 FAIL `test_allowed_future_callback_is_watchdog_tracked` возникает в общем
  suite; весь соответствующий модуль отдельно проходит 15/15. Вероятна утечка
  глобальных monkeypatch между тестовыми модулями; продуктовый дефект не установлен.
- `Tools/test_ai_autoreply.py` не запускается: отсутствует
  `app/nimarkogram/messenger/ai/AutoReplyController.java`.
- `tools/liner-lyrics/check.cjs` не запускается: Liner engine удалён ранее по запросу.
  Отсутствующие функции не восстанавливались ради зелёного теста.

## Проверенные области и остающиеся сценарии

Пути ниже относительно `TMessagesProj/src/main/java/`. «Есть подключение» означает
прослеживаемую ссылку в коде, а **не доказанную работоспособность**.

| Подсистема | Что просмотрено | Что ещё проверить |
| --- | --- | --- |
| Хаб/поиск настроек | `preferences/MainPreferencesActivity`, `search/LinkiSettingsSearch`, генератор каталога | Все переходы, RTL, крупный шрифт, сохранение запроса |
| You/ColorOS/Glass | Палитры, выбранные пузыри, селектор, lifecycle панели, shader tests | Attach/detach, удержание/drag, режимы dock/floating, Android 24/33+, светлая/тёмная темы |
| Linki-Share | Payload, APDU, account ownership, reader/HCE, экран, foreground-controller | Два NFC/HCE-телефона; экран/любой раздел; один аккаунт; блокировка; уход из приложения; NFC off/on |
| Удалённая история/чаты | `AyuHistoryHook`, `AyuDeletedDialogs`, DAO, точки delete/load, scheduled guard | Рестарт/force-stop, больше 200 записей, альбомы, повторное удаление, оба варианта локальной копии |
| Приватность/ghost | `LinkiPrivacy`, suite `test_ghost_privacy_runtime`, конфиги и request-hook | Серверные read/online/typing, исключения чатов; локальное скрытие номера не меняет серверную приватность |
| Меню чатов/drawer | Прямые config-хуки и сохранение порядка в инвентаре | Drag/reset, скрытые пункты, «Прочитать все» для конкретной папки |
| Плагины | Полный доступный Python suite; startup/rescan/callback/settings tests | Реальные плагины, рестарт, выключение/удаление, Python/Java hook, незелёные safety-контракты выше |
| ИИ/агент | Каталог endpoint/service и экран провайдеров присутствуют; старый autoreply-тест не соответствует дереву | Реальные ответы каждого провайдера, Free/rate-limit, vision/file attach, отмена запроса |
| Плеер/лирика | Production lrcmux-parser fixtures и hooks из инвентаря | Живая лирика, пословная подсветка, очередь/shuffle/drag, background playback |
| Перевод/речь | Подсистемы ML Kit/Vosk присутствуют в дереве | Загрузка модели, авиарежим, неподдерживаемый язык, ошибки/отмена |
| Камера/медиа | Подсистемы camera/media и прямые config-подключения перечислены | Реальные CameraX-запись, микрофон, смена камеры, разные версии Android |
| Сессии | `org/telegram/messenger/sessions` присутствует; текущий проход не выполнял вход/выход | Экспорт/импорт всех форматов тестовым аккаунтом, отказ/таймаут без порчи активной сессии |
| Иконки | Ресурсы/manifest входят в общий XML-проход | Смена alias и возврат дефолта в разных launcher |
| Локальные/автоматические сообщения | Модули local/automation учтены, scheduled-путь отделён от архива | Повторы после рестарта, отмена, часовой пояс, отсутствие дублей отправки |
| Бейджи/баннеры/quick-info | Конфиги и исходники учтены | Ответы backend, пустые/ошибочные данные, отказ сети |
| Обновления/диагностика | Код updater и tests startup учтены | Проверка обновления/подписи; незелёный startup diagnostics test |
| X-CFG/wsbypass/landing/Desktop | Только инвентарь ссылок Android | Не менялись; живые сети, сайты и Desktop-сборка этим аудитом не проверены |

### Настройки без найденного потребителя

Ручной grep подтвердил отсутствие использования вне объявления/переключателя/UI
для `hideActionBarStatus`, `disableSendHints`, `hideBubbleTail`, `gifSpoilers`,
`forceBlur`, `autoQuoteReplies`. В этом дереве их нельзя выдавать за работающие
функции только на основании наличия свитча. Исправление всех неподключённых
настроек — отдельная серия реализаций и device-тестов; массово включать их
непроверенными хуками или скрывать без согласования в этом проходе не стали.

## Команды воспроизведения

```sh
python3 Tools/audit_feature_hooks.py --output docs/FEATURE_HOOK_AUDIT.md
python3 Tools/test_history_and_dates.py
python3 Tools/test_linkishare.py
python3 Tools/test_main_tabs_glass.py
python3 Tools/test_coloros17.py --java-home /path/to/jdk17 --material-jar /path/to/material-classes.jar
python3 Tools/test_lrcmux_words.py --json-jar /path/to/org-json.jar
python3 -m unittest discover -s TMessagesProj/src/test/python -p 'test_*.py'
```

Android APK собирается только через GitHub Actions. Компиляция и установка
подтверждают сборку/подпись, но не заменяют перечисленные device-тесты.

#!/usr/bin/env python3
"""Inventory direct NimarkoConfig references; presence is NOT proof of correct behavior."""
import argparse
from collections import defaultdict
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "TMessagesProj/src/main/java"
CONFIG = SOURCE / "app/nimarkogram/messenger/NimarkoConfig.java"


def without_comments(text):
    tokens = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(tokens, lambda m: re.sub(r'[^\n]', ' ', m[0]) if m[0].startswith(('//', '/*')) else m[0], text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    config = without_comments(CONFIG.read_text())
    fields = set(re.findall(r'public\s+static\s+(?:volatile\s+)?(?:boolean|int|long|float|double|String)\s+(\w+)\s*=', config))
    settings, runtime = defaultdict(set), defaultdict(set)
    modules = defaultdict(int)
    source_count = 0
    for path in sorted(SOURCE.rglob('*')):
        if path.suffix not in ('.java', '.kt'): continue
        source_count += 1
        relative = path.relative_to(SOURCE).as_posix()
        if relative.startswith('app/nimarkogram/messenger/'):
            module = relative.split('/')[3] if len(relative.split('/')) > 4 else '(root)'
            modules[module] += 1
        if path == CONFIG: continue
        text = without_comments(path.read_text(errors='replace'))
        for line_num, line in enumerate(text.splitlines(), 1):
            for field in re.findall(r'\bNimarkoConfig\.(\w+)\b', line):
                if field not in fields: continue
                target = settings if '/preferences/' in relative else runtime
                target[field].add(f'{relative}:{line_num}')
    missing = sorted(name for name in fields if not runtime[name])
    lines = ['# Инвентарь прямых подключений настроек LinkiGram', '',
             'Сгенерировано `Tools/audit_feature_hooks.py`. Проверяются Java/Kotlin в `src/main/java`.', '',
             '**Это поиск ссылок, не функциональный тест.** Ссылки могут быть записью, а не чтением;',
             'функции могут использовать accessor, static import или рефлексию. Отсутствие прямой ссылки',
             'означает кандидата для ручной проверки, не доказанный дефект. Константы и настройки',
             'других конфигов не включены в таблицу NimarkoConfig.', '',
             f'- Исходников просмотрено автоматически: **{source_count}**.',
             f'- Публичных изменяемых скалярных полей NimarkoConfig: **{len(fields)}**.',
             f'- Без прямых ссылок вне preferences: **{len(missing)}**.', '',
             '## Модули LinkiGram', '', '| Модуль | Java/Kotlin файлов |', '| --- | ---: |']
    lines += [f'| `{module}` | {count} |' for module, count in sorted(modules.items())]
    lines += ['', '## Кандидаты без прямого runtime-хука', '', ', '.join(f'`{name}`' for name in missing), '',
              '## Все скалярные настройки NimarkoConfig', '', '| Поле | Ссылок вне preferences | Примеры |', '| --- | ---: | --- |']
    for name in sorted(fields):
        refs = sorted(runtime[name])
        examples = '<br>'.join(f'`{ref}`' for ref in refs[:3]) or 'Только preferences / accessor / не используется: требуется ручная проверка'
        lines.append(f'| `{name}` | {len(refs)} | {examples} |')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text('\n'.join(lines) + '\n')
    print(f'{source_count} sources; {len(fields)} scalar settings; {len(missing)} candidates without direct runtime references')
    print(args.output)


if __name__ == '__main__':
    main()

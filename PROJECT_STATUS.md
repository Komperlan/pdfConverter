# Состояние проекта

Актуализировано: 24 июня 2026 года.

DocConverter находится в состоянии рабочего full-stack MVP. Реализован полный
сценарий от загрузки DOC/DOCX в веб-интерфейсе до асинхронной обработки и
скачивания PDF.

## Реализовано

- REST API загрузки, статуса и результата;
- PostgreSQL persistence и baseline-миграция Flyway;
- безопасное локальное файловое хранилище;
- HTTP adapter Carbone;
- worker, retry backoff и recovery зависших попыток;
- retention cleanup и статус `EXPIRED`;
- React/Vite frontend с клиентской валидацией и контролируемым polling;
- same-origin nginx proxy для frontend API-запросов;
- отдельные Dockerfile для backend и frontend, общий Docker Compose;
- unit, HTTP integration и PostgreSQL Testcontainers tests.

## Не проверено

- повторный Testcontainers-прогон PostgreSQL после схлопывания схемы в baseline;
- визуальная проверка frontend в реальном браузере на desktop и mobile.

## Последняя проверка

- 74 unit/MockMvc/storage/integration теста: успешно;
- test compilation всего набора: успешно;
- сборка executable JAR: успешно;
- полный Docker Compose стек: все четыре сервиса healthy;
- ручной end-to-end через frontend proxy: DOCX успешно преобразован в PDF;
- загрузка, polling, скачивание и JSON-ошибки API: успешно;
- `docker compose config`: успешно;
- frontend lint: успешно;
- frontend tests: 6 тестов успешно;
- frontend production build: успешно.

Подробные команды запуска, конфигурация и ограничения описаны в `README.md`.

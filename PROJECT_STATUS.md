# Состояние проекта

Актуализировано: 22 июня 2026 года.

DocConverter находится в состоянии рабочего backend MVP. Реализован полный
сценарий от загрузки DOC/DOCX до асинхронной обработки и скачивания PDF.

## Реализовано

- REST API загрузки, статуса и результата;
- PostgreSQL persistence и baseline-миграция Flyway;
- безопасное локальное файловое хранилище;
- HTTP adapter Carbone;
- worker, retry backoff и recovery зависших попыток;
- retention cleanup и статус `EXPIRED`;
- Dockerfile и Docker Compose;
- unit, HTTP integration и PostgreSQL Testcontainers tests.

## Не проверено

- end-to-end конвертация настоящего DOC/DOCX официальным Docker-образом Carbone;
- повторный Testcontainers-прогон PostgreSQL после схлопывания схемы в baseline;
- полный Docker Compose запуск после последнего изменения baseline-схемы.

## Последняя проверка

- 65 unit/MockMvc/storage тестов: успешно;
- test compilation всего набора: успешно;
- сборка executable JAR: успешно;
- `docker compose config`: успешно.

Подробные команды запуска, конфигурация и ограничения описаны в `README.md`.

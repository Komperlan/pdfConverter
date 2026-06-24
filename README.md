# DocConverter

Веб-приложение для асинхронной конвертации документов `DOC` и `DOCX` в `PDF`.
React-клиент загружает файл, отслеживает состояние задания и позволяет скачать
готовый результат. Backend выполняет обработку через Carbone.

## Возможности

- загрузка одного документа размером до 10 МБ;
- проверка расширения, MIME type и содержимого через Apache Tika;
- локальное хранение исходников и PDF с безопасными UUID-именами;
- асинхронная конвертация через HTTP API Carbone;
- сохранение заданий и попыток в PostgreSQL;
- ограничение параллельности worker-а;
- экспоненциальные повторные попытки;
- восстановление зависших `PROCESSING`-заданий;
- удаление файлов после окончания срока хранения;
- React-интерфейс загрузки, проверки статуса и скачивания результата;
- OpenAPI и Swagger UI.

## Стек

| Компонент | Версия / реализация |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.0 |
| PostgreSQL | 18 |
| Flyway | версия из Spring Boot BOM |
| Apache Tika | 3.3.1 |
| Testcontainers | 2.0.5 |
| Конвертер | Carbone HTTP API |
| Сборка | Maven |
| Frontend | React 19, TypeScript, Vite |
| Web proxy | nginx |

Точные версии библиотек зафиксированы в `pom.xml` и `backend/pom.xml`.

## API

| Метод | Endpoint | Ответ |
| --- | --- | --- |
| `POST` | `/api/v1/conversions` | `202 Accepted`, UUID и состояние задания |
| `GET` | `/api/v1/conversions/{id}` | состояние и метаданные задания |
| `GET` | `/api/v1/conversions/{id}/result` | готовый PDF |

### Создание задания

```bash
curl -F "file=@document.docx" http://localhost:8080/api/v1/conversions
```

Поддерживаемые статусы:

- `CREATED`;
- `PROCESSING`;
- `COMPLETED`;
- `FAILED`;
- `EXPIRED`.

Endpoint результата возвращает:

| Состояние | HTTP |
| --- | --- |
| UUID не найден | `404 Not Found` |
| `CREATED` или `PROCESSING` | `409 Conflict` |
| срок хранения истек | `410 Gone` |
| конвертация завершилась ошибкой | `422 Unprocessable Content` |
| PDF готов | `200 OK` |

Swagger UI после запуска доступен по адресу
`http://localhost:8080/swagger-ui.html`.

## Архитектура

Код разделен на четыре области:

- `api`: HTTP-контроллеры, DTO и обработка ошибок;
- `application`: сценарии приложения и входные/выходные порты;
- `domain`: JPA-модели и правила переходов состояний;
- `infrastructure`: PostgreSQL, local storage, Carbone и schedulers.

`DocumentConverterPort` и `FileStoragePort` отделяют application layer от
конкретного HTTP-конвертера и файловой системы. Идентификатор внешнего запроса
хранится как `externalRequestId`, поэтому модель попытки не зависит от Carbone.

Попытки конвертации хранятся отдельными сущностями. `ConversionJob` контролирует
номер и максимальное количество попыток, а `ConversionAttemptRepository`
отвечает за их persistence.

## Конфигурация

Для запуска backend обязательны переменные PostgreSQL:

| Переменная | Пример |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/docconverter` |
| `SPRING_DATASOURCE_USERNAME` | `docconverter` |
| `SPRING_DATASOURCE_PASSWORD` | `<password>` |

Основные необязательные настройки:

| Переменная | По умолчанию |
| --- | --- |
| `CARBONE_BASE_URL` | `http://localhost:4000` |
| `CARBONE_CONNECT_TIMEOUT` | `5s` |
| `CARBONE_READ_TIMEOUT` | `65s` |
| `DOC_CONVERTER_STORAGE_ROOT` | `./storage` |
| `CONVERSION_JOB_EXPIRATION` | `24h` |
| `CONVERSION_JOB_MAX_ATTEMPTS` | `3` |
| `CONVERSION_WORKER_MAX_PARALLELISM` | `2` |
| `CONVERSION_RETRY_INITIAL_DELAY` | `5s` |
| `CONVERSION_RETRY_MAX_DELAY` | `1m` |
| `CONVERSION_RECOVERY_STALE_TIMEOUT` | `2m` |
| `CONVERSION_RETENTION_CLEANUP_INTERVAL` | `1m` |

Полный список значений находится в `backend/src/main/resources/application.yml`.

## Запуск

Через Docker Compose:

```bash
docker compose up --build
```

Локальные значения читаются из `.env`. Репозиторий содержит безопасный шаблон
`.env.example`; сам `.env` исключён из Git.

После запуска веб-интерфейс доступен на `http://localhost:3000`, backend API —
на `http://localhost:8080`. Порт интерфейса можно изменить переменной
`FRONTEND_PORT`.

Локальный запуск только backend:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/docconverter
export SPRING_DATASOURCE_USERNAME=docconverter
export SPRING_DATASOURCE_PASSWORD=local-password
mvn -pl backend spring-boot:run
```

Схема пока имеет одну baseline-миграцию `V1__create_conversion_schema.sql`.
После перехода со старой цепочки `V1-V5` локальный PostgreSQL volume необходимо
пересоздать один раз:

```bash
docker compose down -v
```

Команда удаляет локальные данные PostgreSQL и сохраненные тестовые файлы.

## Тесты

Backend:

```bash
mvn -pl backend test
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm test -- --run
npm run build
```

Unit-тесты проверяют доменные переходы, загрузку, validation, processing,
recovery, retention, local storage и HTTP API. Integration-тест Carbone adapter
использует настоящий HTTP transport и локальный stub-сервер. Testcontainers
поднимает чистый PostgreSQL 18, применяет baseline-миграцию, запускает Hibernate
schema validation и проверяет конкурентный `FOR UPDATE SKIP LOCKED`.

Для полного набора тестов должен быть доступен Docker.

## Ограничения

- inline API Carbone требует base64, поэтому исходник и PDF временно находятся
  в памяти адаптера;
- реализовано только локальное файловое хранилище;
- реальный образ Carbone пока не проверяется автоматическим end-to-end тестом.

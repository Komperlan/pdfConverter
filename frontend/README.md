# DocConverter Frontend

Веб-интерфейс для загрузки DOC/DOCX, отслеживания конвертации и скачивания PDF.

## Локальный запуск

Backend должен быть доступен на `http://localhost:8080`. Vite проксирует запросы `/api` к backend, поэтому отдельная CORS-конфигурация для локальной разработки не нужна.

```bash
npm ci
npm run dev
```

Откройте `http://localhost:5173`.

Для другого адреса backend задайте proxy target:

```bash
VITE_BACKEND_PROXY_TARGET=http://localhost:8081 npm run dev
```

Для статической сборки API по умолчанию вызывается через `/api/v1` на том же origin. При необходимости базовый URL можно задать во время сборки:

```bash
VITE_API_BASE_URL=https://example.org/api/v1 npm run build
```

## Проверки

```bash
npm run lint
npm test -- --run
npm run build
```

import { useEffect, useRef, useState } from 'react';
import {
  ApiRequestError,
  getConversion,
  resultUrl,
  submitConversion,
  type ConversionJob,
} from './api';
import './App.css';

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const POLL_INTERVAL_MS = 2_000;
const MAX_POLL_DURATION_MS = 30 * 60 * 1_000;
const ACTIVE_STATUSES = new Set(['CREATED', 'PROCESSING']);

type ViewState =
  | { kind: 'idle' }
  | { kind: 'uploading' }
  | { kind: 'processing'; job: ConversionJob }
  | { kind: 'completed'; job: ConversionJob }
  | { kind: 'error'; message: string };

function validateFile(file: File): string | null {
  if (file.size === 0) {
    return 'Файл пуст. Выберите документ с содержимым.';
  }

  if (file.size > MAX_FILE_SIZE_BYTES) {
    return 'Файл превышает максимальный размер 10 МБ.';
  }

  if (!/\.(doc|docx)$/i.test(file.name)) {
    return 'Поддерживаются только файлы DOC и DOCX.';
  }

  return null;
}

function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const handleAbort = () => {
      clearTimeout(timeoutId);
      reject(new DOMException('Request aborted', 'AbortError'));
    };
    const timeoutId = window.setTimeout(() => {
      signal.removeEventListener('abort', handleAbort);
      resolve();
    }, milliseconds);

    signal.addEventListener('abort', handleAbort, { once: true });
  });
}

async function pollUntilFinished(
  initialJob: ConversionJob,
  signal: AbortSignal,
  onUpdate: (job: ConversionJob) => void,
): Promise<ConversionJob> {
  let job = initialJob;
  const expiresAt = Date.parse(job.expiresAt);
  const deadline = Math.min(
    Number.isNaN(expiresAt) ? Number.POSITIVE_INFINITY : expiresAt,
    Date.now() + MAX_POLL_DURATION_MS,
  );

  while (ACTIVE_STATUSES.has(job.status)) {
    if (Date.now() >= deadline) {
      throw new Error('Время ожидания результата истекло. Проверьте задание позже.');
    }

    await wait(POLL_INTERVAL_MS, signal);
    job = await getConversion(job.id, signal);
    onUpdate(job);
  }

  return job;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.status) {
      case 400:
        return 'Файл пуст или повреждён.';
      case 413:
        return 'Файл превышает максимальный размер 10 МБ.';
      case 415:
        return 'Формат файла не поддерживается или не соответствует содержимому.';
      default:
        return error.message;
    }
  }

  if (error instanceof TypeError) {
    return 'Не удалось связаться с сервером. Проверьте, что backend запущен, и повторите попытку.';
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'Не удалось выполнить запрос. Повторите попытку.';
}

function App() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [view, setView] = useState<ViewState>({ kind: 'idle' });
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const requestRef = useRef<AbortController | null>(null);

  const isBusy = view.kind === 'uploading' || view.kind === 'processing';
  const fileName = selectedFile ? `📎 ${selectedFile.name}` : 'Файл не выбран';

  useEffect(() => () => requestRef.current?.abort(), []);

  const selectFile = (file: File | null) => {
    requestRef.current?.abort();

    if (!file) {
      setSelectedFile(null);
      setView({ kind: 'idle' });
      return;
    }

    const validationError = validateFile(file);
    if (validationError) {
      setSelectedFile(null);
      setView({ kind: 'error', message: validationError });
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    setSelectedFile(file);
    setView({ kind: 'idle' });
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
    selectFile(event.dataTransfer.files[0] ?? null);
  };

  const handleConvert = async () => {
    if (!selectedFile) {
      setView({ kind: 'error', message: 'Сначала выберите файл.' });
      return;
    }

    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setView({ kind: 'uploading' });

    try {
      const createdJob = await submitConversion(selectedFile, controller.signal);
      setView({ kind: 'processing', job: createdJob });

      const finishedJob = await pollUntilFinished(createdJob, controller.signal, (job) => {
        setView({ kind: 'processing', job });
      });

      if (finishedJob.status === 'COMPLETED' && finishedJob.resultAvailable) {
        setView({ kind: 'completed', job: finishedJob });
      } else if (finishedJob.status === 'EXPIRED') {
        setView({ kind: 'error', message: 'Срок хранения результата истёк.' });
      } else {
        const reason = finishedJob.errorMessage
          ? ` ${finishedJob.errorMessage}`
          : '';
        setView({ kind: 'error', message: `Конвертация не выполнена.${reason}` });
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        setView({ kind: 'error', message: errorMessage(error) });
      }
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null;
      }
    }
  };

  return (
    <div className="app-wrapper">
      <header className="app-header">
        <div className="header-actions" />
      </header>

      <main className="main-content">
        <div className="container">
          <h1>📄 Конвертер DOC → PDF</h1>
          <p className="description">Загрузите файл .doc или .docx, и мы превратим его в PDF</p>

          <div
            className={`upload-area ${isDragging ? 'dragging' : ''}`}
            role="button"
            tabIndex={isBusy ? -1 : 0}
            onClick={() => !isBusy && fileInputRef.current?.click()}
            onKeyDown={(event) => {
              if (!isBusy && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                fileInputRef.current?.click();
              }
            }}
            onDragEnter={(event) => {
              event.preventDefault();
              if (!isBusy) setIsDragging(true);
            }}
            onDragLeave={(event) => {
              event.preventDefault();
              setIsDragging(false);
            }}
            onDragOver={(event) => event.preventDefault()}
            onDrop={handleDrop}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept=".doc,.docx,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
              style={{ display: 'none' }}
            />
            <label>
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                style={{ display: 'inline-block', marginRight: '8px', verticalAlign: 'middle' }}
              >
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="17 8 12 3 7 8" />
                <line x1="12" y1="3" x2="12" y2="15" />
              </svg>
              {isDragging ? 'Отпустите файл для загрузки' : 'Нажмите, чтобы выбрать файл'}
            </label>
            <p className="file-name">{fileName}</p>
          </div>

          <button type="button" onClick={handleConvert} disabled={isBusy}>
            {selectedFile ? 'Конвертировать выбранный файл' : 'Конвертировать'}
          </button>

          <div
            className="result"
            aria-live="polite"
            style={{
              color: view.kind === 'completed'
                ? '#0a7a3a'
                : view.kind === 'error'
                  ? '#d32f2f'
                  : view.kind === 'uploading' || view.kind === 'processing'
                    ? '#1a73e8'
                    : '#666',
            }}
          >
            {view.kind === 'idle' && '▼ Здесь появится результат'}
            {view.kind === 'uploading' && '⏳ Загрузка файла...'}
            {view.kind === 'processing' && `⏳ Статус: ${view.job.status}...`}
            {view.kind === 'completed' && (
              <>
                <span>✅ Конвертация завершена!</span>
                <a
                  className="download-button"
                  href={resultUrl(view.job.id)}
                  download
                >
                  Скачать PDF
                </a>
              </>
            )}
            {view.kind === 'error' && `❌ Ошибка: ${view.message}`}
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;

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

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} Б`;
  }

  return `${(bytes / 1024 / 1024).toFixed(2)} МБ`;
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

  const handleDrop = (event: React.DragEvent<HTMLButtonElement>) => {
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
    <main className="app-shell">
      <section className="converter" aria-labelledby="page-title">
        <div className="title-block">
          <span className="product-name">DocConverter</span>
          <h1 id="page-title">Конвертация DOC в PDF</h1>
          <p>Загрузите один документ DOC или DOCX размером до 10 МБ.</p>
        </div>

        <button
          className={`upload-area${isDragging ? ' upload-area--dragging' : ''}`}
          type="button"
          onClick={() => fileInputRef.current?.click()}
          onDragEnter={(event) => {
            event.preventDefault();
            setIsDragging(true);
          }}
          onDragLeave={(event) => {
            event.preventDefault();
            setIsDragging(false);
          }}
          onDragOver={(event) => event.preventDefault()}
          onDrop={handleDrop}
          disabled={isBusy}
        >
          <span className="upload-area__action">
            {isDragging ? 'Отпустите файл' : 'Выберите или перетащите файл'}
          </span>
          <span className="upload-area__details">
            {selectedFile
              ? `${selectedFile.name} · ${formatFileSize(selectedFile.size)}`
              : 'DOC, DOCX · до 10 МБ'}
          </span>
        </button>

        <input
          ref={fileInputRef}
          className="visually-hidden"
          type="file"
          accept=".doc,.docx,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
          tabIndex={-1}
          aria-hidden="true"
        />

        <button
          className="primary-action"
          type="button"
          onClick={handleConvert}
          disabled={isBusy || !selectedFile}
        >
          {view.kind === 'uploading'
            ? 'Загрузка...'
            : view.kind === 'processing'
              ? 'Конвертация...'
              : 'Конвертировать'}
        </button>

        <div className={`status status--${view.kind}`} aria-live="polite">
          {view.kind === 'idle' && 'Результат появится здесь после конвертации.'}
          {view.kind === 'uploading' && 'Файл загружается на сервер.'}
          {view.kind === 'processing' && (
            <>
              Задание выполняется. Статус: <strong>{view.job.status}</strong>
            </>
          )}
          {view.kind === 'completed' && (
            <>
              <span>PDF готов.</span>
              <a className="download-link" href={resultUrl(view.job.id)}>
                Скачать PDF
              </a>
            </>
          )}
          {view.kind === 'error' && view.message}
        </div>
      </section>
    </main>
  );
}

export default App;

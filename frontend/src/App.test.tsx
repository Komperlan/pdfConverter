import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

const completedJob = {
  id: '5ef29e3e-0d2b-488b-8560-72585f991d64',
  originalFilename: 'document.docx',
  status: 'COMPLETED',
  fileSizeBytes: 7,
  attemptCount: 1,
  maxAttempts: 3,
  createdAt: '2026-06-23T12:00:00Z',
  updatedAt: '2026-06-23T12:00:01Z',
  processingStartedAt: '2026-06-23T12:00:00Z',
  processingFinishedAt: '2026-06-23T12:00:01Z',
  nextAttemptAt: null,
  expiresAt: '2026-06-24T12:00:00Z',
  errorCode: null,
  errorMessage: null,
  resultAvailable: true,
};

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('renders the converter and disables submission without a file', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Конвертация DOC в PDF' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Конвертировать' })).toBeDisabled();
  });

  it('rejects unsupported files before sending a request', () => {
    render(<App />);
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['content'], 'notes.txt', { type: 'text/plain' });

    fireEvent.change(input, { target: { files: [file] } });

    expect(screen.getByText('Поддерживаются только файлы DOC и DOCX.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Конвертировать' })).toBeDisabled();
  });

  it('submits a document and renders a safe result link', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(completedJob), {
      status: 202,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['content'], 'document.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    fireEvent.change(input, { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: 'Конвертировать' }));

    const downloadLink = await screen.findByRole('link', { name: 'Скачать PDF' });
    expect(downloadLink).toHaveAttribute(
      'href',
      `/api/v1/conversions/${completedJob.id}/result`,
    );
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/conversions');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).toBe(file);
  });

  it('polls active jobs until the result is ready', async () => {
    vi.useFakeTimers();
    const jobs = [
      { ...completedJob, status: 'CREATED', resultAvailable: false },
      { ...completedJob, status: 'PROCESSING', resultAvailable: false },
      completedJob,
    ];
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(
      new Response(JSON.stringify(jobs.shift()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [new File(['content'], 'document.docx')] },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Конвертировать' }));

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(2_000);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await vi.advanceTimersByTimeAsync(2_000);
    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(3);
      expect(screen.getByRole('link', { name: 'Скачать PDF' })).toBeInTheDocument();
    });
  });

  it('shows a safe message for a rejected file', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: 'Detected content is not a Word document',
    }), {
      status: 415,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [new File(['content'], 'document.docx')] },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Конвертировать' }));

    expect(await screen.findByText(
      'Формат файла не поддерживается или не соответствует содержимому.',
    )).toBeInTheDocument();
  });

  it('shows a clear message when the backend is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    render(<App />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [new File(['content'], 'document.docx')] },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Конвертировать' }));

    expect(await screen.findByText(
      'Не удалось связаться с сервером. Проверьте, что backend запущен, и повторите попытку.',
    )).toBeInTheDocument();
  });
});

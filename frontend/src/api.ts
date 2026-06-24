export type ConversionStatus =
  | 'CREATED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'EXPIRED';

export interface ConversionJob {
  id: string;
  originalFilename: string;
  status: ConversionStatus;
  fileSizeBytes: number;
  attemptCount: number;
  maxAttempts: number;
  createdAt: string;
  updatedAt: string;
  processingStartedAt: string | null;
  processingFinishedAt: string | null;
  nextAttemptAt: string | null;
  expiresAt: string;
  errorCode: string | null;
  errorMessage: string | null;
  resultAvailable: boolean;
}

interface ApiErrorBody {
  message?: unknown;
  code?: unknown;
}

const configuredApiBase = import.meta.env.VITE_API_BASE_URL?.trim();
export const API_BASE = configuredApiBase
  ? configuredApiBase.replace(/\/$/, '')
  : '/api/v1';

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(
    message: string,
    status: number,
    code?: string,
  ) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
  }
}

async function apiError(response: Response): Promise<ApiRequestError> {
  let body: ApiErrorBody = {};

  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // The status still gives the client a useful fallback for non-JSON errors.
  }

  const message = typeof body.message === 'string'
    ? body.message
    : `Сервер вернул ошибку ${response.status}.`;
  const code = typeof body.code === 'string' ? body.code : undefined;
  return new ApiRequestError(message, response.status, code);
}

async function readJob(response: Response): Promise<ConversionJob> {
  if (!response.ok) {
    throw await apiError(response);
  }

  return response.json() as Promise<ConversionJob>;
}

export async function submitConversion(
  file: File,
  signal?: AbortSignal,
): Promise<ConversionJob> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE}/conversions`, {
    method: 'POST',
    body: formData,
    signal,
  });

  return readJob(response);
}

export async function getConversion(
  id: string,
  signal?: AbortSignal,
): Promise<ConversionJob> {
  const response = await fetch(`${API_BASE}/conversions/${encodeURIComponent(id)}`, {
    signal,
  });

  return readJob(response);
}

export function resultUrl(id: string): string {
  return `${API_BASE}/conversions/${encodeURIComponent(id)}/result`;
}

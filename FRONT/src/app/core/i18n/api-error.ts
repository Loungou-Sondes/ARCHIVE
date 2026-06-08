export interface ApiErrorPayload {
  status?: number;
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

export function parseApiError(err: unknown): ApiErrorPayload {
  const res = err as {
    status?: number;
    error?: { code?: string; message?: string; fieldErrors?: Record<string, string> };
  };
  const api = res?.error;
  return {
    status: res?.status,
    code: typeof api?.code === 'string' && api.code.trim() ? api.code.trim() : undefined,
    message: typeof api?.message === 'string' ? api.message : undefined,
    fieldErrors:
      api?.fieldErrors && typeof api.fieldErrors === 'object' && Object.keys(api.fieldErrors).length > 0
        ? api.fieldErrors
        : undefined,
  };
}

function appendFieldErrors(detail: string, fieldErrors?: Record<string, string>): string {
  if (!fieldErrors) {
    return detail;
  }
  const extra = Object.entries(fieldErrors)
    .map(([k, v]) => `${k}: ${v}`)
    .join(' · ');
  return extra ? `${detail} (${extra})` : detail;
}

export function resolveApiErrorDetail(
  translate: (key: string, params?: Record<string, unknown>) => string,
  err: unknown,
  fallbackKey: string,
): string {
  const parsed = parseApiError(err);
  if (parsed.code) {
    const key = `errors.${parsed.code}`;
    const translated = translate(key);
    if (translated !== key) {
      return appendFieldErrors(translated, parsed.fieldErrors);
    }
  }
  return appendFieldErrors(translate(fallbackKey), parsed.fieldErrors);
}

export function resolveApiErrorSummary(
  translate: (key: string) => string,
  err: unknown,
): string {
  const status = parseApiError(err).status;
  if (status === 401) {
    return translate('common.unauthorized');
  }
  if (status === 403) {
    return translate('common.accessDenied');
  }
  return translate('common.error');
}

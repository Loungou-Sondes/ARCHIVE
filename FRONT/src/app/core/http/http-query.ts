import { HttpParams } from '@angular/common/http';

/** Ajoute le paramètre de recherche texte `q` (ignoré si vide). */
export function addQuery(params: HttpParams, q: string | null | undefined): HttpParams {
  const trimmed = q?.trim();
  if (!trimmed) {
    return params;
  }
  return params.set('q', trimmed);
}

/** Fusionne plusieurs champs texte en une seule requête `q`. */
export function buildSearchQuery(...parts: (string | null | undefined)[]): string {
  return parts
    .map((part) => part?.trim())
    .filter((part): part is string => !!part)
    .join(' ')
    .trim();
}

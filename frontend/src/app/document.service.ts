import { HttpClient, HttpEvent, HttpInterceptorFn, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { DocumentCategory } from './categories';

export interface DocumentItem {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: string;
  category: DocumentCategory;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PresignedUrl {
  url: string;
  expiresAt: string;
}

export interface Summary {
  total: number;
  byCategory: Record<DocumentCategory, number>;
}

const USER_KEY = 'dms.userId';

/** Holds the current user id (the API has no login yet; it uses the X-User-Id header). */
@Injectable({ providedIn: 'root' })
export class UserStore {
  readonly userId = signal(readUser());

  set(id: string): void {
    const value = id.trim();
    this.userId.set(value);
    try { localStorage.setItem(USER_KEY, value); } catch { /* storage unavailable */ }
  }
}

function readUser(): string {
  try { return localStorage.getItem(USER_KEY) ?? 'ravi'; } catch { return 'ravi'; }
}

/** Adds X-User-Id to every /api call so no component has to remember it. */
export const userHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  const userId = inject(UserStore).userId();
  if (req.url.startsWith('/api/') && userId) {
    req = req.clone({ setHeaders: { 'X-User-Id': userId } });
  }
  return next(req);
};

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/documents';

  /** category undefined = all sections; name = optional filename search */
  list(page: number, size: number, category?: DocumentCategory, name?: string): Observable<Page<DocumentItem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (category) {
      params = params.set('category', category);
    }
    if (name) {
      return this.http.get<Page<DocumentItem>>(`${this.base}/search`, { params: params.set('name', name) });
    }
    return this.http.get<Page<DocumentItem>>(this.base, { params });
  }

  summary(): Observable<Summary> {
    return this.http.get<Summary>(`${this.base}/summary`);
  }

  upload(file: File, category: DocumentCategory): Observable<HttpEvent<DocumentItem>> {
    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    return this.http.post<DocumentItem>(this.base, form, { reportProgress: true, observe: 'events' });
  }

  viewUrl(id: string): Observable<PresignedUrl> {
    return this.http.get<PresignedUrl>(`${this.base}/${id}/view`);
  }

  downloadUrl(id: string): Observable<PresignedUrl> {
    return this.http.get<PresignedUrl>(`${this.base}/${id}/download`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  restore(id: string): Observable<DocumentItem> {
    return this.http.post<DocumentItem>(`${this.base}/${id}/restore`, null);
  }
}

/** Shared display helpers */
export function formatSize(bytes: number): string {
  if (bytes < 1024) { return `${bytes} B`; }
  if (bytes < 1024 * 1024) { return `${(bytes / 1024).toFixed(1)} KB`; }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const TYPE_LABELS: Record<string, string> = {
  'application/pdf': 'PDF',
  'image/png': 'PNG',
  'image/jpeg': 'JPEG',
  'text/plain': 'TXT',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'DOCX',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'XLSX',
};

export function typeLabel(contentType: string): string {
  return TYPE_LABELS[contentType] ?? contentType;
}

export function errorMessage(e: { status?: number; error?: { message?: string }; message?: string }): string {
  return e.error?.message
    ?? (e.status === 0 ? 'Cannot reach the server. Is the Spring Boot app running on port 8080?' : e.message ?? 'Request failed');
}

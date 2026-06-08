import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';

export interface AgentNotificationItem {
  id: number;
  typeCode: string;
  bordereauId: number;
  numeroBordereau: string;
  message: string;
  createdAt: string;
}

export interface AgentNotificationsResponse {
  unreadCount: number;
  items: AgentNotificationItem[];
}

@Injectable({ providedIn: 'root' })
export class AgentNotificationsService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/api/notifications`;

  readonly data = signal<AgentNotificationsResponse>({ unreadCount: 0, items: [] });
  readonly loading = signal(false);
  readonly clearing = signal(false);

  readonly unreadCount = computed(() => Math.max(0, this.data().unreadCount));

  readonly badgeLabel = computed(() => {
    const n = this.unreadCount();
    if (n <= 0) return '';
    return n > 99 ? '99+' : String(n);
  });

  refresh(onDone?: () => void): void {
    this.loading.set(true);
    this.http.get<AgentNotificationsResponse>(this.api).subscribe({
      next: (res) => {
        this.data.set(normalize(res));
        this.loading.set(false);
        onDone?.();
      },
      error: () => {
        this.data.set({ unreadCount: 0, items: [] });
        this.loading.set(false);
        onDone?.();
      },
    });
  }

  dismissOne(id: number, onDone?: () => void): void {
    this.clearing.set(true);
    this.http.delete<void>(`${this.api}/${id}`).subscribe({
      next: () => {
        const current = this.data();
        const items = current.items.filter((n) => n.id !== id);
        this.data.set({ unreadCount: items.length, items });
        this.clearing.set(false);
        onDone?.();
      },
      error: () => {
        this.clearing.set(false);
        this.refresh(onDone);
      },
    });
  }

  dismissAll(onDone?: () => void): void {
    this.clearing.set(true);
    this.http.delete<void>(this.api).subscribe({
      next: () => {
        this.data.set({ unreadCount: 0, items: [] });
        this.clearing.set(false);
        onDone?.();
      },
      error: () => {
        this.clearing.set(false);
        this.refresh(onDone);
      },
    });
  }

  reset(): void {
    this.data.set({ unreadCount: 0, items: [] });
  }
}

function normalize(res: AgentNotificationsResponse | null | undefined): AgentNotificationsResponse {
  if (!res) return { unreadCount: 0, items: [] };
  const items = Array.isArray(res.items) ? res.items : [];
  const unreadCount = Math.max(0, Number(res.unreadCount) || items.length);
  return { unreadCount, items };
}

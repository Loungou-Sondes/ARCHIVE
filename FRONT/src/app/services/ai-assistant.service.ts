import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AssistantChatResponse {
  reply: string;
  intent: string;
  suggestions: string[];
}

@Injectable({ providedIn: 'root' })
export class AiAssistantService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/api/assistant/chat`;

  send(message: string): Observable<AssistantChatResponse> {
    return this.http.post<AssistantChatResponse>(this.api, { message }).pipe(timeout(120_000));
  }
}

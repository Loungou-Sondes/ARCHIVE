import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { AiAssistantService } from '../../services/ai-assistant.service';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, RippleModule, TranslocoPipe],
  templateUrl: './ai-assistant.component.html',
  styleUrl: './ai-assistant.component.scss',
})
export class AiAssistantComponent implements OnInit {
  private readonly assistant = inject(AiAssistantService);
  private readonly i18n = inject(AppTranslateService);

  readonly loading = signal(false);
  readonly messages = signal<ChatMessage[]>([]);
  readonly suggestions = signal<string[]>([]);

  inputText = '';

  @ViewChild('messagesEnd') private messagesEnd?: ElementRef<HTMLDivElement>;
  @ViewChild('inputField') private inputField?: ElementRef<HTMLInputElement>;

  ngOnInit(): void {
    this.pushAssistant(this.i18n.t('aiAssistant.welcome'), [
      this.i18n.t('aiAssistant.suggestionHelpConservation'),
      this.i18n.t('aiAssistant.suggestionHelp'),
      this.i18n.t('aiAssistant.suggestionStats'),
    ]);
    setTimeout(() => this.inputField?.nativeElement.focus(), 150);
  }

  sendSuggestion(text: string): void {
    this.inputText = text;
    this.send();
  }

  send(): void {
    const text = this.inputText.trim();
    if (!text || this.loading()) {
      return;
    }

    this.messages.update((list) => [...list, { role: 'user', text }]);
    this.inputText = '';
    this.loading.set(true);
    this.suggestions.set([]);
    this.scrollToBottom();

    this.assistant.send(text).subscribe({
      next: (res) => {
        this.pushAssistant(res.reply, res.suggestions ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.pushAssistant(this.i18n.t('aiAssistant.error'), [
          this.i18n.t('aiAssistant.suggestionHelp'),
          this.i18n.t('aiAssistant.suggestionStats'),
        ]);
        this.loading.set(false);
      },
    });
  }

  private pushAssistant(text: string, suggestions: string[]): void {
    this.messages.update((list) => [...list, { role: 'assistant', text }]);
    this.suggestions.set(suggestions);
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.messagesEnd?.nativeElement;
      el?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }, 50);
  }
}

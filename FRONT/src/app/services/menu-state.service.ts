import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class MenuStateService {
  readonly collapsed = signal(false);

  toggle(): void {
    this.collapsed.update((c) => !c);
  }
}

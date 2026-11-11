import { Component, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { SECTIONS, sectionFor } from '../categories';
import { DocumentItem, DocumentService, Summary, UserStore, errorMessage, formatSize, typeLabel } from '../document.service';

/** Home page: document count per section + most recent uploads across all sections. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
  private readonly api = inject(DocumentService);
  private readonly user = inject(UserStore);

  readonly sections = SECTIONS;
  readonly summary = signal<Summary | null>(null);
  readonly recent = signal<DocumentItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly formatSize = formatSize;
  readonly typeLabel = typeLabel;
  readonly sectionFor = sectionFor;

  constructor() {
    // Reload whenever the signed-in user changes (and once on start)
    effect(() => {
      this.user.userId();
      untracked(() => this.load());
    });
  }

  count(category: string): number {
    return this.summary()?.byCategory[category as keyof Summary['byCategory']] ?? 0;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ summary: this.api.summary(), recent: this.api.list(0, 5) }).subscribe({
      next: ({ summary, recent }) => {
        this.summary.set(summary);
        this.recent.set(recent.content);
        this.loading.set(false);
      },
      error: e => {
        this.loading.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}

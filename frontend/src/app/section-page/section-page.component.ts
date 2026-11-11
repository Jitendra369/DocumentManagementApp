import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Section } from '../categories';
import { DocumentItem, DocumentService, UserStore, errorMessage, formatSize, typeLabel } from '../document.service';

const PAGE_SIZE = 10;

/**
 * The page for one section (Personal, Invoices, Documents, Medicine Bills).
 * The router passes the section in via route data (see app.routes.ts).
 */
@Component({
  selector: 'app-section-page',
  imports: [FormsModule, DatePipe],
  templateUrl: './section-page.component.html',
  styleUrl: './section-page.component.css'
})
export class SectionPageComponent {
  private readonly api = inject(DocumentService);
  private readonly user = inject(UserStore);

  readonly section = input.required<Section>();

  readonly docs = signal<DocumentItem[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly uploadProgress = signal<number | null>(null);
  readonly error = signal<string | null>(null);
  readonly lastDeleted = signal<DocumentItem | null>(null);

  readonly formatSize = formatSize;
  readonly typeLabel = typeLabel;

  search = '';
  private activeSearch = '';

  constructor() {
    // Load on start, and again if the section or the signed-in user changes
    effect(() => {
      this.section();
      this.user.userId();
      untracked(() => {
        this.lastDeleted.set(null);
        this.load(0);
      });
    });
  }

  runSearch(): void {
    this.activeSearch = this.search.trim();
    this.load(0);
  }

  clearSearch(): void {
    this.search = '';
    this.runSearch();
  }

  load(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(page, PAGE_SIZE, this.section().category, this.activeSearch || undefined).subscribe({
      next: res => {
        this.docs.set(res.content);
        this.page.set(res.page);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: e => this.fail(e),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';               // allow re-selecting the same file
    if (!file) { return; }

    this.error.set(null);
    this.uploadProgress.set(0);
    this.api.upload(file, this.section().category).subscribe({
      next: ev => {
        if (ev.type === HttpEventType.UploadProgress && ev.total) {
          this.uploadProgress.set(Math.round((100 * ev.loaded) / ev.total));
        } else if (ev.type === HttpEventType.Response) {
          this.uploadProgress.set(null);
          this.load(0);
        }
      },
      error: e => { this.uploadProgress.set(null); this.fail(e); },
    });
  }

  view(doc: DocumentItem): void {
    // Open the tab synchronously so pop-up blockers allow it, then point it at the signed URL
    const tab = window.open('', '_blank');
    this.api.viewUrl(doc.id).subscribe({
      next: res => { if (tab) { tab.location.href = res.url; } else { window.location.href = res.url; } },
      error: e => { tab?.close(); this.fail(e); },
    });
  }

  download(doc: DocumentItem): void {
    this.api.downloadUrl(doc.id).subscribe({
      next: res => { window.location.href = res.url; },
      error: e => this.fail(e),
    });
  }

  remove(doc: DocumentItem): void {
    if (!confirm(`Delete "${doc.filename}"?`)) { return; }
    this.api.delete(doc.id).subscribe({
      next: () => {
        this.lastDeleted.set(doc);
        const onlyItemOnPage = this.docs().length === 1 && this.page() > 0;
        this.load(onlyItemOnPage ? this.page() - 1 : this.page());
      },
      error: e => this.fail(e),
    });
  }

  undoDelete(): void {
    const doc = this.lastDeleted();
    if (!doc) { return; }
    this.api.restore(doc.id).subscribe({
      next: () => { this.lastDeleted.set(null); this.load(this.page()); },
      error: e => this.fail(e),
    });
  }

  private fail(e: HttpErrorResponse): void {
    this.loading.set(false);
    this.error.set(errorMessage(e));
  }
}

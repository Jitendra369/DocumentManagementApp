import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard/dashboard.component';
import { SectionPageComponent } from './section-page/section-page.component';
import { SECTIONS } from './categories';

export const routes: Routes = [
  { path: '', component: DashboardComponent, title: 'Dashboard · Documents' },

  // One page per section: /personal, /invoices, /documents, /medicine-bills
  ...SECTIONS.map(section => ({
    path: section.path,
    component: SectionPageComponent,
    title: `${section.title} · Documents`,
    data: { section },          // bound to SectionPageComponent's "section" input
  })),

  { path: '**', redirectTo: '' },
];

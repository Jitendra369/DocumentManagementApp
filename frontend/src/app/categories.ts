/** The sections of the app. Each one gets its own route/page and a value stored with every document. */
export type DocumentCategory = 'PERSONAL' | 'INVOICE' | 'DOCUMENT' | 'MEDICINE_BILL';

export interface Section {
  category: DocumentCategory;
  path: string;
  title: string;
  description: string;
  icon: string;   // SVG path data (24x24 viewBox, stroke icon)
}

export const SECTIONS: Section[] = [
  {
    category: 'PERSONAL',
    path: 'personal',
    title: 'Personal',
    description: 'ID cards, certificates and other personal papers',
    icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8a7 7 0 0 1 14 0',
  },
  {
    category: 'INVOICE',
    path: 'invoices',
    title: 'Invoices',
    description: 'Bills, receipts and invoices',
    icon: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6m-6 4h6m-6 4h3',
  },
  {
    category: 'DOCUMENT',
    path: 'documents',
    title: 'Documents',
    description: 'General documents and files',
    icon: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Zm0 0v5h5',
  },
  {
    category: 'MEDICINE_BILL',
    path: 'medicine-bills',
    title: 'Medicine Bills',
    description: 'Pharmacy bills and medical receipts',
    icon: 'M10 4h4v6h6v4h-6v6h-4v-6H4v-4h6V4Z',
  },
];

export function sectionFor(category: string): Section | undefined {
  return SECTIONS.find(s => s.category === category);
}

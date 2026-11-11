-- Section a document belongs to: PERSONAL, INVOICE, DOCUMENT, MEDICINE_BILL.
-- Existing rows become DOCUMENT.
ALTER TABLE documents
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'DOCUMENT';

-- Section pages list "this owner's active documents in this category, newest first"
CREATE INDEX idx_documents_owner_category_status_created
    ON documents (owner_id, category, status, created_at DESC);

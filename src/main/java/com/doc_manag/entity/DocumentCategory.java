package com.doc_manag.entity;

/** Which section of the app a document belongs to. */
public enum DocumentCategory {
    PERSONAL,       // ID cards, certificates, personal papers
    INVOICE,        // bills and invoices
    DOCUMENT,       // general documents (default)
    MEDICINE_BILL   // pharmacy and medical bills
}

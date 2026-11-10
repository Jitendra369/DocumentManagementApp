# S3 Standards Checklist

A practical checklist for using Amazon S3 in an application (for example a Spring Boot document management system).
Tick each item as you build. Items marked **(must)** are baseline standards; the rest are strongly recommended.

---

## 1. Bucket setup

- [ ] **(must)** Block Public Access enabled at bucket level (and at account level if possible)
- [ ] **(must)** Object Ownership set to **Bucket owner enforced** (ACLs disabled)
- [ ] Bucket name follows a convention, e.g. `<company>-<app>-<env>-<region>` → `acme-dms-prod-ap-south-1`
- [ ] Separate buckets (or separate AWS accounts) for `dev`, `staging`, `prod`
- [ ] Bucket created in the region closest to your users / compute (e.g. `ap-south-1` for India)
- [ ] Data-residency or compliance requirements checked before picking a region
- [ ] Tags on the bucket: `app`, `env`, `owner`, `cost-center`

## 2. Security & access control

- [ ] **(must)** No AWS access keys hard-coded in code, `application.yml`, or Git
- [ ] **(must)** App uses an **IAM role** in AWS (EC2 instance profile, ECS task role, EKS IRSA); access keys only for local development
- [ ] **(must)** Least-privilege IAM policy: only the actions the app actually uses, only on its bucket/prefix
  ```json
  {
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::acme-dms-prod-ap-south-1/documents/*"
  },
  {
    "Effect": "Allow",
    "Action": ["s3:ListBucket"],
    "Resource": "arn:aws:s3:::acme-dms-prod-ap-south-1"
  }
  ```
- [ ] **(must)** Bucket policy denies non-HTTPS requests (`aws:SecureTransport = false` → Deny)
- [ ] Bucket policy restricts access to known roles / VPC endpoint where possible
- [ ] Use a **VPC Gateway Endpoint** for S3 so traffic from your servers stays inside AWS (also saves NAT cost)
- [ ] Separate IAM permissions for humans (console) and the application
- [ ] MFA required for humans with delete permissions on production buckets

## 3. Encryption

- [ ] **(must)** Default encryption enabled on the bucket (SSE-S3 at minimum)
- [ ] Use **SSE-KMS** for sensitive data (personal documents, financial, medical) so access is also controlled and audited through KMS
- [ ] If using SSE-KMS, enable **S3 Bucket Keys** to reduce KMS cost
- [ ] **(must)** All traffic over HTTPS (TLS)
- [ ] Bucket policy denies uploads without the expected encryption header (optional, for strict compliance)

## 4. Object key design

- [ ] **(must)** Never use the user's original filename as the key; generate it
      e.g. `documents/{yyyy}/{MM}/{uuid}.{ext}`
- [ ] Original filename stored in your database, not trusted for the path
- [ ] Keys contain only safe characters: `a-z 0-9 / - _ .`
- [ ] Prefixes organised by purpose: `documents/`, `thumbnails/`, `temp/`, `exports/`
- [ ] No personal data in keys (keys appear in logs and URLs)
- [ ] Tenant/owner separation in the prefix if multi-tenant, e.g. `tenants/{tenantId}/documents/...`

## 5. Metadata & database

- [ ] **(must)** Document metadata stored in your database (id, s3_key, original_filename, content_type, size, owner, status, timestamps)
- [ ] API exposes your **document ID**, never the raw S3 key or bucket name
- [ ] `Content-Type` set correctly on every upload
- [ ] Checksum stored (ETag or SHA-256) to detect duplicates and corruption
- [ ] Status field (`PENDING` → `ACTIVE` → `DELETED`) to handle partial failures
- [ ] Listing, searching and pagination done from the **database**, not `ListObjects`

## 6. Upload

- [ ] **(must)** Validate file size (server-side, not only in the UI)
- [ ] **(must)** Validate file type by content (magic bytes, e.g. Apache Tika), not only by extension
- [ ] Allow-list of permitted types (e.g. pdf, docx, xlsx, png, jpg)
- [ ] Large files (> 100 MB) use **multipart upload** (`S3TransferManager`)
- [ ] Large/direct client uploads use **pre-signed PUT URLs** so bytes don't pass through your server
- [ ] For pre-signed uploads, confirm completion (client callback or S3 event) before marking `ACTIVE`
- [ ] Lifecycle rule to **abort incomplete multipart uploads** after 1–7 days
- [ ] Malware scanning for user uploads (e.g. GuardDuty Malware Protection for S3, or ClamAV via Lambda)
- [ ] Upload to a `quarantine/` or `temp/` prefix first, move to `documents/` after validation/scan

## 7. Download, view & pre-signed URLs

- [ ] **(must)** Permission check in your app **before** generating a pre-signed URL
- [ ] **(must)** Short expiry: 5–15 minutes for view/download, shorter for uploads
- [ ] Override response headers when presigning:
  - View: `Content-Disposition: inline` + stored `Content-Type`
  - Download: `Content-Disposition: attachment; filename="<original name>"`
- [ ] Pre-signed URLs are never logged in full or stored long-term
- [ ] Remember: a URL signed with temporary role credentials expires when those credentials expire, even if the requested expiry is longer
- [ ] For high traffic / global users, serve through **CloudFront** with signed URLs or signed cookies

## 8. Delete & retention

- [ ] Soft delete in DB first (`status = DELETED`, `deleted_at`), physical delete later by a scheduled job
- [ ] **Versioning** enabled on production buckets (protects against accidental overwrite/delete)
- [ ] Lifecycle rule to expire **noncurrent versions** after N days (otherwise versioning cost grows forever)
- [ ] Lifecycle rule to expire **delete markers** that have no versions left
- [ ] Lifecycle rule to clean up `temp/` objects (e.g. after 1 day)
- [ ] Retention policy defined (how long documents must be kept, legally)
- [ ] **Object Lock** (compliance/governance mode) if regulations require files that can't be changed
- [ ] MFA Delete considered for critical buckets

## 9. Consistency between S3 and DB

- [ ] Upload order: S3 first → DB insert (or DB `PENDING` → S3 → DB `ACTIVE`)
- [ ] Delete order: DB soft delete first → S3 delete asynchronously
- [ ] Scheduled **reconciliation job**:
  - DB rows stuck in `PENDING` too long → delete row/object
  - S3 objects with no DB row (orphans) → delete
- [ ] Retries with backoff for S3 calls (the AWS SDK does this by default; don't disable it)
- [ ] Idempotent operations (retrying an upload or delete doesn't create duplicates or errors)

## 10. Storage classes & cost

- [ ] Default class `STANDARD` for active documents
- [ ] **S3 Intelligent-Tiering** for documents with unpredictable access patterns
- [ ] Lifecycle transitions for old data, e.g. `STANDARD` → `STANDARD_IA` (30 days) → `GLACIER_IR` / `DEEP_ARCHIVE` (90–180 days)
- [ ] Watch minimum storage duration & minimum object size charges for IA/Glacier classes
- [ ] **AWS Budgets / billing alert** configured
- [ ] Cost allocation tags enabled
- [ ] Avoid frequent `ListObjects` calls in hot paths (use the DB)
- [ ] S3 Storage Lens enabled to watch growth and incomplete uploads

## 11. Logging, monitoring & audit

- [ ] **CloudTrail** enabled (management events; data events for sensitive buckets)
- [ ] S3 server access logs or CloudTrail data events sent to a **separate** log bucket
- [ ] Application-level audit log: who uploaded/viewed/downloaded/deleted which document and when
- [ ] CloudWatch metrics/alarms: 4xx/5xx error rates, bucket size, request count
- [ ] **AWS Config** rules / Security Hub checks for public buckets and missing encryption
- [ ] IAM Access Analyzer reviewing bucket policies for unintended access

## 12. Backup & disaster recovery

- [ ] Versioning on (required for replication)
- [ ] **Cross-Region Replication** (or same-region to another account) for critical documents
- [ ] Database backups aligned with S3 (metadata without files, or files without metadata, are both useless)
- [ ] Restore procedure documented **and tested**
- [ ] AWS Backup for S3 considered for central backup policies

## 13. Performance

- [ ] Reuse a single `S3Client` / `S3Presigner` bean (thread-safe, expensive to create)
- [ ] Close/stream `ResponseInputStream` properly; never load large files fully into memory
- [ ] Multipart / byte-range downloads for large files
- [ ] Spread heavy write traffic across prefixes (each prefix supports ~3,500 PUT and ~5,500 GET req/s)
- [ ] Transfer Acceleration only if users upload from far away (costs extra)

## 14. Spring Boot / code standards

- [ ] AWS SDK **v2** used (v1 is in maintenance mode)
- [ ] Bucket name, region, expiry durations in configuration (`application.yml` + per-environment profiles), not in code
- [ ] S3 code isolated in a `StorageService` interface (easy to mock / swap for MinIO)
- [ ] `S3Exception` mapped to meaningful API errors (403 → access denied, 404 → not found)
- [ ] `spring.servlet.multipart.max-file-size` set to match your upload limit
- [ ] Integration tests against **LocalStack** or **MinIO** (Testcontainers)
- [ ] Credentials via `DefaultCredentialsProvider` (works locally and in AWS without code changes)

## 15. Before going to production

- [ ] Public access test: try opening an object URL without signing → must get **403**
- [ ] Expired pre-signed URL test → must get **403**
- [ ] Unauthorised user cannot get a pre-signed URL for someone else's document
- [ ] Upload rejected for wrong type / too large
- [ ] Delete → restore → permanent delete flows tested
- [ ] Billing alert, CloudTrail, encryption, versioning and lifecycle rules confirmed in the console
- [ ] Runbook written: how to restore a deleted file, rotate keys, respond to a leaked URL

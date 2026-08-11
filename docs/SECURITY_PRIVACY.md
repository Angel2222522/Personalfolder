# Security and privacy notes

## Guarantees implemented in the app

- No account, backend, analytics, trackers, advertisements or `INTERNET` permission.
- Document files are AES-GCM encrypted with an Android Keystore key and use atomic `.part` writes.
- A missing Keystore key fails document decryption; it is not silently regenerated.
- Private document paths are canonicalized below the app's documents root.
- Backup encryption derives an AES key from the password with PBKDF2-HMAC-SHA256 and an authenticated AES-GCM envelope. Password character arrays are cleared after use.
- Backup restore rejects traversal names, duplicate entries/IDs, invalid references, oversized archives, missing files and malformed relationships before commit.
- `FLAG_SECURE` protects the app window from screenshots and recent-task previews. Biometric/device credential capability is checked before enabling the lock, and authentication errors are never success.
- Notifications use private visibility and generic text while app lock is enabled.
- Temporary decrypted files have short cache lifetimes, deterministic cleanup and startup recovery.

## Explicit boundaries

Room OCR/metadata columns and the FTS index are app-private but not separately encrypted. Android sandboxing and device encryption protect normal third-party access; a privileged filesystem/forensic reader can still inspect them. Full database encryption would require an additional audited key lifecycle and migration plan and is not claimed by V2.

Ordinary ZIP/PDF export is plaintext by design and is clearly labeled in the UI. The password-protected portable backup is the appropriate transfer mechanism. Authentication settings and credentials are deliberately excluded from backup.

## Input and resource policy

Imports are limited to supported PDF/image MIME types, 100 sources, a 512 MiB aggregate plaintext budget and 1,000 logical pages. OCR image dimensions, text output, PDF render dimensions, archive entry count and archive bytes are bounded. PDF export is streamed one rendered page at a time with output limits.

These are availability protections, not a promise that every malformed OEM decoder or PDF implementation behaves safely. The app reports failures locally and does not upload document content.

# Security

Doc Scanner is designed to keep document content on the local device.

## Reporting a vulnerability

Do not publish sensitive vulnerability details in a public issue.

Use GitHub Security Advisories for the project repository once enabled. If private reporting is not available, contact the maintainers through the repository's published security contact.

## Security principles

- No application-level account system
- No required cloud service
- No analytics or advertising SDK
- No document upload service
- Android Keystore for local vault key material
- Scoped storage and Android system file pickers
- Minimal runtime permissions

Security-sensitive changes should include regression tests and an explanation of how the change affects data exposure, persistence and export paths.

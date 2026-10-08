# Security Policy

## Scope

Simple Keyboard requests no `INTERNET` permission in its Android manifest and makes no network connections. Consequently, vulnerabilities involving remote network exploitation, data exfiltration to remote servers, or man-in-the-middle attacks are out of scope.

Security assessments and reports focus on local-only attack surfaces:
- Clipboard and clipboard history storage.
- Backup and restore JSON parsing and export files.
- Exported components, intents, or preferences accessible to other local applications.

For details on local data handling and permissions, see [PRIVACY.md](PRIVACY.md).

## Supported Versions

Only the latest release is supported with security patches:

| Version | Supported |
| :--- | :--- |
| Latest release | Yes |
| Older releases | No |

## Reporting a Vulnerability

Please do not disclose security vulnerabilities through public GitHub issues.

Report vulnerabilities privately using GitHub Security Advisories:
- Navigate to the repository **Security** tab.
- Click **Report a vulnerability** to open a private advisory.

Provide steps to reproduce the issue locally, an assessment of impact, and relevant device or Android OS details.

## Bounty

This project is maintained on a volunteer basis and does not operate a bug bounty program.

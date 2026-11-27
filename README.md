# MailShelf

Local-first Android inbox reader for Gmail. Connect accounts independently of Android's system accounts, with no app-imposed account cap.

## Features (0.1)
- Gmail IMAP over TLS, using an eligible Google app password (not your normal password).
- Android Keystore AES-GCM encrypted account credentials; Android backup disabled.
- Read-only inboxes, unified recent-message list, account filter and local subject/sender search.
- Select an account after refresh and tap **Load older** to fetch 20 more inbox headers at a time, up to 200 loaded per account per session. Refresh resets the loaded history.
- Message text fetched on demand; no persistent mail cache, no attachment downloads.
- Bounded network concurrency, per-account failures, and manual refresh.
- No analytics or backend.

## Setup

Enable 2-Step Verification on your Google account and create an app password at https://myaccount.google.com/apppasswords if eligible. Enter the email and app password only in the installed app. Never commit credentials or send them in chat. Accounts with Advanced Protection, organization restrictions or other eligibility limitations may not support app passwords. Removing the account from Android settings should not affect the app password, but revocation or password changes may.

## Build and install

Use JDK 17, Android SDK 35, and Gradle 8.9.

```sh
gradle :app:assembleDebug
```

[GitHub Actions](https://github.com/aksharghatole/MailShelf/actions) builds and uploads the debug APK. Debug APKs are for testing, not production distribution.

## Limitations

Initial implementation, not yet tested with real Gmail accounts. Initially 20 messages per account, with manual older pages up to 200 loaded per account; search only covers loaded headers. No full-history search, OAuth, notifications, persistent cache, or biometric lock yet. Account count has no hard-coded maximum; provider quotas and device resources apply. 500 accounts / 1M accessible emails are future stress-test targets, not demonstrated capacity. No HTML rendering, no link execution, and no attachments in the first build.

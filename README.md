# Coupon Pilot MVP

A privacy-first Android coupon wallet that:

- Captures likely coupon notifications from Google Pay, Paytm, CRED, Amazon and Flipkart after explicit notification-access approval.
- Parses common percentage/flat-discount, minimum-spend, maximum-discount, code and payment-method patterns.
- Stores coupons locally with Room.
- Ranks coupons by estimated saving for a merchant, payment amount and payment method.
- Copies the selected coupon code to the clipboard.
- Supports manual coupon entry and deletion.

## Open in Android Studio

1. Install the latest stable Android Studio.
2. Open this folder as a project.
3. Let Gradle sync and install Android SDK 35 if prompted.
4. Run on an Android 8.0+ device or emulator.
5. In the app, tap **Enable notification access** and explicitly enable Coupon Pilot.

## Important limitations

- Payment apps do not expose a universal API for reading all in-app rewards. This MVP only captures coupon information visible in notifications or entered manually.
- Notification text varies by app; the parser is intentionally conservative and will need more patterns after real-world testing.
- Expiry-date parsing, screenshot OCR, Gmail/SMS import, merchant deep links and encrypted database storage are not included in v0.1.
- The MVP does not use Accessibility Services and does not automatically control payment apps.

## Suggested v0.2

- Share-to-Coupon-Pilot intent for text and screenshots.
- On-device ML Kit OCR for screenshots.
- Better date parsing and duplicate detection.
- App-specific parsers and confidence scores.
- Encrypted local database and optional biometric lock.
- Merchant deep links and “copy code + open merchant app”.

## Test example

Manually add:

- Merchant: Swiggy
- Code: SAVE150
- Type: Percent
- Discount value: 20
- Maximum discount: 150
- Minimum spend: 499
- Payment method: UPI

Search for Swiggy, amount ₹850, payment method UPI. Estimated saving should be ₹150.

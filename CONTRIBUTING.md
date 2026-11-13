# Contributing to ScrollMeter

Thanks for helping make ScrollMeter more reliable and transparent.

## Areas where contributions are welcome
- Unit tests for swipe detection and feed-context classification
- Clearer diagnostics for uncertain events
- Compatibility notes for Android and OEM variations
- Accessibility and dashboard improvements
- Documentation and privacy explanations

## Development
1. Check existing issues before starting.
2. For a substantial change, open an issue to discuss the approach.
3. Fork the repository and create a focused branch.
4. Run the relevant Gradle unit and instrumentation tests.
5. Include test results and device/Android-version details in your pull request.

## Privacy requirements
- Do not collect, store, or transmit screen contents, messages, account details, or video content.
- Keep event processing limited to the documented swipe-counting purpose.
- Do not add analytics or network transmission without explicit discussion and clear user consent.
- Never commit signing keys, credentials, or private user data.

## Pull request checklist
- [ ] Detection changes include tests for confirmed and uncertain events.
- [ ] Privacy behavior is unchanged or clearly documented.
- [ ] Limitations and supported Android versions are explained.
- [ ] No secrets or personal data are included.

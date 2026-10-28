# Pre-match touch targets — October 28, 2026

The pre-match Droidball panel used small text-only targets, leaving Open/Start
and Stop easy to confuse. Nonbattle actions now have full-width targets at least
48 dp tall, 11 sp labels, and an additional 16 dp clear gap before Stop Session.
Yes/No confirmation targets remain side by side with space between them. The
nonbattle panel scrolls within the display height so the extra spacing does not
strand its lower controls. Live battle sprite cards and badge geometry are unchanged.

The full working-tree unit suite passed (1,356 tests, 218 suites, zero failures,
errors or skips), and debug/app-test APKs built. No padding-specific unit tests
were added. On-phone target spacing and scrolling still need the user's next
pre-flight check; compilation is not a touch-layout acceptance test.

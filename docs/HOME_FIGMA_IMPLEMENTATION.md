# Home: Figma adaptation

Reference: https://www.figma.com/design/Ks5fX8kvgJ6wzwM0uOvsUj/WeGlow?node-id=1578-444

## Reading order

1. Date, greeting, alerts information and profile access.
2. Primary AI face-analysis action.
3. Daily routine: Morning/Evening, real completion count, next step and Continue Routine.
4. Skin environment: location/weather status, UV, humidity, air quality and SPF logging.
5. Compact hairstyle, routine and progress shortcuts.
6. Existing personalized product recommendations.
7. WeGlow Insights: care notes plus previews of future alerts/recovery features.
8. Skin-history and score placeholders.
9. Curated skincare/hair editorials and the retained layering guide.

The daily routine is promoted above the environment card because it is a working,
repeat-use feature. The screen keeps one visually dominant scan action instead of
three stacked competing hero buttons. Supporting cards grow with text size and the
shortcut row becomes a column at larger font sizes.

## Live vs pending

Routine products, AM/PM completion, account name/photo and device date are real.
Continue Routine carries the selected period into Routines. Profile and product
recommendation actions use existing routes. Hairstyle opens the scan mode chooser.
Progress scrolls to the skin-history section; Alerts explains its pending state.

Weather/location, SPF logging/reminders, skin synchronization, biometric scores,
barrier trends and editorial content are not integrated. These stay visible as
Not connected or Coming soon. No sample location, UV/AQI values, score improvements,
water prescriptions, read times or dermatologist endorsements are presented as real.

## Artwork

The following PNGs are exact Figma exports bundled locally, not expiring remote URLs:

- home_scan.png: biometric scan hero background.
- home_environment.png: environment background.
- home_hydration.png: hydration/insight background.
- home_botanicals.png: Layering Botanicals Guide.
- home_scalp.png: Scalp Barrier for Waves.

Routine images continue using actual catalog data and the shared product image component.
Existing typography, contrast tokens, navigation, avatar and pending-feature components
are reused rather than copying Figma's small fixed-height web typography into Android.

## Verification

Debug app and test APK builds passed. The 21 focused contrast/routine unit tests and
12 device tests passed, including AM/PM key isolation, selected-period callback,
all new sections at 130% font scale, and local journal persistence. Device fixture
screenshots were visually inspected; the installed app is updated without clearing data.

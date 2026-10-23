# Svitlo

<p>
  <a href="https://github.com/hatogg/Svitlo/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/hatogg/Svitlo?style=flat-square&label=release&color=E0A526"></a>
  <a href="https://github.com/hatogg/Svitlo/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/hatogg/Svitlo/build.yml?branch=main&style=flat-square&label=build"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <a href="LICENSE"><img alt="MIT" src="https://img.shields.io/badge/license-MIT-1F2130?style=flat-square"></a>
</p>

<p><a href="README.md">Українська</a> &nbsp;|&nbsp; <b>English</b></p>

**Svitlo** ("light" in Ukrainian) shows the power outage schedule for your group in Ukraine: whether the power is on now, when it goes off and when it comes back. It reminds you 15 minutes before an outage, has a home-screen widget and several addresses — home, work, parents. No ads.

<p>
  <a href="https://github.com/hatogg/Svitlo/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-E0A526?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

<p align="center">
  <img src="docs/screens/en-light-home.png" width="24%" alt="Power is off, back at 14:30">
  <img src="docs/screens/en-light-home-on.png" width="24%" alt="Power is on, goes off at 18:00">
  <img src="docs/screens/en-light-editor.png" width="24%" alt="Picking a region and group">
  <img src="docs/screens/en-light-manual.png" width="24%" alt="Own schedule for other regions">
</p>

<sub>Real screenshots: GitHub Actions runs the app in an Android 14 emulator with a test schedule.</sub>

## Features

- **Now:** a big card — "Power is on · goes off at 13:00" or "Power is off · back at 18:00" — with a countdown.
- **Today and tomorrow:** a half-hour timeline and the list of outages. If tomorrow's schedule isn't out yet, possible outages from the weekly plan are shown.
- **Reminders** 5, 15, 30 or 60 minutes before an outage; optionally when power comes back and about possible outages.
- **Schedule changes:** checked every hour, with a notification when the schedule changes or tomorrow's is published.
- **Several addresses**, a **widget**, and **offline** use with the last saved schedule.

## Regions

Schedules load automatically for **Kyiv, Kyiv region, Dnipro and region, Odesa and region** from the open [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) project, which collects them from DTEK pages every few minutes. For **other regions** you tap your group's hours once on a weekly grid, and reminders and the widget work the same way.

## Permissions

`INTERNET` to download schedules, `POST_NOTIFICATIONS` for reminders, `SCHEDULE_EXACT_ALARM` for on-the-minute reminders (if you allow it), `RECEIVE_BOOT_COMPLETED` to restore reminders after a reboot. No location, contacts or ads.

## License

[MIT](LICENSE). Schedule data from [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) (MIT), collected from DTEK pages.

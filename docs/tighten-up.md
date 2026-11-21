# Tighten-up list

Shortcuts taken to get v1 on the TV. None of these block using it.

- [ ] **Never run on a real TV before the first PR.** Built blind in CI; first boot is the real test.
- [ ] **Focus styling is hand-rolled** on plain Compose instead of the Compose-for-TV library. Works with the D-pad; swap to `androidx.tv` for proper focus restoration.
- [ ] **Back returns to the top of a list**, not the row you left. Scroll position is kept; focus is not.
- [ ] **No global taper index.** archive.org has no cheap "list all tapers" call, so Tapers is search + follow. A nightly-built index (Cloudflare Worker) would allow real A–Z browsing.
- [ ] **Taper names are whatever the uploader typed.** "J. Smith" and "John Smith" are two tapers. Needs an alias map.
- [ ] **MP3 only.** Lossless (FLAC) toggle not built yet.
- [ ] **Gapless depends on the MP3 derivatives.** Media3 plays the queue back to back; true sample-accurate gapless needs FLAC.
- [ ] **On This Day is one big OR query** across every year. Untested against the live API at scale.
- [ ] **Year list is first-year-to-last-year**, so a band with a hiatus shows empty years.
- [ ] **Search field + D-pad** can be clumsy getting out of the on-screen keyboard.
- [ ] **Signing key is checked into the repo** (sideload-only debug key). Replace before any store release.
- [ ] **Icon/banner are the full retro lockup**, including "Consulting", scaled down. Wants a purpose-drawn Taper TV mark.
- [ ] **APK delivery**: private-repo release links need a GitHub login, so the TV can't pull the APK directly.
- [ ] **No resume**: closing the app forgets the queue position.

— Vega

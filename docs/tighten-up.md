# Tighten-up list

Shortcuts taken to get v1 on the TV. None of these block using it.

- [ ] **Never run on a real TV before the first PR.** Built blind in CI; first boot is the real test.
- [ ] **Focus styling is hand-rolled** on plain Compose instead of the Compose-for-TV library. Works with the D-pad; swap to `androidx.tv` for proper focus restoration.
- [ ] **Back returns to the top of a list**, not the row you left. Scroll position is kept; focus is not.
- [ ] **No global taper index.** archive.org has no cheap "list all tapers" call, so Tapers is search + follow. A nightly-built index (Cloudflare Worker) would allow real A–Z browsing.
- [ ] **Taper names are whatever the uploader typed.** "J. Smith" and "John Smith" are two tapers. Needs an alias map.
- [ ] **Tapers Section artists are grouped by typed name.** "John Scofield" also pulls "John Scofield Band", but "Medeski Scofield Martin & Wood" shows up under four spellings. Needs a name-merge pass.
- [ ] **The Artists home grid is Live Music Archive bands only.** Tapers Section artists only appear when you search for them.
- [ ] **Switching versions stacks screens.** Recording → versions → recording; Back walks through each one instead of swapping in place.
- [ ] **"Highest rated" falls back to most played** when nobody has rated a tape, which is most tapes outside the big bands.
- [ ] **Updater checks only when Home opens**, and asks GitHub directly (60 checks an hour per network, plenty for one TV).
- [ ] **The cassette logo sits on a cream tile.** It is black-bodied, so it disappears on the app's dark background without one. A light-outline variant would let it sit directly on dark.
- [ ] **MP3 only.** Lossless (FLAC) toggle not built yet.
- [ ] **Gapless depends on the MP3 derivatives.** Media3 plays the queue back to back; true sample-accurate gapless needs FLAC.
- [ ] **On This Day is one big OR query** across every year. Untested against the live API at scale.
- [ ] **Year list is first-year-to-last-year**, so a band with a hiatus shows empty years.
- [ ] **Search field + D-pad** can be clumsy getting out of the on-screen keyboard.
- [ ] **Signing key is checked into the repo** (sideload-only debug key). Replace before any store release.
- [ ] **No resume**: closing the app forgets the queue position.

— Vega

# Presentation

Interactive slide deck for the oral defense, built as a [HyperFrames](https://hyperframes.heygen.com)
slideshow: 12 slides with step-by-step reveals, one optional detail slide and speaker notes.

**Online:** <https://luis63e.github.io/huffman-vs-lzw/presentation/>

## Present it locally

The deck is plain static files, so any static web server works. From the repository root:

```bash
python3 -m http.server 8000 --directory presentation
```

Then open <http://localhost:8000/>. Opening `index.html` directly from disk does not work, because
browsers block the embedded slide frame on `file://` pages.

| Key | Action |
|---|---|
| Right arrow / Space | Next reveal or slide |
| Left arrow | Previous slide |
| `P` | Presenter mode: opens the audience tab; this tab shows notes, next slide and a timer |
| `F` | Full screen |
| Backspace | Return from the detail slide |

On the methodology slide, the "¿Cómo se midió el tiempo?" button opens the measurement protocol
slide.

## Contents

- `index.html`: the page you open. It hosts the slideshow player and holds a copy of the deck
  settings.
- `composition/index.html`: the slides. One `<section>` per slide, plus the JSON block at the top of
  `<body>` that sets the slide order, the reveal times, the detail button and the speaker notes.
  When that block changes, copy it into `index.html` too.
- `composition/assets/gsap.min.js`: animation library (GSAP 3.15.0).
- `assets/hyperframes-*.global.js`: the HyperFrames player and slideshow components, from
  `@hyperframes/player` 0.9.41 (Apache-2.0).

Everything is vendored, so the deck needs no network access and no build step.

To validate the slides after editing them:

```bash
npx --yes hyperframes@0.9.41 lint presentation/composition
```

Every number on the slides comes from `results/` and matches the report.

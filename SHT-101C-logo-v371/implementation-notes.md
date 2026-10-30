# SHT-101C handset Samsung oval correction 3.7.1

Baseline: origin/main4204d000a4aa0ad4e6acfbd3862241b1732675c0, preserving preceding local work.

Replaces the generic-font SAMSUNG text and horizontal ellipse on the handset's inside face with the actual oval-era Samsung wordmark outlines. The original SVG is kept unchanged at tools/assets/samsung-oval-original.svg. Its oval and seven letter outlines are converted into a small planar mesh by tools/samsung_logo_mesh.py, including the distinctive open A and geometric M. No system font is substituted. The oval's original tilt and1000:332.026 proportions are preserved at the same handset location. The photograph's monochrome application is represented by existing charcoal and ivory material colors, rather than the SVG's corporate blue fill. This is an SVG-to-game-geometry conversion, not editing the uploaded photograph.

Source: https://commons.wikimedia.org/wiki/File:Samsung_old_logo_before_year_2015.svg
Original file URL: https://upload.wikimedia.org/wikipedia/commons/6/61/Samsung_old_logo_before_year_2015.svg
The mark is used to identify the replicated Samsung product. Asset generation uses Pillow and svgpathtools.

No docking logic, body shape, antenna or button placement changes. Handset5855 faces, below6500-face/2MB bounds. Validation includes geometry/triangulation checks, in-game inside/held captures and existing docking-state assertions, normal build excluding capture harness, archive resource checks, and downloaded publication byte comparison.

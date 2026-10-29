# SHT-101C inner-panel projection 3.6.4

Source baseline: origin/main 4204d000a4aa0ad4e6acfbd3862241b1732675c0. All prior local SHT-family changes retained.

User requested redrawn status-lamp designs using the physical product reference, unlit charge/power indicators, corrected volume text/arrows, and physical projection of only the inner region circled in red. Artwork edits retain the flat colored drawing and existing HA marks. Three status windows use dark colored printed flame/gas-tap/intruder symbols; charge/power windows are dark burgundy, without a lit pink fill. Volume row is ▲ 음 량 ▼, with the separate call label retained. The tiny symbols are reconstructed artwork from the reference, rather than an original manufacturer vector asset.

The .10-pixel backing plate remains. A single continuous inner face projects an additional .40 model pixels, including the upper center tab. Its drafting boundary is (232,254), (670,254), (670,198), (756,198), (756,254), (1287,254), (1287,986), (232,986). All keys, lamps, cradle and screen stay coplanar on that face. Outside frame/backplate stays at its previous depth. UVs preserve the exact drawing coordinates across the raised region. Physical model contains20 quads, with ivory walls and matching facing-aware collision bounds.

Validation: source/model geometry bounds, winding and face counts, integrated-game placement/facing/item/baked model assertions and raw front/oblique/profile captures, normal release build excluding injected capture harness, packaged resource checks, and byte comparison of downloaded published JAR. No functional GUI or new device operation is introduced.

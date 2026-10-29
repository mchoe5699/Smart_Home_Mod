# SHT-101C flat colored elevation 3.6.1

Baseline: origin/main 4204d000a4aa0ad4e6acfbd3862241b1732675c0. Existing uncommitted family functionality is preserved.

The user's clarification supersedes the previous shallow relief interpretation: color the approved drawing as flat artwork, and extrude only its whole outer rectangle. The new OBJ has exactly six quads/eight positions and a constant .60 model-pixel depth. All switches, lamps, cradle and screen are printed on the same front plane. There are no separately raised or recessed parts. Side/back faces reuse SHT-308's #eee9d3 ivory. No receiver/antennas; exterior only, no invented GUI or functions.

The approved line drawing was recolored with image generation. Its output is kept verbatim in sht_101c_flat_front.png; the generator only selects the outer rectangular boundary with UVs, removing blank margins without image processing. Fine text and tiny icon forms inherit the supplied drawing/generated recoloring and are not claimed to be exact manufacturer artwork. The former relief generator is archived as build_sht101c_relief_legacy.py, not invoked by the active asset generation command.

Run python3 tools/build_sht101c_exterior_assets.py. Normal release build excludes the external capture harness. Validation: integrated-game placement/four facings/item/no functional entity/baked model, raw front and oblique/profile screenshots, normal build and artifact inspection. Online release bytes are compared with the local build.

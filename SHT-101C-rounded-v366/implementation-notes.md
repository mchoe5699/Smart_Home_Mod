# SHT-101C rounded inner frame 3.6.6

Baseline: origin/main 4204d000a4aa0ad4e6acfbd3862241b1732675c0; prior local work preserved.

The user requested slightly less projection of the marked inner face and a rounded transition from the immediately surrounding INNER frame into that boundary, explicitly excluding the outer mounting frame. The inner face now projects .30 rather than .40 model pixels beyond the .10 backing (25% reduction in additional projection). Its face outline and printed button layout remain unchanged. The immediate frame is a continuous 24-step cosine-eased surface joining z15.9 to15.6. Both depth endpoints have zero slope. The large outer rectangular backplate remains square and unchanged.

The rolled band follows the inner face silhouette including its upper-center tab. It starts at the existing intermediate frame outline: main x176..1339, y223..1018, with a narrow upper-tab collar inside the backplate. All label UVs follow their drafting positions. No texture edit was performed; the thinner central boundary strokes from3.6.5 are retained byte-for-byte. Collision uses conservative boxes covering the new curved surface. 204 quads total; individual keys, CRT and cradle remain printed on a shared flat front.

Validation: geometry face count, winding and projected depth checks; in-game placement/facing/item/baked-model assertions and raw front/oblique/profile captures; normal build excluding external capture harness; packaged resources checked and downloaded publication byte-compared. The external capture harness is not distributed.

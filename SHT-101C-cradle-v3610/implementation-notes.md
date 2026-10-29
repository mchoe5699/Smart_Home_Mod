# SHT-101C real cradle recess 3.6.10

Baseline: origin/main4204d000a4aa0ad4e6acfbd3862241b1732675c0, prior local family changes preserved.

The user requested carving the receiver cradle following its shape. Removed the flat front cover over that region and formed a long rounded well, following the existing drawn outer contour (241,270)-(435,975), radius49 drafting units, down to an inset floor (258,285)-(421,960), radius38. The floor is .23 model pixels behind the raised front face (z15.83 versus15.6), in front of the backing at15.9. This is a closed-bottom recess, not a hole through the device. Twelve depth bands and16 arc subdivisions per corner avoid coarse steps. The pre-existing hook/grille/contact graphics remain printed at their original drafting coordinates on the well floor; this revision does not independently raise those details.

The other panels, convex side shoulders, upper cover projection, model proportions and artwork are preserved. Render geometry1124 quads. Collision is also cut over the inner rounded floor with24 conservative vertical slices, leaving the rim intact. No functional state machine or GUI is added.

Validation: geometry winding/depth/face budget; integrated-game placement/facing/item/baked model and screenshots; normal release build excluding injected capture harness; packaged assets and unchanged artwork; downloaded publication byte comparison.

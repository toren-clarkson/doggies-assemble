# Dog Wand 1.7 validation

## Completed

- Compiled the plugin with Java 25 against Paper 26.3 API.
- 14 control checks: binding-list serialization and duplicate handling; independent wand lists; cancellation isolation for different groups; cancellation for shared dogs; ownership-transfer isolation; persisted revisions; movement/target selector separation; default mode; four-mode order and wrap; isolated selection containers; Release Orders remaining last after extension.
- Eight tests using Paper's actual NBT-backed persistent-data containers, including reproducing the original byte/integer exception and accepting both existing marker types safely.
- 11 production geometry checks: distant hit, entity precedence, wall occlusion, unloaded gap, sky exit, downward ray, negative coordinates, two slab cases, suitable footing, and obstructed body clearance.
- Compiled the isolated integration fixture with new binding, sit/stand, highlight, right-click semantics, and active/released teleport-event assertions.
- Inspected the available Paper server implementation: PaperMobGoals.getHandle routes a goal containing TARGET to targetSelector; FollowOwnerGoal independently navigates/teleports toward the owner. This explains why the previous combined goal failed to suppress following.

## Live verification still required

The managed Windows environment previously denied Java's local loopback socket before plugin enable. This restriction remains the known blocker for full automated server testing. The earlier user reports confirm previous clicks worked. They do not verify the new 1.2.0 features. This build has not passed an end-to-end client/server run here.

Run node tools/verify.mjs on a host that permits local server sockets. It uses an isolated loopback-only server at 127.0.0.1:25625, with synthetic players and normal server ticks. Work files live under .cache/server-1.3.1; logs are .cache/server-*.log. DOGWAND_TEST_HOME can choose a fresh scratch directory after an interrupted startup. The harness creates a test world, never touches a running production server, and restarts to check saved orders.

Manual checks should cover: binding different groups on different wands; sharing one dog across wands; holding two wands; both-hand controls; duplicate binds; foreign-owner rejection; persistence after dropping/storing a wand and restarting; all four modes and action-bar display; distant movement and attacks with the owner remaining behind; holding position while the owner walks away; teleport prevention while commanded and restoration after Release; swimming; closed doors and blocked terrain; and server protection plugins.

An unloaded dog cannot move, sit/stand, or gain glow until its chunk ticks. Release invalidates unloaded dogs' saved orders, but new movement/posture/glow commands do not queue onto unloaded dogs. Administrative teleports are also blocked while a command is active; release it first.

## 1.2.0 checks

28 added feature checks pass: 3D region normalization, inclusive corner blocks and exclusive outer faces, body clearance and boundary rejection, randomized samples inside the prism, region and order persistence (including issuing wand), legacy 1.1.0 order decoding, no-player automatic targeting, hostile/passive/pet filters, independent per-item mode containers and persistence, complete styled tooltip contents, compact lore wrapping, private metadata flag preservation/restoration, and resolving the actual private packet bridge against cached Paper 26.3. Feeding tests use NBT-backed item food components and controlled inventory/health callbacks to verify correct nutrition, consumption, cancelled-heal conservation, the 10-block radius, and no cost for healthy dogs.

Together with the 33 existing control, marker, and geometry checks, 61 checks pass. The live server fixture compiles with binding toggles, auto-target states, feeding, guard setup/boundary events, and no-global-glow assertions. It has not executed end-to-end here because of the known local network restriction. Private rendering still needs two actual clients (owner and another viewer) to verify outline visibility, expiry, and changes in tracking distance. Full guard navigation and protection-plugin interactions also need a server run. The standalone native bridge check bootstraps registries without creating a network server; it does not replace client rendering tests.

## 1.3.0 regression checks

24 additional checks pass, bringing the standalone total to 85: reproduce the incorrect raw-node boundary rejection and verify centered edge paths; ignore visited outside nodes while rejecting future outside nodes; target crowding scores; bounded long beams with exact endpoints; bounding-box point budgets and edge geometry; owner-filtered bulk indexing; death cleanup that retains unloaded/unknown dogs; persisted death records; ownership transfers; stale sightings after death; heart, beam, and box delivery through owner Player APIs with World broadcasts forbidden in the fixture; dimension-specific previews; and a private activation sound for all seven modes. The existing 61 feature, feeding, control, marker, and geometry checks also pass.

The live fixture is compiled separately. Actual multi-dog pathfinding, multiplayer particle visibility, held-Shift input, inventory refreshes, and server protection integrations still need an end-to-end server run. No successful live run is claimed in this environment.

## 1.3.1 regression checks

21 new checks in `node tools/check-guard-bindings.mjs` exercise premature arrival outside the region, body clearance at the border, entry aims on all faces and corners, a one-block footprint, clipping that preserves sideways/inward movement and look direction, corner sliding, vertical clipping, and physical final-entry steps. The same suite uses actual Paper item stacks and metadata to verify unique binding IDs with matching counts, styled lore preservation, unbinding, death cleanup, and stale/duplicate/missing count rows.

Two geometry checks resolve real entry footing against a deterministic flat-block world, including the one-block footprint. Together with the existing 85 checks, 108 standalone checks pass. The live server fixture compiles with assertions for clipped movement and actual item lore after unbinding and death, but has not executed here. These checks validate the navigation rules and item metadata; full native pathfinding, client inventory delivery, and multiple dogs entering a region still require gameplay verification.

## 1.4 release

This release changes the resource-pack wand texture and release metadata only. Plugin behavior remains as checked in 1.3.1. Rebuilt the Paper 26.3 plugin, both packs and the development bundle. Release checks verify archive contents, plugin version, JSON validity, and exact texture bytes in the new resource pack. No new gameplay test run is claimed for this visual update.

## 1.5 verification

Standalone suites cover FIFO queues and legacy order upgrades, spaced pack destinations, balanced target distribution, fixed-density visible region edges, persistent binding protection, and the production automatic/guard planner. Fetch tests exercise actual production reservation, pickup, delivery and death methods with real Paper item serialization and deterministic world/event adapters; they cover full custom stack preservation, cancellation, reload, owner and pickup restrictions, exclusive reservations and no delivery loops. Ledger checks use real Paper metadata and exercise owner locks, retirement after silent deletion, recovered retired items, offline/unloaded/opaque storage, nested storage, legacy owner inference, percentage rounding/cancellation and separate selected-mode/current-order lore.

The live fixture is compiled, but the known Windows loopback restriction still prevents an end-to-end server/client run here. Actual native navigation, mouse-release detection and rapid multi-click Shift gestures, creative/drag/shift transfers, forced inventory writes, protection-plugin integrations and multiplayer rendering require an in-game verification. No absolute deletion guarantee is claimed for external plugins with inaccessible private storage.

Final 1.5 result: **180 standalone checks passed across nine suites** (25 pack, 21 fetch/planner, 25 ownership/ledger/lore, 14 control, 8 marker, 13 geometry, 25 private-feedback, 28 feature and 21 guard/binding checks). The expanded native integration fixture compiles; it includes Shift confirmation, percentage/cancel gestures, foreign pickup and cramming-event assertions. The fixture has not run end-to-end here.

## 1.6 verification

A native Minecraft/Paper attribute regression reproduces the supplied duplicate-modifier exception using a negative spawn-style modifier, then verifies repeated enable/disable and base-value changes through the production NavigationRange helper. Updated pack tests cover compact stays, melee approaches for every dog, spherical hitbox selection and the 2.5-block boundary. Production planner checks cover equal simultaneous Point Order assignments and an automatic target for every standing dog. Fetch checks cover discovering fresh drops while armed, idling without turning off and merge-safe delivery-loop prevention. The full bulk mapping covers free percentages, all-dog percentages, Unbind All and cancellation; known empty binding groups persist to invalidate unloaded saved orders. The expanded native fixture includes toggle and bulk actions but has not run end-to-end in this environment.

Final 1.6 result: **205 standalone checks passed across ten suites** (31 pack, 26 fetch/planner, 31 ownership/bulk/lore, 8 native navigation-attribute, 14 control, 8 marker, 13 geometry, 25 private-feedback, 28 feature, and 21 guard/binding checks). The native regression reproduces the precise duplicate-modifier exception in the supplied log. The expanded live fixture compiles, including continuous Fetch toggling and the free/all/Unbind All/cancel binding cycle; no successful end-to-end client/server run is claimed.

## 1.7 rebuild

Rebuilt the current Java sources and both packs with the user's revised in-game text. Preserved the edited recipe lore in the generator and verified that rebuilding retains it. Release validation checks compiled text, version metadata and bundled artifacts. This text update does not add a new gameplay test run.

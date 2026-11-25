# Dog Wand 1.7 - Paper 26.3

Requires Java 25 and Paper 26.3. No client mods.

## Builds and downloads

See [dist/README.md](dist/README.md) for the current downloads. Distributions separate into `dist/resourcepack/<minecraft-version>/`, `dist/datapack/<minecraft-version>/`, and `dist/plugin/<minecraft-version>/<server-type>/`. Full bundles live under `dist/bundles/<minecraft-version>/<server-type>/`. Filenames include the Dog Wand release number so future releases can coexist.

Currently only Minecraft 26.3 with Paper has an implementation. Packs share a Minecraft-version directory; server plugins separate by backend. Other versions and backends require compatible implementations and validation before adding their distributions.

Build with Java 25, Node.js and cached Paper dependencies available through `DOGWAND_PAPER_HOME` (an extracted Paper 26.3 server directory containing `libraries/` and `versions/`). The local fallback uses `../corrupted-overworld/.cache/test-server`.

```sh
node tools/build.mjs
node tools/package.mjs
```

The first command creates the plugin and both packs. The second creates a full development bundle. Maven's `package` goal also writes the plugin JAR into the same dedicated plugin directory; it does not build the packs. Increment `pom.xml` and `src/main/resources/plugin.yml` together for each new Dog Wand release. Existing release artifacts stay in place.

## Upgrade / installation

Stop the server, replace the old JAR in plugins/ and the old crafting ZIP in your world's datapacks/ directory, then start the server. Keep only one installed copy of each. Version 1.6 adds simultaneous area attack selections, continuous fetch toggling, the extended bulk-binding cycle, denser stays, and a fix for the duplicate navigation modifier exception. Keep the 1.4 artwork; install the new plugin and crafting pack to receive the updated behavior and default help text. Existing wand lore refreshes when used.

Existing 1.1.x through 1.4 wands keep their bindings and saved orders. Legacy wand ownership comes from known bound-dog ownership, or the first legitimate holder when that information is unavailable. Open or use old stored wands once to index their existing bindings before relying on globally free-dog selection. Wands upgrading from before 1.2 start in Point Order until you cycle their per-item mode selection. Wands from 1.0.x have no bindings, and those old owner-wide orders clear on upgrade. Newly crafted wands begin empty. Recipe discoveries remain unlocked. For the 1.3.0 -> 1.3.1 upgrade, replacing only the plugin JAR and restarting is sufficient; the crafting and resource packs have not changed.

The plugin supplies a fallback recipe when the datapack is absent. The datapack alone cannot control dogs.

## Crafting and discovery

Pick up a bone to unlock the recipe. The datapack also discovers it when a bone enters your inventory. With the plugin-only fallback, bone pickups and joining with a bone already in inventory unlock it. An ordinary renamed stick cannot act as a wand.

| Bone | Bone | Bone |
| --- | --- | --- |
| Bone | Stick | Bone |
| Bone | Bone | Bone |

## Bind dogs to an item

Hold a wand in either hand and right-click one of your tamed dogs. You can also right-click while pointing at a dog farther away in uninterrupted loaded chunks. That dog joins that specific item's group. Repeat for more dogs. Right-clicking an already-bound dog unbinds it. If this wand issued that dog's current order, unbinding releases that order; an order from another wand remains. Binding/unbinding never toggles posture. A brief input debounce prevents holding right-click from repeatedly binding/unbinding.

A dog can belong to multiple wands. Each wand stores its own dog UUID list and identity in item data, which survives inventories, drops, chests, and restarts. Only the dog's current owner can bind or command it, even if somebody else obtains the wand. Commands on overlapping groups replace orders only for the dogs those groups share. Disjoint groups keep independent orders.

If both hands hold a wand, the main-hand wand takes priority for all actions. Creative item copying copies its item data and bindings too; a separately crafted wand starts with its own empty group. The lore count retains unloaded dogs. Deaths observed by this version remove the dog from the count automatically; carried/open wands update immediately, and stored wands clean up when opened or used.

## Controls and mode cycle

Tap and release the sneak key (normally Shift) to advance the selected left-click mode. Holding Shift while right-clicking previews bulk binding and does not advance the mode. Release the mouse between clicks; release Shift to confirm. Holding Shift does not repeatedly cycle. The action bar above the hotbar shows a colored, bold mode name and a short description. Each wand remembers its own selected mode. Its lore lists every mode, emphasizes the selected one, wraps its detailed help into short lines, and shows the number of bound dogs. Command feedback replaces that indicator briefly.

| Mode, in cycle order | Left-click action |
| --- | --- |
| Point Order | Add eligible mobs within 2.5 blocks of the ray hit to a shared attack selection. Divide standing dogs evenly across all targets. With no eligible mobs there, clear attacks and compactly spread/stay. |
| Sit / Stand | If any loaded bound dog stands, sit the group. If all sit, stand the group. Cancel their previous orders. |
| Highlight Dogs | Show outlines to the owner alone for 200 ticks (10 game seconds). No global potion effect. Includes sitting dogs. |
| Auto Target | Cycle Off -> Hostile Only -> All Mobs. Standing bound dogs target eligible mobs within a spherical 20-block radius around the owner. Never players; tamed pets are excluded. |
| Feed Dogs | One click heals damaged bound dogs within a spherical 10-block radius, consuming viable meat from the owner's inventory until full or food runs out. |
| Guard Region | First click sets corner 1; second sets corner 2. Dogs return inside, roam, and attack hostile mobs inside the box. |
| Fetch Items | Toggle continuous fetching within 20 blocks. One wolf per item entity; return and drop stacks at your feet. Click again to turn it off. |
| Release Orders | Clear attack selections, fetching, guard duty and auto targeting; resume vanilla AI. Leave sitting dogs sitting. |

Without Shift, right-click toggles a dog's binding, regardless of mode. It no longer releases orders. Every left-click mode affects only the held wand's bound dogs that you currently own. Point Order excludes sitting dogs and acts in the owner's current dimension. Sit / Stand, Highlight, and Release consider loaded bound dogs across dimensions. No mode queues movement, posture, or glow for unloaded dogs. Release also invalidates saved orders for unloaded bound dogs, preventing stale orders from resuming when they load.

While holding a wand, mouse clicks belong to the wand, even if the other hand holds a tool. Put the wand away to feed a dog or interact with it normally. Mode selection now belongs to each wand item, rather than to the player. Existing bindings remain; old player-wide mode choices are not migrated, so existing wands initially select Point Order.

## Orders and movement

Point Order uses actual block collision shapes and entity hitboxes, choosing the nearer hit. Unattackable entities obstruct selection rather than allowing selection through them. A miss leaves orders unchanged. The ray ignores fluids, stops at unloaded chunks, and has no fixed gameplay range cap within the loaded area. It never force-loads chunks.

Dogs use native navigation with intermediate waypoints. During an active movement or combat task, separate movement and targeting goals suppress vanilla owner-following and competing target selection. Armed auto attack with no targets, and spare fetch dogs, use normal vanilla AI. A temporary navigation-range modifier supports the staged path requests and is removed on release, unload, or plugin shutdown. Automatic owner teleports cannot complete while a movement/combat task is active. Idle auto attack and idle fetch dogs can follow and teleport normally. The teleport-event guard also blocks administrative/plugin teleports during that period; release the order first if you need to teleport a dog deliberately.

Terrain, closed doors, blocked paths, and non-ticking or unloaded chunks can still prevent arrival. Dogs do not teleport to destinations or simulate outside ticking chunks. An unreachable stay order persists with throttled retries. Release or replace it to cancel.

Dogs stay standing near their destination, return after displacement, and do not follow the owner while commanded. Manual sitting cancels a dog's order. When a selected Point Order target dies, unloads, changes dimension, or becomes invalid, it leaves the selection and the pack redistributes across the remaining targets. Normal AI resumes when the selection empties. Auto Target and Guard Region instead look for another eligible target. After release, normal owner-following and vanilla teleporting resume.

The owner, their own tamed animals, armor stands, invulnerable entities, and creative/spectator players cannot serve as attack targets. Player targeting checks world PvP and the owner's scoreboard-team friendly-fire setting. Bites use native damage events so protection plugins can cancel them; plugin-specific pet rules may need integration.

Bindings, per-dog orders, and owner/dog cancellation revisions persist across restarts. Each dog has one active order: another wand bound to that dog can replace or release it. Ownership changes invalidate old owners' orders and authority.

## Administration

- /dogwand — controls.
- /dogwand mode — current mode.
- /dogwand give [online-player] — operator: give an empty wand.
- /dogwand reload — operator: reload config.

Permission dogwand.use defaults to everyone. dogwand.admin defaults to operators. Movement, timing, player targeting, and feedback settings live in plugins/DogWand/config.yml.

## Extend and build

Implement WandMode and register it through plugin.modes().register(...). IDs must remain stable. Registration order controls cycling; Release Orders stays last even when another plugin registers additional modes. The built-in modes select dogs through plugin.bindings().selection(player).

WandBindings handles item identity and bound UUIDs. RevisionLedger persists cancellation per owner/dog pair. DogCommands handles orders, posture, glow, and two separate AI goals. Never combine TARGET and MOVE flags into the same goal: Paper routes a goal containing TARGET to the target selector, where it cannot suppress owner-follow movement.

Use Maven with Java 25 (mvn package) for the plugin, or set DOGWAND_PAPER_HOME to an extracted Paper 26.3 server directory with libraries/ and versions/, then run these Node scripts with Java tools on PATH:

    node tools/build.mjs
    node tools/check-feedback.mjs
    node tools/check-features.mjs
    node tools/check-controls.mjs
    node tools/check-marker.mjs
    node tools/check-geometry.mjs
    node tools/verify.mjs
    node tools/package.mjs

The default dependency directory is the sibling corrupted-overworld/.cache/test-server workspace. DOGWAND_JAVAC selects another Java 25 compiler. The API pin matches the cached build 140; the user's supplied log confirms earlier builds of this plugin loaded on Paper build 262. The shipped JAR uses the Paper API, plus an isolated reflection bridge for private metadata packets. That bridge resolves against the running Paper 26.3 server classes; if a later server changes those internals, highlights report an error rather than falling back to global glow. Server-internal imports appear only in separate tests.

See TESTING.md for verification and remaining runtime checks.

## New mode details

Auto Target replaces the group's previous movement orders. Off and Release restore vanilla AI. With no eligible target, the custom AI goals deactivate: dogs use ordinary owner-following and vanilla teleporting while auto attack remains armed. The radius follows the owner. Hostile means Minecraft's Enemy classification, which includes some neutral monster types. All Mobs adds passive mobs such as animals and villagers; neither setting attacks players or any tamed pet. Sitting dogs remain sitting. Changing the selected mode alone does not stop an active order; use Off, Release, Sit/Stand, Point Order, or a new Guard order to replace it.

Feed Dogs is a one-shot batch, not a continuous toggle. It uses the server's wolf-food eligibility and item nutrition, does not breed dogs or grow puppies, and consumes one portion for each successful heal, even in Creative mode. Healthy or distant dogs cost no meat. A cancelled heal costs no meat. Normal inventory slots and the offhand can supply food. It leaves movement orders and posture unchanged.

Guard Region uses the inclusive blocks of the two corners, in either order, and includes all three axes. Include walkable air above the floor: two solid ground-level blocks at the same height define a floor-only box with no room for a dog. Invalid second corners leave the first corner selected so you can try again. Both corners must be in the same dimension. A region assigns to currently loaded standing bound dogs in that dimension. Completed guard orders survive restarts; unfinished corner selections do not. Dogs initially outside return inside. Once a dog fits inside, paths and movement events prevent its body from leaving the prism. A target that leaves the box is no longer pursued. A blocked entry or obstructed route can still prevent movement; chunks never force-load. Release clears guard duty and unfinished corner selection for the held wand.

Private highlights go only to the current owner's network connection and only for dogs their client tracks. They do not change server glow flags or apply a Glowing potion. Normal server metadata is restored on expiry and plugin shutdown; ownership changes and logout end the private highlight. A glowing effect applied by another plugin or vanilla gameplay remains independent and can still be visible to other players.

## 1.3.0 effects, binding, and guard updates

All new wand particles and activation sounds go only through the owner's Player connection. Successful feeding adds a small heart burst above each healed dog. Valid point orders draw a brief spark beam from the eye to the ray hit, with a hit marker. Selecting Guard Region while holding the wand shows the saved box outline every half second, only in its dimension. Particle budgets remain bounded for huge regions or long rays. Each of the seven left-click modes plays a simple private activation sound; selecting a mode also retains its private UI click. These additions do not mute ordinary vanilla mob sounds.

Shift-right-click now previews a percentage of your globally unbound, loaded or previously indexed tamed dogs, across dimensions and including sitting dogs. Only releasing Shift commits the selection; it never removes existing bindings. No chunks force-load. Dogs never observed by this plugin become available after their chunks first load.

A persistent death registry cleans binding UUID lists without confusing unloaded dogs with dead dogs. Already-dead dogs from before the plugin observed their death cannot be identified safely from a missing entity alone. Registry files live in plugins/DogWand/known-dogs.yml.

Point Order, Guard Region, Auto Target changes, Sit/Stand, and explicit Release all use the same prepare/release routine before committing the replacement: persist cancellation revisions, remove both old AI goals, stop paths, clear targets and aggression, and reset old wand order state. Invalid selections leave existing orders unchanged. Assigning the new order then creates fresh per-dog goal state. Feeding and highlighting leave movement commands alone.

Guard path validation now converts Paper's raw block-corner nodes to the dog's movement center and skips visited nodes. Each standing guard chooses local reachable patrol points and immediately chooses another after arrival. Guards balance comparable threats and select different approach directions to reduce crowding; failed approaches can try another side. Terrain, available space, and ticking chunks still limit what dogs can reach. Sitting dogs remain sitting.

## 1.3.1 border and lore fixes

Assigning a region starts an entry phase for each standing dog outside or touching the edge. Dogs aim at walkable footing inside the box and continue until their whole body clears the border with a small margin. The usual Point Order arrival radius cannot end this phase early. Once inside, boundary crossings clip only the outward part of movement, allowing inward movement and sliding along an edge instead of cancelling the entire step. Small final entry steps use ordinary physical movement and collisions; dogs do not teleport into the box.

Binding lists and their lore counts now update in the same item metadata write. Binding, bulk binding, unbinding, and observed deaths refresh the stored count. Periodic/use-time cleanup also repairs a stale count even when the UUID list was already correct. The active wand's full tooltip reads the latest item after selection and state cleanup, and binding/death events resend inventory state on the next tick. Existing stale count rows repair automatically when the wand is held or its inventory opens. Unloaded dogs still count until the plugin observes their death.

## Orders and pack behavior (1.6)

Point Order adds all eligible mobs whose hitboxes touch the 2.5-block sphere around the ray hit. Repeated selections union with the existing target set for this wand; duplicates do not add extra weight. The whole loaded standing pack receives the same selected set, then splits evenly across all available targets at once. Target group sizes differ by at most one. A no-mob point or Release clears the selection. Selected IDs persist on each dog, including reading legacy saved queues as simultaneous selections. Area selection does not include players; a direct player click still follows the existing PvP rules. Dogs shared with another wand obey the most recently issued order.

Block orders use distinct walkable destinations roughly 1.4 blocks apart inside a radius that grows with the bound pack size. If terrain leaves insufficient safe positions, remaining dogs resume normal AI rather than converge on one occupied block. Auto attack and guard duty assign dogs to the least-occupied eligible mobs before considering travel distance. Every allocated dog approaches and attacks its target; there is no eight-dog front-line limit or inactive reserve group. Sitting dogs and dogs whose chunks are not ticking remain excluded. Bound or actively commanded tamed wolves have cramming damage cancelled; the world gamerule and other damage causes stay unchanged.

Fetch Items toggles ON/OFF. While ON, it scans the owner's current spherical 20-block radius every half second and assigns each available item entity to exactly one standing loaded dog, across simultaneous wand groups. Spare dogs use native AI while waiting. Dogs pick up the entire current stack, return, and drop it at the owner's feet, then accept new items automatically. Completed deliveries carry an entity-only marker so dogs do not fetch the same drop repeatedly; merging preserves that marker. Picking up and dropping an item again creates a new fetchable entity. Ownership, mob-pickup restrictions, pickup delays and cancellable events still apply. Custom item data and counts persist on the wolf through unload/restart. OFF, Release, Unbind All from the issuing wand, or a replacement movement command stops fetching and drops any carried items safely near the dog. Offline or different-dimension owners pause delivery. Unreachable or denied items receive a retry delay; no chunks force-load.

Region outlines keep a fixed spacing of one particle per block. Only edge points within 96 blocks of the owner are transmitted, so large boxes retain visible density without sending millions of distant particles. Effects and sounds remain private to the owner.

### Confirmed bulk binding

Hold Shift, right-click and release the mouse for each selection step, then release Shift to confirm:

| Right clicks while Shift stays held | Free dogs to bind |
| --- | --- |
| 1-5 | 100 / 80 / 60 / 40 / 20% of your globally unbound dogs |
| 6-10 | 100 / 80 / 60 / 40 / 20% of all your known tamed dogs |
| 11 | Unbind all dogs from this wand |
| 12 or more | Cancel |

Free means your known tamed dogs with no registered binding to any wand. All includes your dogs that already belong to this or other wands. Percentages apply to that pool when Shift is released, round up for fractional dogs, and select randomly. Both percentage scopes add bindings and keep existing bindings; the confirmation reports how many were newly added. Unbind All clears only this wand's bindings and orders, and invalidates its saved orders on unloaded dogs while preserving other wands' bindings. Normal right-click can still bind a dog to multiple wands intentionally. Switching the selected wand cancels the pending bulk operation. Tapping Shift without any right-click still cycles modes. The action bar previews the percentage throughout the selection.

### Owner locks and deletion

New wands gain a persistent owner and item ID when crafted, given, or first legitimately claimed. Other players cannot pick them up or take them from storage, including containers holding nested wands. Inventory clicks, drags, creative transfers and armor-stand exchanges enforce the lock. Direct plugin writes into online player inventories are reconciled every second; foreign items are dropped with their original ownership intact. Only owners can use a wand. Crafting multiple wands produces separate IDs.

The durable ledger at plugins/DogWand/wands.yml tracks known wands alongside the binding index. A five-second census scans online player inventories, ender chests and cursors, open inventories, loaded block storage, item entities, storage entities, displayed/equipped items, nested bundles/shulker storage, and fetched cargo. Normal destruction, despawning, creative deletion, inventory clears, and silent removal from inspectable known locations retire missing wands after a five-second absence grace. Their dogs become free again, and orders issued by the retired wand clear. A recovered retired item becomes a new empty wand with its original owner.

No server plugin can reliably distinguish an arbitrary external plugin's hidden storage from deletion without that plugin's cooperation. Offline inventories, unloaded chunks, and opaque virtual storage therefore retain bindings. Deletions there reconcile when the location becomes inspectable; they are not guessed from absence. Use /dogwand wands to list your live registered IDs and /dogwand forget <UUID> to retire a genuinely missing wand explicitly. If the item later returns, its old bindings remain retired. Never delete registry files to clean up a wand.

### Current-order lore

The selected mode still determines the next left-click action. A separate **Current orders** section reports what the bound dogs actually do now: go/stay, simultaneous attack selections, guarding, auto attack or idle follow, fetching/returning, ordinary follow, sitting, and mixed orders. It includes orders issued by another wand to shared dogs. Unloaded dogs show as unloaded/unknown rather than falsely reporting that they have released their orders.

## 1.6 movement error fix

The supplied Paper 26.3 log showed repeated `Modifier is already applied on this attribute!` errors in `DogCommands.navigationRange`, interrupting the group planner. A native negative spawn-style range modifier can leave the effective range below 48 even after the plugin modifier exists. Navigation updates now check the modifier key before adding it, making repeated updates idempotent and preserving other modifiers. Short physical finishing steps also help dogs whose native path ended just outside their stay or attack position.

## 1.7 text update

Updates in-game mode descriptions, action-bar messages and the crafting recipe's default lore. Gameplay behavior remains the same as 1.6.

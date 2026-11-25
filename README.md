# Dog Wand 1.3.1 - Paper 26.3

Requires Java 25 and Paper 26.3. No client mods.

## Upgrade / installation

Stop the server, replace the old JAR in plugins/ and the old crafting ZIP in your world's datapacks/ directory, then start the server. Keep only one installed copy of each. The resource-pack artwork has not changed; the bundle includes it for new installs.

Existing 1.1.x, 1.2.x and 1.3.x wands keep their bindings and saved orders. Wands upgrading from before 1.2 start in Point Order until you cycle their per-item mode selection. Wands from 1.0.x have no bindings, and those old owner-wide orders clear on upgrade. Newly crafted wands begin empty. Recipe discoveries remain unlocked. For the 1.3.0 -> 1.3.1 upgrade, replacing only the plugin JAR and restarting is sufficient; the crafting and resource packs have not changed.

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

Tap and release the sneak key (normally Shift) to advance the selected left-click mode. Holding Shift while right-clicking performs bulk binding and does not also advance the mode. Holding Shift does not repeatedly cycle. The action bar above the hotbar shows a colored, bold mode name and a short description. Each wand remembers its own selected mode. Its lore lists every mode, emphasizes the selected one, wraps its detailed help into short lines, and shows the number of bound dogs. Command feedback replaces that indicator briefly.

| Mode, in cycle order | Left-click action |
| --- | --- |
| Point Order | First block hit: go and stay. First attackable entity hit: pursue and attack. |
| Sit / Stand | If any loaded bound dog stands, sit the group. If all sit, stand the group. Cancel their previous orders. |
| Highlight Dogs | Show outlines to the owner alone for 200 ticks (10 game seconds). No global potion effect. Includes sitting dogs. |
| Auto Target | Cycle Off -> Hostile Only -> All Mobs. Standing bound dogs target eligible mobs within a spherical 20-block radius around the owner. Never players; tamed pets are excluded. |
| Feed Dogs | One click heals damaged bound dogs within a spherical 10-block radius, consuming viable meat from the owner's inventory until full or food runs out. |
| Guard Region | First click sets corner 1; second sets corner 2. Dogs return inside, roam, and attack hostile mobs inside the box. |
| Release Orders | Clear orders and command-related aggression for bound dogs; resume normal vanilla AI. Leave sitting dogs sitting. |

Right-click always toggles a dog's binding, regardless of mode. It no longer releases orders. Every left-click mode affects only the held wand's bound dogs that you currently own. Point Order excludes sitting dogs and acts in the owner's current dimension. Sit / Stand, Highlight, and Release consider loaded bound dogs across dimensions. No mode queues movement, posture, or glow for unloaded dogs. Release also invalidates saved orders for unloaded bound dogs, preventing stale orders from resuming when they load.

While holding a wand, mouse clicks belong to the wand, even if the other hand holds a tool. Put the wand away to feed a dog or interact with it normally. Mode selection now belongs to each wand item, rather than to the player. Existing bindings remain; old player-wide mode choices are not migrated, so existing wands initially select Point Order.

## Orders and movement

Point Order uses actual block collision shapes and entity hitboxes, choosing the nearer hit. Unattackable entities obstruct selection rather than allowing selection through them. A miss leaves orders unchanged. The ray ignores fluids, stops at unloaded chunks, and has no fixed gameplay range cap within the loaded area. It never force-loads chunks.

Dogs use native navigation with intermediate waypoints. During an order, separate movement and targeting goals suppress vanilla owner-following and competing target selection. A temporary navigation-range modifier supports the staged path requests and is removed on release, unload, or plugin shutdown. Automatic owner teleports cannot complete while an order is active. The teleport-event guard also blocks administrative/plugin teleports during that period; release the order first if you need to teleport a dog deliberately.

Terrain, closed doors, blocked paths, and non-ticking or unloaded chunks can still prevent arrival. Dogs do not teleport to destinations or simulate outside ticking chunks. An unreachable stay order persists with throttled retries. Release or replace it to cancel.

Dogs stay standing near their destination, return after displacement, and do not follow the owner while commanded. Manual sitting cancels a dog's order. If a Point Order attack target dies, unloads, changes dimension, or becomes invalid, the dog returns to normal AI. Auto Target and Guard Region instead look for another eligible target. After release, normal owner-following and vanilla teleporting resume.

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

Auto Target replaces the group's previous movement orders. Off and Release restore vanilla AI. With no eligible target, dogs walk toward the owner without teleporting. The radius follows the owner. Hostile means Minecraft's Enemy classification, which includes some neutral monster types. All Mobs adds passive mobs such as animals and villagers; neither setting attacks players or any tamed pet. Sitting dogs remain sitting. Changing the selected mode alone does not stop an active order; use Off, Release, Sit/Stand, Point Order, or a new Guard order to replace it.

Feed Dogs is a one-shot batch, not a continuous toggle. It uses the server's wolf-food eligibility and item nutrition, does not breed dogs or grow puppies, and consumes one portion for each successful heal, even in Creative mode. Healthy or distant dogs cost no meat. A cancelled heal costs no meat. Normal inventory slots and the offhand can supply food. It leaves movement orders and posture unchanged.

Guard Region uses the inclusive blocks of the two corners, in either order, and includes all three axes. Include walkable air above the floor: two solid ground-level blocks at the same height define a floor-only box with no room for a dog. Invalid second corners leave the first corner selected so you can try again. Both corners must be in the same dimension. A region assigns to currently loaded standing bound dogs in that dimension. Completed guard orders survive restarts; unfinished corner selections do not. Dogs initially outside return inside. Once a dog fits inside, paths and movement events prevent its body from leaving the prism. A target that leaves the box is no longer pursued. A blocked entry or obstructed route can still prevent movement; chunks never force-load. Release clears guard duty and unfinished corner selection for the held wand.

Private highlights go only to the current owner's network connection and only for dogs their client tracks. They do not change server glow flags or apply a Glowing potion. Normal server metadata is restored on expiry and plugin shutdown; ownership changes and logout end the private highlight. A glowing effect applied by another plugin or vanilla gameplay remains independent and can still be visible to other players.

## 1.3.0 effects, binding, and guard updates

All new wand particles and activation sounds go only through the owner's Player connection. Successful feeding adds a small heart burst above each healed dog. Valid point orders draw a brief spark beam from the eye to the ray hit, with a hit marker. Selecting Guard Region while holding the wand shows the saved box outline every half second, only in its dimension. Particle budgets remain bounded for huge regions or long rays. Each of the seven left-click modes plays a simple private activation sound; selecting a mode also retains its private UI click. These additions do not mute ordinary vanilla mob sounds.

Shift-right-click adds ALL your loaded or previously indexed tamed dogs to the current wand, across dimensions, including sitting dogs. It is additive, so it never removes an existing binding. No chunks force-load. A dog that has always remained in an unloaded chunk and has never been observed by this plugin cannot be discovered until that chunk loads once. The known ownership index persists across restarts and updates when loaded dogs change owner.

A persistent death registry cleans binding UUID lists without confusing unloaded dogs with dead dogs. Already-dead dogs from before the plugin observed their death cannot be identified safely from a missing entity alone. Registry files live in plugins/DogWand/known-dogs.yml.

Point Order, Guard Region, Auto Target changes, Sit/Stand, and explicit Release all use the same prepare/release routine before committing the replacement: persist cancellation revisions, remove both old AI goals, stop paths, clear targets and aggression, and reset old wand order state. Invalid selections leave existing orders unchanged. Assigning the new order then creates fresh per-dog goal state. Feeding and highlighting leave movement commands alone.

Guard path validation now converts Paper's raw block-corner nodes to the dog's movement center and skips visited nodes. Each standing guard chooses local reachable patrol points and immediately chooses another after arrival. Guards balance comparable threats and select different approach directions to reduce crowding; failed approaches can try another side. Terrain, available space, and ticking chunks still limit what dogs can reach. Sitting dogs remain sitting.

## 1.3.1 border and lore fixes

Assigning a region starts an entry phase for each standing dog outside or touching the edge. Dogs aim at walkable footing inside the box and continue until their whole body clears the border with a small margin. The usual Point Order arrival radius cannot end this phase early. Once inside, boundary crossings clip only the outward part of movement, allowing inward movement and sliding along an edge instead of cancelling the entire step. Small final entry steps use ordinary physical movement and collisions; dogs do not teleport into the box.

Binding lists and their lore counts now update in the same item metadata write. Binding, bulk binding, unbinding, and observed deaths refresh the stored count. Periodic/use-time cleanup also repairs a stale count even when the UUID list was already correct. The active wand's full tooltip reads the latest item after selection and state cleanup, and binding/death events resend inventory state on the next tick. Existing stale count rows repair automatically when the wand is held or its inventory opens. Unloaded dogs still count until the plugin observes their death.

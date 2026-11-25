> Doggies Assemble! requires the installation of the **plugin, the datapack, and the resourcepack**.


# Doggies, Assemble!

Doggies, Assemble! lets you control groups of your tamed wolves with a _dog wand_. Send them to a location, split them across enemies, defend a region, fetch dropped items, and more to come.

Each wand controls its own group and remembers its selected mode.


## Craft your wand


![Pick up a **bone** to discover the recipe, then surround a stick with eight bones](https://cdn.modrinth.com/data/cached_images/d3556aaff3b5676ce96fd70e67b16f7b0580d504.png)

## Controls

Hold the wand in your **main hand or offhand**.

| Input | Action |
|---|---|
| **Right-click your dog** | Bind it to this wand, or unbind it if it already belongs to this wand. |
| **Left-click** | Use the selected mode (see "Modes"). |
| **Press Shift** | Cycle through modes. |
| **Hold Shift and right-click** | Preview a bulk-binding selection. Release Shift to confirm. (see "Bulk Binding")|


You can bind multiple dogs to one wand, and one dog can belong to multiple wands. Only the dog's owner can command it. Wands also have owner locks that prevent other players from picking them up or taking them from storage through normal inventory interactions.

## Modes
_Keep in mind these orders only apply to dogs that are loaded in and are bound to the dog wand you are using._

### Point Order

Point and left-click to select mobs for the dogs bound to that dog wand to attack.
Your standing dogs split evenly across all selected targets and attack them simultaneously.

Point and left-click an area with no mobs around to make the bound dogs go then stay there instead.


![Bound dogs moving to a location](https://i.imgur.com/Wu1iF0X.jpeg)

### Sit / Stand

Make the bound dogs toggle sit/stand.
Other movement commands leave sitting dogs alone.

![Bound dogs sitting](https://i.imgur.com/dbtXcpb.jpeg)

### Highlight Dogs

Highlight your bound dogs to see where they are.

![Bound Dogs Highlighted](https://i.imgur.com/Ww3sseL.jpeg)

### Auto Target

Left-click to Cycle between:

- **Off**
- **Hostile Only**
- **All Mobs**

Dogs split evenly across eligible mobs within **20 blocks of you**.
With no targets, dogs use normal AI while the mode stays armed. Automatic targeting excludes players and tamed pets.

![Bound dogs in auto target all mobs mode](https://i.imgur.com/MPcwn1o.jpeg)

### Feed Dogs

Heal damaged bound dogs within **10 blocks**, using suitable meat from your inventory.
Healthy dogs do not consume food.

![Bound dogs getting fed using the meat in inventory](https://i.imgur.com/XWcaHsL.jpeg)

### Guard Region

Left-click two blocks to select opposite corners of a **3D rectangular region**.

Dogs enter the region, roam inside it, and divide themselves across hostile mobs that enter and attack, essentially guarding this region. While you hold the wand in Guard Region mode, the edges of the region are shown with particles.
Include enough walkable space and air for your dogs when selecting a region.

![Bound dogs guarding a rectangular prisim region](https://i.imgur.com/XwRrNp7.jpeg)

### Fetch Items

Makes the bound dogs fetch any dropped items for you.

Dogs collect available dropped items within **20 blocks of you**, return, and drop the stacks at your feet. Each item entity receives only one dog to go and get.

![Bound dogs fetching items](https://i.imgur.com/sDhgDRI.jpeg)

### Release Orders

Clear attack selections, guard duty, fetching, and automatic targeting. Dogs return to normal vanilla AI.

Sitting dogs remain sitting.

![Bound dogs set back to vanilla AI](https://i.imgur.com/83hLwjy.jpeg)

## Bulk binding

Hold **Shift**, then right-click a certain number of times, then release Shift to confirm selection. The number of times you right-click while holding Shift defines the number of dogs to bulk-bind to that dog wand:

| Right-click count | Selection |
|---|---|
| 1–5 | **100 / 80 / 60 / 40 / 20%** of your globally _unbound_ dogs |
| 6–10 | **100 / 80 / 60 / 40 / 20%** of _all your tamed_ dogs |
| 11 | **Unbind all** dogs from this wand |
| 12 or more | **Cancel** |

“Unbound” means the dog has no registered binding to any wand. The all-dog choices can include dogs already bound to other wands.

Percentage selections add bindings and preserve existing ones. Fractions round up, and the plugin selects dogs randomly from the chosen pool.



## Installation

1. Put the matching plugin JAR in `plugins/`.
2. Put the datapack ZIP in your world's `datapacks/` folder.
3. Install and enable the resource pack on your client, or distribute it through your server.


When updating, keep the plugin's data folder to preserve its records.

## Things to know

Dogs use Minecraft's native pathfinding. Walls, terrain, protection plugins, and unloaded or non-ticking chunks can prevent them from reaching a destination. The plugin does not force-load chunks.

The plugin tracks known dogs and wands. Offline inventories, unloaded storage, and inaccessible plugin storage do not count as proof that a wand has been deleted. Missing wands reconcile when their locations become inspectable.

For a confirmed missing wand, use `/dogwand wands` to find its registered ID, then `/dogwand forget <UUID>` to retire it and release its bindings.

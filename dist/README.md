# Dog Wand distributions

Only Minecraft **26.3 / Paper** is currently supported. Purpur, Spigot and other Minecraft versions have no builds yet.

| Artifact | Directory | Current release |
| --- | --- | --- |
| Resource pack | `resourcepack/<minecraft-version>/` | [1.3.1 / 26.3](resourcepack/26.3/dog-wand-1.3.1-resourcepack-26.3.zip) |
| Crafting datapack | `datapack/<minecraft-version>/` | [1.3.1 / 26.3](datapack/26.3/dog-wand-1.3.1-datapack-26.3.zip) |
| Server plugin | `plugin/<minecraft-version>/<server-type>/` | [1.3.1 / 26.3 / Paper](plugin/26.3/paper/dog-wand-1.3.1-paper-26.3.jar) |
| Full development bundle | `bundles/<minecraft-version>/<server-type>/` | [1.3.1 / 26.3 / Paper](bundles/26.3/paper/Dog-Wand-1.3.1-paper-26.3-development.zip) |

Packs are shared between server types for a given Minecraft version. Plugin JARs and full bundles separate by server type. Every filename includes the Dog Wand release number; building a new release preserves older release files. Rebuilding the same release replaces that release's files.

Run `node tools/build.mjs` from the repository root to compile the plugin and create both packs. Then run `node tools/package.mjs` to create the development bundle. Update the release version in both `pom.xml` and `src/main/resources/plugin.yml` together, and update this release index when publishing. Build tools and test runners share artifact paths from `tools/build.mjs`.

Adding another target requires implementing and checking its API, Java level, item data, and pack formats. Renaming a file or changing the target label does not add compatibility.

# AE2 optional API stubs (compile-only)

GTNH Applied Energistics 2 (`rv3-beta-1000-GTNH`) builds against Mekanism / RotaryCraft with `compileOnly`, so classes such as `TileChest` may still list those interfaces in the published bytecode.

Any mod that references `TileChest` at **compile time** needs those interface `.class` files visible to javac. That does **not** mean:

- the GTNH pack installs Mekanism / RotaryCraft
- TeXTech needs them at runtime
- stubs are packaged into the published `textech` jar

`addon.gradle` task `buildAe2OptionalApiStubs` jars this directory into `build/ae2-optional-api-stubs.jar` (`compileOnly`).

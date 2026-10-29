# Special Compatibility Operation: Fabric and Quilt to FeatureCreep converter

Status: planned, not implemented. This is a separate project. It is described here so `featurecreep-loader` keeps the features the converter needs.

## Goal

Turn a Fabric or Quilt mod jar into a FeatureCreep module that `featurecreep-loader` can discover, order, and run. The converter writes metadata. It does not recompile code or rewrite class files. Class-level changes that are needed (access widening, environment stripping, mixin registration) happen at load time in the loader, from data the converter writes.

## Inputs

- `fabric.mod.json`. Required for Fabric mods. Its `schemaVersion` selects the parser.
- `quilt.mod.json`. Present in Quilt mods, often alongside `fabric.mod.json`.
- Mixin config files (`*.mixins.json`) named in the descriptor.
- Access widener files named by `accessWidener`.
- Nested jars named by `jars`.
- Language adapter jars, when the descriptor names a `languageAdapter` (for example Kotlin or Scala).
- The mod's classes and resources, copied unchanged.

## Concept mapping

### Identity and metadata

| Fabric / Quilt | FeatureCreep | Notes |
|---|---|---|
| `id` | `FeatureCreep-Mod-Id` and module `name` | Same ID space. |
| `version` | Module `version` | Used by version predicates. |
| `name`, `description` | `FeatureCreep-Name`, `FeatureCreep-Description` | |
| `authors`, `contact.homepage` | `FeatureCreep-Authors`, `FeatureCreep-Website` | Contact objects are flattened. |
| `license` | `FeatureCreep-License` | |
| `icon` | Not mapped yet | No FeatureCreep property exists for icons. |
| `custom` | Not mapped | Logged. |

### Environment

| Fabric / Quilt | FeatureCreep | Notes |
|---|---|---|
| `environment` `*` | `FeatureCreep-Side` `both` | |
| `environment` `client` / `server` | `FeatureCreep-Side` `client` / `server` | Gates client/server entrypoints and environment stripping. |
| `@Environment` on classes | Not enforced | Class-level environment is a loader limitation (see `MODULE_XML.md`). |

### Entrypoints

Fabric's `entrypoints` object maps a key to a list of entries. An entry is either a class name string or an object with `adapter` and `value`.

| Fabric key | FeatureCreep entrypoint | Notes |
|---|---|---|
| `preLaunch` | `<entrypoint type="preLaunch" adapter="fabric">` | Runs in the `preLaunch` phase. |
| `main` | `<entrypoint type="main" adapter="fabric">` | Runs in the `main` phase. |
| `client` | `<entrypoint type="client" adapter="fabric">` | Runs in `ready`, gated by `FeatureCreep-Side`. |
| `server` | `<entrypoint type="server" adapter="fabric">` | Runs in `ready`, gated by `FeatureCreep-Side`. |
| any other key | `<entrypoint type="<key>" adapter="fabric">` | Custom entrypoint types run in `ready`. |
| `adapter` in an entry object | `adapter="<name>"` | One adapter name per entrypoint. |

Fabric entrypoints are instance methods on implementers of an interface, such as `ModInitializer.onInitialize()`. The built-in `java` adapter calls `public static void` methods. A Fabric entrypoint therefore needs a different invocation path. See Open questions, item 1.

### Dependencies

| Fabric / Quilt | FeatureCreep kind | Notes |
|---|---|---|
| `depends` | `required` | Version predicates map to `version-range`. |
| `recommends` | `recommends` | Warns when missing. |
| `suggests` | `suggests` | Warns when missing. Same behavior as `recommends`. |
| `conflicts` | `conflicts` | Warns only. Never blocks loading. |
| `breaks` | Not converted | See Not converted. |

Fabric version predicates include `*`, comparators, `x` wildcards, `||`, `~`, and `^`. The loader's `FCVersionRange` grammar supports the same forms, so predicates copy across unchanged.

Quilt's `depends` entries use `id` and `versions`. Optional Quilt dependencies convert to `optional` (or `recommends`, when the Quilt entry says so). The exact Quilt field names must be checked against the Quilt Loader source before implementation.

### Nested jars

| Fabric / Quilt | FeatureCreep | Notes |
|---|---|---|
| `jars` `[ { "file": ... } ]` | `FeatureCreep-Nested-Jars` `declared` with `FeatureCreep-Nested-Jar-Paths` | Nested jars stay private, matching the loader default for nested JARs. |
| Quilt `container` nesting | Same as `jars` | Field names to confirm. |

### Mixins

| Fabric / Quilt | FeatureCreep | Notes |
|---|---|---|
| `mixins` string entry | Added to `MixinConfigs` | |
| `mixins` object with `environment` | `MixinConfigs` entry gated by side | Split into client and server lists, or left as one list with a note. |

Mixin configs are passed to the host through `MixinSupport`. The loader does not apply them. A host without a `MixinSupport` implementation logs once and ignores them.

### Access widener

Fabric access wideners only widen. They are a good match for the MappingEx access rules.

| Fabric AW | MappingEx output | Notes |
|---|---|---|
| `accessible class` / `accessible field` / `accessible method` | `AccessFlag` row, public | Widens visibility. |
| `extendable class` | `AccessFlag` row, public, `final` removed | Makes the class subclassable. Exact member effects to confirm against Fabric's AW semantics. |
| `mutable field` | `AccessFlag` row, `final` removed | |
| `transitive-*` lines | Same as the base line | Namespace transitivity does not apply here. |
| Unknown or malformed line | Not converted | Logged in the converter report. |

Each AW line becomes an `AccessFlag` row in a generated `.pdme` file. Field and method rows use the member path described in `docs/MappingExAccessFlagExtension.md`. The AW format version (v1 or v2) must be checked against the Fabric source before parsing.

Declared in `module.xml` as `MappingEx-Files`.

### Language adapters

A `languageAdapter` on an entrypoint becomes `adapter="<name>"` on the matching `<entrypoint>`. The adapter must be provided by a `LanguageAdapter` ServiceLoader entry. Behavior when no adapter matches is undecided. See Open questions, item 4.

## Outputs

- `modules/module.xml`: the descriptor. Properties, entrypoints, dependencies, and `MixinConfigs`, `MappingEx-Files`, and nested-jar settings.
- `.pdme` files: one per access widener, in a module-relative path referenced by `MappingEx-Files`.
- The rewritten jar: a copy of the original jar with `modules/module.xml` and the `.pdme` files added. Classes, resources, nested jars, and the original `fabric.mod.json` / `quilt.mod.json` are left in place. The original descriptors stay so the same jar still loads on Fabric or Quilt. The loader reads `module.xml` first.
- A converter report: a text file listing each converted, warned, and dropped item.

The output jar name proposal is `<original-name>-featurecreep.jar`, written next to the input.

## Pipeline

1. Read `fabric.mod.json` and `quilt.mod.json`, whichever exist.
2. Build the metadata model. If both exist, Quilt values win on conflict. This needs confirmation (Open questions, item 5).
3. Read AW and mixin files named by the descriptors.
4. Map entrypoints, dependencies, environment, nested jars, and mixin configs.
5. Write `.pdme` files from the AW content.
6. Write `modules/module.xml`.
7. Copy the jar, add the new entries, and write the report.

Verification: golden tests with fixture descriptors taken from real Fabric and Quilt mods. Each fixture checks the generated `module.xml` and `.pdme` text. An end-to-end test loads a converted fixture jar with `featurecreep-loader` and checks the module graph and entrypoint order.

## Not converted

- `breaks`. FeatureCreep has no breaking dependency type. A mod must not force another to crash. The converter drops these entries and warns.
- Fabric API hooks with no FeatureCreep equivalent, such as Fabric events, registries, networking, and rendering hooks. Mods that call them keep failing, as they do on any non-Fabric loader. Equivalents belong to `featurecreep-api`, if they are built at all.
- Quilt loader plugins, `intermediate_mappings`, and other Quilt-specific loader features.
- Fabric `jar-in-jar` behavior beyond nested JAR paths.
- Remapping of class and member names. See Open questions, item 3.
- Validation of mixin contents. The converter checks that files exist and parse as JSON, not that the mixins apply.

## Open questions

1. Instance entrypoints. Fabric calls instance methods on interface implementers, and the `java` adapter calls static methods. Options: (a) a `fabric` `LanguageAdapter` that instantiates the class and calls the interface method, shipped with the converter or the loader's optional adapter set; (b) generate static bridge classes at conversion time. Recommendation: (a). It keeps the `java` adapter small, and Fabric semantics stay in one place. Option (b) needs bytecode generation, which the converter has not planned for.
2. Fabric API interfaces. Converted mods that implement `ModInitializer`, `ClientModInitializer`, `DedicatedServerModInitializer`, or `PreLaunchEntrypoint` need those interfaces on the classpath. Options: a bundled shim jar, or require the mod to ship them.
3. Intermediary names. Fabric jars reference intermediary names, not the target game's names. Converting a Fabric mod for a different game version needs a remapping step, which is a separate design. Scope of the first version to be decided.
4. Missing adapter. If an entrypoint names an adapter that is not present, the proposal is to skip that entrypoint with a warning and load the rest of the mod. Needs confirmation, since it affects whether a mod can partially load.
5. Both descriptors present. The proposal is to read both and let Quilt values win on conflict. Confirm against Quilt's documented precedence.
6. Quilt key names. Confirm the Quilt entrypoint keys and dependency fields against the Quilt Loader source before writing the Quilt parser.
7. AW format version. Confirm v1 and v2 syntax and which forms the converter accepts.
8. Output placement. Whether the converter writes a sibling jar (proposed) or replaces the input.

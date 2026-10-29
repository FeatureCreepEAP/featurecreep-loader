# NeoFrom the Matrix: NeoForge descriptor support for featurecreep-loader

Status: planned, not started.

## Goal

Let `featurecreep-loader` read NeoForge mod metadata and wire it into FeatureCreep's own module graph, so NeoForge mods can be discovered, ordered, and checked without a second loader. Parsing only. Running NeoForge's own runtime (FML, event bus, registries) is out of scope and belongs to `featurecreep-api`.

## Constraints

- No third-party dependencies in the loader. The TOML reader is hand-written and covers only the subset NeoForge uses: tables, arrays of tables (`[[...]]`), dotted table names, strings (basic and literal), booleans, integers, and arrays of strings.
- Java 25. Annotation scanning uses `java.lang.classfile`.
- Parsing happens once at discovery time. Nothing here sits on the class-definition path.
- Mods must never crash the game through metadata. Malformed or unsupported entries produce a warning and are skipped. Only `required` dependency failures are fatal, matching the existing `module.xml` behavior.
- `main-class` and `module.xml` stay the primary path. NeoForge metadata is a second descriptor source, used only when a jar has no `module.xml`.

## Files a NeoForge mod ships

| Path in jar | Purpose | Status |
|---|---|---|
| `META-INF/neoforge.mods.toml` | Mod metadata, dependencies | Read |
| `META-INF/mods.toml` | Legacy Forge descriptor; read as fallback only if `neoforge.mods.toml` is absent | Read (open question 3) |
| `META-INF/accesstransformer.cfg` (via `[[accessTransformers]]`) | Access widening | Read, widening only |
| `META-INF/jarjar/metadata.json` | Nested jars (JarJar) | Reuses nested-JAR support |
| `*.mixins.json` (via `[[mixins]]`) | Mixin configs | Recorded, see Mixins |
| `pack.mcmeta` | Resource pack metadata | Recorded only |

## `neoforge.mods.toml` fields

Top level:
- `modLoader` (string, expected `javafml`), `loaderVersion` (Maven range), `license`, `issueTrackingURL`, `showAsResourcePack` (bool, recorded only)

`[[mods]]` (one or more):
- `modId`, `version`, `displayName`, `description`, `authors`, `credits`, `displayURL`, `logoFile`, `updateJSONURL`, `namespace`
- `version` may contain `${file.jarVersion}`. Substitute from the manifest `Implementation-Version`, or from `module.xml` if present.

`[[dependencies.<modId>]]`:
- `modId`, `type` (`required`, `optional`, `incompatible`, `discouraged`), `versionRange` (Maven range), `ordering` (`NONE`, `BEFORE`, `AFTER`), `side` (`BOTH`, `CLIENT`, `SERVER`)

Mapping to FeatureCreep kinds (see `MODULE_XML.md`, Dependencies):

| NeoForge | FeatureCreep kind | Behavior |
|---|---|---|
| `required`, side matches | `required` | Missing or out-of-range is fatal for the dependent module. |
| `optional`, side matches | `optional` | Silently skipped when missing or out of range. |
| `discouraged`, side matches | `conflicts` | Warn only, when present and in range. Never blocks loading. |
| `incompatible`, side matches | `conflicts` | Warn only, when present and in range. Never a `breaks`. A mod cannot stop another from loading through metadata. |
| `ordering` `BEFORE` / `AFTER` | Ordering edge, non-required | Feeds the topological sort. Cycles drop these edges with a warning. |
| `side` not matching the runtime side | Dropped | Ignored for ordering and checks. |

`discouraged` maps to `conflicts` rather than `recommends`. A discouraged mod should be reported when it is present, and `recommends` reports when a mod is absent.

Version ranges: NeoForge uses Maven-style ranges such as `[1.0,2.0)`, `(,2.0]`, and bare versions. Intervals and open bounds already parse with the `FCVersionRange` grammar. Bare-version meaning is not confirmed. Maven treats a bare version as a soft recommendation, and NeoForge may not. Do not assume.

## Entrypoints

NeoForge has no entrypoint table. Mods are found by the `@Mod("modId")` class annotation, so the loader must scan class files:

- Scan only classes in the jar's package roots that `neoforge.mods.toml` or the jar manifest points to. Do not scan every class on every launch.
- Read the `RuntimeVisibleAnnotations` attribute with `java.lang.classfile` and match `Lnet/neoforged/fml/common/Mod;`.
- Record the annotated class as the `main` entry for that mod, unless a `module.xml` `main-class` is already present. `main-class` keeps priority.
- Client-only and server-only `@Mod` classes are recorded with their dist when the annotation provides it, and are skipped on the other side.

## Access transformers

`[[accessTransformers]]` names a file (for example `META-INF/accesstransformer.cfg`). Its format differs from MappingExFormat. The rules here are AT rules, not `AccessFlag` lines.

Plan: parse the AT format into the same internal rule type as MappingEx. Apply only widening rules, as with MappingEx `AccessFlag`. A rule that would narrow a flag is logged and skipped, not applied.

Before implementing, confirm the AT syntax and whether NeoForge's `public-f` / `public` / `protected` forms map cleanly onto access widening. Narrowing forms are not supported and must not be applied.

## Mixins

`[[mixins]]` records are parsed, but mixin application is delegated to the host through `MixinSupport` (`GameProvider.getMixinSupport()`). The loader does not apply mixins itself. Open question: whether NeoForge mixin records are registered with the host or only recorded.

Options:
1. Parse `[[mixins]]` and record them, but do not register them. Warn that the mod expects mixins that FeatureCreep does not run. (Safest; matches "not loaded by other loaders".)
2. Register NeoForge mod mixin configs through `MixinSupport.registerMixinConfigs`. A host that implements `MixinSupport` applies them, with environment gating. The default `MixinSupport.NONE` logs once and ignores them.

Recommendation: option 1 for the first implementation. Option 2 needs a decision first.

## Lifecycle and events (mapping only, not implemented here)

| NeoForge | FeatureCreep equivalent |
|---|---|
| `FMLConstructModEvent` (constructor) | Module `main` invocation |
| `FMLCommonSetupEvent` | `ready` phase |
| `FMLClientSetupEvent` | `client` entrypoint |
| `FMLDedicatedServerSetupEvent` | `server` entrypoint |
| `@EventBusSubscriber` / `@SubscribeEvent` | Out of scope. Belongs to `featurecreep-api`. |
| `ModConfigSpec` | Config values in `./etc/<modId>.properties`. Spec-level API is out of scope here. |

## Nested jars (JarJar)

`META-INF/jarjar/metadata.json` lists nested jars with `identifier`, `version`, and `path`. The loader already supports nested JARs (private, public, declared, disabled), so map JarJar entries to those kinds. Jars nested this way are never exposed as public modules unless the metadata says so.

## Interaction with the existing pipeline

1. Discovery (existing) finds jars.
2. For each jar with no `module.xml`, read `neoforge.mods.toml`, then `mods.toml`, then skip.
3. Build an internal `FCModuleDescriptor` from the NeoForge fields. Identifiers are the `modId` values.
4. Dependency edges feed the topological sort, with kinds as in the mapping table above.
5. Access transformers feed the MappingEx rule index.
6. Entrypoints feed the entrypoint registry.

## Verification

- TOML subset parser: round-trip the sample files in the tests, including arrays of tables, dotted names, and malformed input that must warn and skip.
- Dependency mapping: a fixture for each NeoForge dependency type, with checks that `incompatible` and `discouraged` produce a warning and never throw or block.
- Version ranges: table-driven tests. Bare-version semantics are blocked on the source check above and must have a test once confirmed.
- `@Mod` scanning: fixture classes compiled with `javac`, checking the annotated class is found and an unannotated class is not.
- AT parsing: widening applied, narrowing rejected and logged.

## Open questions

1. Mixins from NeoForge mods: record only, or register through `MixinSupport`? (See Mixins above.)
2. Bare-version semantics in NeoForge ranges. Needs a source check.
3. Whether `mods.toml` fallback is wanted at all, or whether NeoForge-only is enough.

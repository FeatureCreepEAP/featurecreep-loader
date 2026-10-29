# FeatureCreep JBoss Modules metadata

FeatureCreep uses JBoss Modules as its only mod-loading backend. A mod may put
`module.xml` at either `modules/module.xml` (preferred for compatibility with
older FeatureCreep packages) or `/module.xml` in the mod JAR/directory.

Normal top-level FeatureCreep mods are **public by default**: they can see the
running game/launcher classes, the FeatureCreep API, and other public
FeatureCreep mods. Nested JARs are **private by default**, allowing two mods to
carry different versions of the same dependency.

## Recommended example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.9" name="example.coolmod">
    <main-class name="example.coolmod.Main"/>

    <properties>
        <property name="FeatureCreep-Mod-Id" value="example.coolmod"/>
        <property name="FeatureCreep-Name" value="Cool Mod"/>
        <property name="FeatureCreep-Description" value="Example FeatureCreep module"/>
        <property name="FeatureCreep-Authors" value="Example Author"/>
        <property name="FeatureCreep-Website" value="https://example.invalid"/>
        <property name="FeatureCreep-License" value="MIT"/>

        <!-- public is the default; isolated/private opts out of automatic mod visibility -->
        <property name="FeatureCreep-Visibility" value="public"/>
        <property name="FeatureCreep-Auto-Dependencies" value="true"/>

        <!-- true by default: unresolved classes/resources may fall back to the real game loader -->
        <property name="FeatureCreep-Host-Fallback" value="true"/>

        <!-- both (default), client, or server -->
        <property name="FeatureCreep-Side" value="both"/>
        <property name="FeatureCreep-Runnable" value="true"/>

        <!-- private (default), public, declared, or disabled -->
        <property name="FeatureCreep-Nested-Jars" value="private"/>
        <property name="FeatureCreep-Nested-Jar-Paths"
                  value="META-INF/jars/private-library.jar"/>

        <!-- Mixin configs; comma, semicolon, or whitespace separated. Applied only by a host that supplies MixinSupport. -->
        <property name="MixinConfigs"
                  value="coolmod.mixins.json,coolmod.client.mixins.json"/>

        <!-- Access widener rules, relative to the module root -->
        <property name="MappingEx-Files" value="META-INF/coolmod.pdme"/>

        <!-- Optional FeatureCreep/JVM transformer entry points -->
        <property name="FeatureCreep-Transformer-Classes"
                  value="example.coolmod.GameTransformer"/>
        <!-- module = JBoss-loaded classes only; game/all causes Instrumentation setup -->
        <property name="FeatureCreep-Transformer-Scope" value="all"/>

        <!-- Java-agent style entry points retained from the existing loader -->
        <property name="Premain-Class" value="example.coolmod.Agent"/>
        <property name="Agent-Class" value="example.coolmod.Agent"/>
        <property name="EarlyListener-Class" value="example.coolmod.EarlyEvents"/>

        <!-- Hot-swap opt-in. Without these, redefinition and retransformation are refused for this module. -->
        <property name="Can-Redefine-Classes" value="true"/>
        <property name="Can-Retransform-Classes" value="true"/>
    </properties>

    <entrypoint type="preLaunch" class="example.coolmod.Early"/>
    <entrypoint type="client" class="example.coolmod.ClientInit"/>

    <dependencies>
        <!-- Explicit dependencies still work. Top-level public FeatureCreep mods
             are additionally visible automatically unless that is disabled. -->
        <module name="some.shared.api" kind="optional" export="false" services="import"/>
    </dependencies>

    <provides>
        <service name="example.api.Service">
            <with-class name="example.coolmod.ServiceImpl"/>
        </service>
    </provides>
</module>
```

## Entrypoints

Entrypoints are direct children of `<module>`:

```xml
<entrypoint type="preLaunch" class="example.coolmod.Early"/>
<entrypoint type="main" class="example.coolmod.Main"/>
<entrypoint type="client" class="example.coolmod.ClientInit"/>
<entrypoint type="server" class="example.coolmod.ServerInit"/>
<entrypoint type="registry" class="example.coolmod.Registry" adapter="java"/>
```

| Attribute | Required | Meaning |
| --- | --- | --- |
| `type` | yes | `preLaunch`, `main`, `client`, `server`, or any custom name. |
| `class` | yes | Fully qualified class name inside the module. |
| `adapter` | no | Adapter that invokes the class. Default `java`. |

Several entrypoints of the same type are allowed. They run in document order.

`<main-class name="..."/>` remains valid. It is equivalent to
`<entrypoint type="main" class="..." adapter="java"/>`.

Phases run in this order:

1. `preLaunch`
2. `main`
3. `ready`: `client`, `server`, and custom entrypoints

Each phase fires an event on the `EventViewer` during `runMods()`: `fc:preLaunch`,
`fc:main`, and `fc:ready`. Only runnable modules (`FeatureCreep-Runnable`) run
entrypoints. A `client` or `server` entrypoint runs only when the module's
`FeatureCreep-Side` permits that side and the running side matches.

### The `java` adapter

The built-in `java` adapter calls a `public static void` method with no arguments,
named after the entrypoint type:

| Type | Method called |
| --- | --- |
| `preLaunch` | `public static void preLaunch()` |
| `client` | `public static void client()` |
| `server` | `public static void server()` |
| any custom type `T` | `public static void T()` |
| `main` | `main(String[])`, invoked through JBoss `Module.run` |

### Custom adapters

An adapter is a `ServiceLoader` provider of `featurecreep.loader.entrypoint.LanguageAdapter`:

```java
public final class KotlinAdapter implements LanguageAdapter {
    public String name() { return "kotlin"; }

    public void invoke(Entrypoint entrypoint, ClassLoader moduleClassLoader, String[] args)
            throws Exception {
        // Load entrypoint.className() from moduleClassLoader and call it.
    }
}
```

`Entrypoint` is a record with `type()`, `className()`, and `adapter()`. Register the
provider in `META-INF/services/featurecreep.loader.entrypoint.LanguageAdapter`. A
module selects it with `adapter="kotlin"`.

## FeatureCreep properties

| Property | Default | Meaning |
| --- | --- | --- |
| `FeatureCreep-Mod-Id` | module name / file name | Stable FeatureCreep module ID. |
| `FeatureCreep-Name` | — | Human-readable name. |
| `FeatureCreep-Description` | — | Description. |
| `FeatureCreep-Authors` | — | Author list/text. |
| `FeatureCreep-Website` | — | Project URL. |
| `FeatureCreep-License` | — | License identifier/text. |
| `FeatureCreep-Visibility` | `public` for top-level; private for nested | `public`, `isolated`, or `private`. Public modules are visible to other public mods. |
| `FeatureCreep-Auto-Dependencies` | `true` for public mods | Automatically adds other public FeatureCreep modules as optional dependencies. |
| `FeatureCreep-Host-Fallback` | `true` | Fall back to the real game/launcher classloader when JBoss resolution does not find a class/resource. |
| `FeatureCreep-Side` | `both` | `both`, `client`, or `server`. Gates client/server entrypoints and environment stripping. |
| `FeatureCreep-Runnable` | determined by source folder | Whether FeatureCreep runs the module's entrypoints. |
| `FeatureCreep-Nested-Jars` | `private` | `private`: nested JARs belong only to the owner; `public`: expose them as public modules; `declared`: load only paths listed below; `disabled`: do not load nested JARs. |
| `FeatureCreep-Nested-Jar-Paths` | — | Explicit nested JAR paths. |
| `MixinConfigs` | — | Sponge Mixin config resources. Honored only when the host supplies `MixinSupport`. `SpongeMixinConfig` and `FeatureCreep-Mixin-Configs` are accepted aliases. |
| `MappingEx-Files` | — | Comma-separated `.pdme` paths relative to the module root. Access widening only. See [Access widening](#access-widening). |
| `FeatureCreep-Transformer-Classes` | — | Transformer classes. Each may implement JBoss `ClassTransformer` or JVM `ClassFileTransformer`. |
| `FeatureCreep-Transformer-Scope` | `all` | `module` avoids JVM attach; `game`/`all` requests Instrumentation so game classes can be transformed. |
| `Premain-Class` | — | Comma-separated premain entry points. Not a hot-swap opt-in. |
| `Agent-Class` | — | Comma-separated agentmain entry points. Runs only for modules that opt into hot-swap. |
| `EarlyListener-Class` | — | FeatureCreep early event-listener entry points. |
| `Can-Redefine-Classes` | `false` | Opts this module into class redefinition. See [Hot-swap](#hot-swap). |
| `Can-Retransform-Classes` | `false` | Opts this module into class retransformation. See [Hot-swap](#hot-swap). |
| `Main-Class` | `<main-class>` | Manifest main-class. Used only when the JAR has no `module.xml`; when `module.xml` exists the manifest `Main-Class` is ignored. Prefer `<main-class>` or `<entrypoint type="main">`. |
| `Module-Version` | — | JBoss module version. |

Manifest attributes with these names continue to be imported for backwards
compatibility, but `module.xml` properties take precedence.

## Visibility model

For a normal public mod, resolution is intentionally Minecraft-friendly:

1. Namespaces owned by the host (JDK, Minecraft, launcher, FeatureCreep, Mixin,
   etc.) are excluded from FeatureCreep module resource roots so mods cannot
   accidentally define a second copy of them.
2. The module's own resources and its JBoss/private dependencies are resolved
   normally.
3. Other public FeatureCreep modules are available through automatic optional
   dependencies.
4. Anything still unresolved falls back to the actual game/launcher
   classloader. This is what exposes the real running Minecraft classes even
   when Knot/ModLauncher/NeoForge does not use the JVM system classloader.

Ordinary host libraries are *not* protected this way. That is deliberate: a
private nested Gson/Guava/etc. can resolve before the host fallback. Thus Mod A
may carry Guava 28 and Mod B Guava 33 without either private copy being
automatically exported to the other mod.

## Dependencies

Each `<module>` in `<dependencies>` has a `kind`:

```xml
<dependencies>
    <module name="shared.api" version-range="[1.2,2.0)"/>
    <module name="optional.addon" kind="optional" version="&gt;=3"/>
    <module name="nice.to.have" kind="recommends"/>
    <module name="tiny.helper" kind="suggests"/>
    <module name="bad.combo" kind="conflicts" version="&lt;2.0"/>
</dependencies>
```

| `kind` | Default | Missing or out of range | Effect on loading |
| --- | --- | --- | --- |
| `required` | yes | Fatal for this module. The dependent module is not loaded and the reason is logged. | Ordering edge. |
| `optional` | | Skipped silently. | Ordering edge when present. |
| `recommends` | | Skipped with a warning. | Ordering edge when present. Never blocks loading. |
| `suggests` | | Skipped with a warning. | Ordering edge when present. Never blocks loading. Behaves identically to `recommends`; kept separate so converted metadata keeps its meaning. |
| `conflicts` | | Nothing to report. | Never a dependency. Logs a warning when the named module is present and in range. Never blocks loading and creates no ordering edge. |

`optional="true"` is an older spelling of `kind="optional"` and is still accepted.

There is no `breaks` kind. A module cannot stop another module from loading
through metadata.

Load order is a topological sort over all present ordering edges. Cycles are
handled as follows:

- A cycle made only of `required` edges skips its members, with an error log.
  Other modules still load.
- A cycle that contains at least one non-`required` edge drops those edges, with
  a warning.

### Dependency version ranges

A `<module>` dependency may carry a range in its `version-range` attribute, or
the shorter `version` attribute. A dependency with no range is satisfied by any
version, including a missing one.

The range is checked against the dependency's `module.xml` `version` (or its
manifest `Module-Version`) before the dependency is resolved. A malformed range,
or a dependency that declares no version while a range is required, is a
mismatch.

A dependency that is not a FeatureCreep module discovered by this loader, such
as a host library, cannot be checked. It is allowed, with a diagnostic when
debug mode is on.

## Version ranges

Version ranges are parsed by `featurecreep.loader.FCVersionRange`. Versions are
compared segment by segment, numerically for digits. Missing trailing segments
count as `0`, so `1.20` equals `1.20.0`. A qualifier such as `beta` sorts before
the release, so `1.0-beta` is older than `1.0`.

| Range | Meaning |
| --- | --- |
| *(omitted)*, `*`, `any` | Any version, including a missing one. |
| `1.20.1`, `=1.20.1`, `==1.20.1` | Exactly this version. |
| `1.20.*`, `1.20.x` | Any version whose leading segments are `1.20`. |
| `>=1.20` | Greater than or equal to `1.20`. Also `>`, `<=`, `<`. |
| `>=1.20,<2.0` | Combined constraints. Commas or spaces both work. Every constraint must hold. |
| `[1.20,2.0)` | Maven-style interval. `[` and `]` are inclusive, `(` and `)` exclusive. |
| `[1.20,)`, `(,2.0]` | Open-ended lower or upper bound. |
| `[1.0]` | Exactly `1.0`. |
| `[1.0,2.0),[3.0,4.0)` | Union. Any interval may match. |
| `1.0 \|\| 2.0`, `>=1.0 <1.5 \|\| >=2.0` | Union of alternatives separated by `\|\|`. Each alternative is a comma- or space-separated set of constraints, and any alternative may match. |
| `~1.2.3` | `>=1.2.3 <1.3.0`. Patch-level changes only. |
| `~1.2` | `>=1.2 <1.3`. |
| `^1.2.3` | `>=1.2.3 <2.0.0`. Compatible within the major version. |
| `^0.2.3` | `>=0.2.3 <0.3.0`. The first non-zero segment is fixed. |
| `^0.0.3` | `>=0.0.3 <0.0.4`. |

The `~` and `^` operators follow npm semantics. Fabric's version predicates use
the same forms, so Fabric ranges convert without rewriting.

Malformed ranges are never treated as compatible. Examples include `[1.0,2.0`,
`[2.0,1.0]`, `!=1.0`, `>=1.*`, and a bare `~` or `^`.

**XML escaping.** `<` is not allowed inside an XML attribute value, so write
`<2.0` as `&lt;2.0`. Likewise `&` must be written `&amp;`. In `<property value="...">`
and `version-range="..."` alike:

```xml
<property name="FeatureCreep-Target-Game-Version" value="[1.20,1.22)"/>
<property name="FeatureCreep-Loader-Version" value="&gt;=12"/>
<module name="shared.api" version-range="&gt;=1.2,&lt;2.0"/>
```

### Target and loader requirements

These properties are checked during discovery, before JBoss Modules resolves the
module. A mismatch is reported on stderr and the module is skipped.

| Property | Compared with | Example |
| --- | --- | --- |
| `FeatureCreep-Target-Game` | `GameProvider.getGameId()`, case-insensitive | `minecraft` |
| `FeatureCreep-Target-Game-Version` | `GameProvider.getGameVersion()` | `[1.20,1.22)` |
| `FeatureCreep-Loader-Version` | `GameProvider.getLoaderVersion()` | `&gt;=12` |

## Access widening

`MappingEx-Files` lists one or more `.pdme` files in
[MappingExFormat](https://github.com/FeatureCreepEAP/MappingExFormat), relative to
the module root:

```xml
<property name="MappingEx-Files" value="META-INF/coolmod.pdme,META-INF/extra.pdme"/>
```

The loader reads these files during discovery and applies only the access rules:

- Visibility can be widened on classes, fields, and methods.
- `final` can be removed from classes, fields, and methods.
- A rule that would narrow visibility is logged and skipped.
- `Class`, `Var`, `Def`, `Param`, and `Include`/`Incluir` records are parsed but not
  applied. Runtime renaming is not implemented.
- Rules for `Def` and `Var` use `AccessFlag` rows with a member path. See
  [docs/MappingExAccessFlagExtension.md](docs/MappingExAccessFlagExtension.md).

Classes with no matching rule pass through the transformer unchanged.

## Environment

`@Environment(EnvType.CLIENT)` and `@Environment(EnvType.SERVER)` from
`net.fabricmc.api` are honored on methods and fields. `@EnvironmentInterface` is
honored on implemented interfaces. A member or interface that does not match the
running side is removed from the class at load time.

Classes without these annotations are not parsed by the environment stripper.

Limitation: a class-level `@Environment` is not enforced. A class marked for the
wrong side still loads, because removing a whole class is not done by the
stripper.

## Mixins

`MixinConfigs` names Sponge Mixin configuration resources. The loader does not
apply mixins itself. After discovery and before modules run, it passes each
module's configs to the host:

```java
GameProvider.getMixinSupport().registerMixinConfigs(moduleName, configs, moduleClassLoader);
```

The default `MixinSupport.NONE` logs once per module that declares configs and
ignores them. A host that applies mixins implements `MixinSupport` and returns it
from `GameProvider.getMixinSupport()`.

## Hot-swap

Class redefinition and retransformation are opt-in per module:

- `Can-Redefine-Classes=true` lets the module request class redefinition.
- `Can-Retransform-Classes=true` lets the module request class retransformation.

A redefinition or retransformation request for a module that did not opt in is
refused.

`Premain-Class` and `Agent-Class` do not opt a module in. `Agent-Class` runs only
for modules that opted in, during the `ready` phase.

`isHotswapNeeded()` is a separate question: it reports whether JVM
instrumentation is needed at all. It stays true for modules that declare
`Premain-Class` or `Agent-Class`, because the bootstrap uses it to decide whether
to install the agent. Per-module opt-in still gates redefinition.

`FCInstrumentation` delegates to a real `java.lang.instrument.Instrumentation`
when one is present. Capability checks (`isModifiableClass`, `isRetransformClassesSupported`,
and similar) report the delegate's answer, or `false` without one. Operations
that need a real Instrumentation throw `UnsupportedOperationException` when none
is available. They do not silently do nothing.

## Transformers

`FeatureCreep-Transformer-Classes` lists classes that implement JBoss
`ClassTransformer` or JVM `ClassFileTransformer`. Transformers run in
registration order. Each receives the bytes produced by the previous one. A
transformer that returns `null` leaves the bytes unchanged.

## Runtime APIs

### Config

`featurecreep.loader.config` is loader-neutral. It contains no Minecraft or
toolkit types. A module exposes configuration either by registering an
`FCConfigEntry` through `FCConfigApi`, or by declaring a `ServiceLoader` service:

```text
META-INF/services/featurecreep.loader.config.FCConfigProvider
```

The service class implements `FCConfigProvider.createConfigEntry()`. It runs once
per loaded module, after all modules load in `loadMods()`. Each entry is keyed by
its module ID. Duplicate IDs are ignored with a diagnostic.

```java
FCConfigEntry entry = FCConfigEntry.builder("example.coolmod")
        .displayName("Cool Mod")
        .description("Tunables for Cool Mod")
        .iconPath("assets/coolmod/icon.png")
        .screenFactory(parent -> new CoolModConfigScreen(parent))
        .build();
```

`FCConfigScreenFactory` receives an opaque parent and returns an opaque screen.
A host such as Minecraft supplies the concrete types. A factory that throws, or
returns `null`, is isolated. The caller then gets an empty `Optional`, and mod
loading continues.

Config values are stored in `./etc/<modId>.properties`. The directory is created
on the first write. Set the system property `featurecreep.config.dir` to use a
different directory. Writes go to a temporary file that is then moved over the
target, so a crash does not leave a partial file. Typed getters (boolean, int,
long) return a default when a key is missing or unparseable.

### ObjectShare

`featurecreep.loader.share.ObjectShare` is a process-wide key/value store for
passing objects between modules. Keys are namespaced as `modId:key`. Operations:
`put`, `get(key, Class<T>)` (type-checked), `putIfAbsent`, `remove`, and `keys`.

### ModQuery

`featurecreep.loader.query.ModQuery` answers questions about the running
environment:

| Method | Answer |
| --- | --- |
| `isModLoaded(id)` | Whether a module with that ID is loaded. |
| `getModContainer(id)` | ID, version, name, path, side, and dependencies, or empty. |
| `getGameDir()` | Game working directory. |
| `getConfigDir()` | Config directory (`./etc/` unless overridden). |
| `getEnvironmentType()` | Client or server, from `ExecutionSide`. |
| `isDevelopmentEnvironment()` | True when a module path is a directory, or when `featurecreep.dev` is set. |

## GameProvider additions

Providers may override:

- `getGameClassLoader()` — actual classloader defining the running game.
- `getHostClassLoaders()` — ordered host fallback loaders.
- `getGameLaunchPath()` — game JAR/classes launch path.
- `getGameWorkingDirectory()` — game working directory.
- `getJavaLaunchPath()` — Java executable path.
- `getLaunchArguments()` — current process launch arguments.
- `exposeModsToEachOtherByDefault()` — default public-mod compatibility policy.
- `getMixinSupport()` — host mixin hook. Default `MixinSupport.NONE`.

The Minecraft provider resolves `getGameClassLoader()` from real Minecraft
anchor classes instead of assuming the JVM system classloader.

Providers may also override these identity and configuration hooks. All have
backward-compatible defaults, so existing providers need no changes:

- `getGameId()` — game identifier such as `minecraft`; default `null` (unknown).
- `getGameVersion()` — running game version; default `null` (unknown).
- `getLoaderVersion()` — FeatureCreep loader version; default is the loader JAR's
  `Implementation-Version`, or `null`.
- `getConfigApi()` — loader-neutral `featurecreep.loader.config.FCConfigApi`;
  default is a registry-backed API that persists to `./etc/`.

An unknown (`null`) host value never causes a mismatch. It only skips that check.

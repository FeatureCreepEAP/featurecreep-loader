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

        <!-- One or more Mixin configs; comma, semicolon, or whitespace separated -->
        <property name="MixinConfigs"
                  value="coolmod.mixins.json,coolmod.client.mixins.json"/>

        <!-- Optional FeatureCreep/JVM transformer entry points -->
        <property name="FeatureCreep-Transformer-Classes"
                  value="example.coolmod.GameTransformer"/>
        <!-- module = JBoss-loaded classes only; game/all causes Instrumentation setup -->
        <property name="FeatureCreep-Transformer-Scope" value="all"/>

        <!-- Java-agent style entry points retained from the existing loader -->
        <property name="Premain-Class" value="example.coolmod.Agent"/>
        <property name="Agent-Class" value="example.coolmod.Agent"/>
        <property name="EarlyListener-Class" value="example.coolmod.EarlyEvents"/>
        <property name="Can-Redefine-Classes" value="true"/>
        <property name="Can-Retransform-Classes" value="true"/>
    </properties>

    <dependencies>
        <!-- Explicit dependencies still work. Top-level public FeatureCreep mods
             are additionally visible automatically unless that is disabled. -->
        <module name="some.shared.api" optional="true" export="false" services="import"/>
    </dependencies>

    <provides>
        <service name="example.api.Service">
            <with-class name="example.coolmod.ServiceImpl"/>
        </service>
    </provides>
</module>
```

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
| `FeatureCreep-Side` | `both` | `both`, `client`, or `server`. |
| `FeatureCreep-Runnable` | determined by source folder | Whether FeatureCreep calls the module main class. |
| `FeatureCreep-Nested-Jars` | `private` | `private`: nested JARs belong only to the owner; `public`: expose them as public modules; `declared`: load only paths listed below; `disabled`: do not load nested JARs. |
| `FeatureCreep-Nested-Jar-Paths` | — | Explicit nested JAR paths. |
| `MixinConfigs` | — | Preferred list of Sponge Mixin config resources. `SpongeMixinConfig` and `FeatureCreep-Mixin-Configs` are accepted aliases. |
| `FeatureCreep-Transformer-Classes` | — | Transformer classes. Each may implement JBoss `ClassTransformer` or JVM `ClassFileTransformer`. |
| `FeatureCreep-Transformer-Scope` | `all` | `module` avoids JVM attach; `game`/`all` requests Instrumentation so game classes can be transformed. |
| `Premain-Class` | — | Comma-separated premain entry points. |
| `Agent-Class` | — | Comma-separated agentmain entry points. |
| `EarlyListener-Class` | — | FeatureCreep early event-listener entry points. |
| `Can-Redefine-Classes` | `false` | Requests redefine support / agent initialization. |
| `Can-Retransform-Classes` | `false` | Requests retransform support / agent initialization. |
| `Main-Class` | `<main-class>` | Manifest-compatible main-class property. Prefer `<main-class>`. |
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

## GameProvider additions

Providers may override:

- `getGameClassLoader()` — actual classloader defining the running game.
- `getHostClassLoaders()` — ordered host fallback loaders.
- `getGameLaunchPath()` — game JAR/classes launch path.
- `getGameWorkingDirectory()` — game working directory.
- `getJavaLaunchPath()` — Java executable path.
- `getLaunchArguments()` — current process launch arguments.
- `exposeModsToEachOtherByDefault()` — default public-mod compatibility policy.

The Minecraft provider resolves `getGameClassLoader()` from real Minecraft
anchor classes instead of assuming the JVM system classloader.

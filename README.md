# featurecreep-loader

FeatureCreep is a JBoss Modules based mod loader. The current implementation is
`FCLoaderBasicR9`; the legacy Flatloader path has been removed.

The loader is built for Java 11+ and the R9 class-loading path is designed to run
on newer JDKs (including Java 25) without reflective access to JBoss Modules
internals. Normal mods can see the game, FeatureCreep API, and other public mods
by default, while nested dependencies are private unless explicitly exported.

Transformer, Java-agent/premain, Mixin metadata, and game-provider integration
are supported. See [MODULE_XML.md](MODULE_XML.md).


## Repository moved to GitHub

Due to the closure of Pagure.io (see https://discussion.fedoraproject.org/t/decommissioning-of-pagure-io-anticipated-by-flock-2026/181997), this project has been moved from Pagure to GitHub:

- Old: https://pagure.io/FeatureCreep/featurecreep-loader/  
- New: https://github.com/FeatureCreepEAP/featurecreep-loader/

## JBoss-only loader model

The legacy Flatloader backend has been removed. FeatureCreep now uses JBoss
Modules with compatibility-first defaults: normal mods see game classes and
other public FeatureCreep mods automatically, while nested JAR dependencies are
private by default. See [MODULE_XML.md](MODULE_XML.md) for the module.xml
properties, Mixin configuration, transformer entry points, dependency
visibility, and GameProvider launch/classloader APIs.

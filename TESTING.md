# FeatureCreep v12 tests

Each project now uses JUnit 5 and Maven Surefire.

Run locally in dependency order:

```bash
cd featurecreep-loader
mvn clean install

cd ../featurecreep-api
mvn clean install

cd ../featurecreep-bootstrap
mvn clean test
```

The loader suite includes an integration test that creates temporary FeatureCreep mod JARs at runtime and verifies:

- public FeatureCreep mods can resolve one another automatically;
- FeatureCreep mods can resolve host/game classes;
- nested dependencies are private by default;
- two mods can load different classes with the same dependency package/class name without sharing class identity;
- `module.xml` metadata, Mixin configuration properties, resource roots, services and dependency flags are parsed correctly.

Normal `package`, `test`, and `install` do not perform GPG signing. Central signing remains under `-Pcentral-release`.

# MappingEx AccessFlag member-path extension

The MappingExFormat (`.pdme`) spec defines `AccessFlag` rows only at class level. This loader
also accepts member-level `AccessFlag` rows so a mod can widen one field or method without
widening the whole class. The class-level form is unchanged.

## Row forms

Fields are separated by `¶`. The first line is a header and is ignored. Absent values are `nil`
or empty.

| Form | Type | Original | New | MethodName |
|------|------|----------|-----|------------|
| Class | `AccessFlag` | `net.Owner` | flag value | `nil` |
| Field | `AccessFlag` | `net.Owner.name:Desc` | flag value | `nil` |
| Method | `AccessFlag` | `net.Owner.name(Args)Ret` | flag value | `name` |

- `Original` for a class uses dots, and nested classes keep `$` (for example `net.Owner$Inner`).
- A field's `Desc` is its JVM field descriptor (`I`, `Ljava/lang/String;`).
- A method's `MethodName` must equal the `name` in `Original`. A mismatch is a load error.
- The flag value is decimal (`1`) or hexadecimal with a `0x` prefix (`0x0011`). Leading zeros
  do not mean octal, so `010` is ten. Values must be 0 to 0xFFFF.
- Spanish spellings `Incluir` and `BanderaDeAcceso` are accepted for `Include` and `AccessFlag`.

Example:

```
(header line, ignored)
AccessFlag¶net.example.Widget¶0x0001¶nil¶nil¶nil
AccessFlag¶net.example.Widget.count:I¶0x0001¶nil¶nil¶nil
AccessFlag¶net.example.Widget.refresh()V¶0x0001¶refresh¶nil¶nil
```

## Semantics

Access rules only widen. The transformer never lowers visibility.

- Visibility rank is private < package < protected < public. A rule whose target has a higher
  rank raises the member to that visibility. A rule with a lower rank is skipped, and the skip is
  logged once per member.
- `final` is cleared unless the rule's flag word includes `0x0010`. A rule of `0x0001` makes a
  `private final` field public and not final.
- `sealed` is not applied.
- A class header carries only public or package visibility. A top-level class rule of
  `public` raises the header. Protected and private rules do not change a top-level header.
- A nested class rule updates the class's own `InnerClasses` entry, which holds its real access.
  The class header is raised only when the rule is `public`.
- A class with no effective change is returned unmodified (`null` from the ClassTransformer), so
  the class is not rebuilt.
- A class that fails to parse is left unchanged and logged once.

## Relationship to the loader

`MappingExTransformer.fromFiles` reads one or more `.pdme` files and builds a `MappingRules`
index keyed by internal class name. It registers as a JBoss Modules `ClassTransformer`. Only
`AccessFlag` rows are indexed. Other record types are parsed and ignored.

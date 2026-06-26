# Development Guidance

## Library Versions and Documentation

Before writing or reviewing any code, always:

1. **Look up the current version** of every library in use — search online for the latest stable release. Do not assume the version already in `pom.xml` or `package.json` is current.
2. **Read the official documentation** for that exact version. APIs, configuration keys, and conventions change between releases; use the docs for the version you are actually on.
3. **Follow library conventions** as documented — preferred configuration style, recommended annotations, idiomatic patterns. Prefer the library's own abstractions over hand-rolled equivalents.
4. **Note any deprecations or removals** in the latest release and apply the recommended replacement immediately rather than deferring it.

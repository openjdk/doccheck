# doccheck

https://openjdk.org/projects/code-tools/doccheck/

See the main [documentation](src/doc/doccheck.md).

## Building `doccheck`

The preferred way to build is with GNU `make`. JDKHOME needs to be set to JDK 17 or later.

```
$ make -C make JDKHOME=/path/to/JDK
```

There is an Ant `build.xml` file; it may be removed in a future update.
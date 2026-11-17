# Meepo Hero Template (pro.fessional.meepo)

Implementation guides (Chinese): [language specification](doc/syntax.zh.md), [names and execution flow](doc/naming.zh.md), [documentation index](doc/README.zh.md).

Charsets are caller-controlled byte I/O parameters, not template syntax. Use `Meepo.parse(file, inputCharset)`
to decode source bytes and `gene.merge(model, outputStream, outputCharset)` to encode output; both default to UTF-8.
The same character-based Gene can render with different output encodings. Output uses `merge` overloads
for Writer or OutputStream; String and Writer APIs retain their character-based contracts.

![Maven Central](https://img.shields.io/maven-central/v/pro.fessional/meepo?color=00DD00)
[![CI](https://github.com/fessionalpro/professional-meepo/actions/workflows/ci.yml/badge.svg)](https://github.com/fessionalpro/professional-meepo/actions/workflows/ci.yml)

> English 🇺🇸 | [中文 🇨🇳](readme.zh.md)

Meepo, Five are stronger than one. Divided they stand, united they fall.

a non-professional template engine that does not break the `master` syntax,
and is based on the comments and markup of the `master` syntax.

![meepo](meepo_full.png)

The syntax of most modern template engines will break the syntax 
of the target file when previewing and editing.

Meepo solves this problem with high performance like C's macro by 
using only static transformations and limited dynamic control.

* Generate `*.java` from `java`, both are compilable.
* Generate `*.sql` from `sql`, both are executable.
* Generate `*.htm` from `htm`, both are previewable.
* `placeholder` supports customizing variable's boundaries and escaping
* flexible and efficient customizing (dynamic, static, runtime) function using java

The target release is **2.0.0**, with development builds defaulting to **2.0.0-SNAPSHOT**.
See [Maven Central publishing](doc/release.zh.md) for release configuration and setup.

## Build and publish

Use Maven Wrapper to run the repository's pinned Maven version. Select Java 8 with mise or your preferred JDK manager.

```sh
./mvnw -B -ntp verify
./mvnw -B -ntp -Pcoverage verify
```

On Windows, use `mvnw.cmd`. Maven Wrapper configuration is in `.mvn/wrapper/maven-wrapper.properties`.
Development conventions are maintained in [Chinese](doc/AGENTS.zh.md).

GitHub Releases publish the matching formal version to Maven Central. Successful `develop` push builds publish a
POM-derived SNAPSHOT, currently `2.0.0-SNAPSHOT`. Private Maven publishing supports release and SNAPSHOT repositories.
Setup, credentials and dry runs are described in the [publishing guide](doc/release.zh.md).

## How to use

① DIY `clone` and `install` is powerful.

② Using Maven Central is stable.

```xml
<dependency>
    <groupId>pro.fessional</groupId>
    <artifactId>meepo</artifactId>
    <version>2.0.0</version>
</dependency>
```

③ Using SNAPSHOT is the latest.

```xml
<repository>
    <id>central-snapshots</id>
    <name>Central Portal Snapshots</name>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

## Standalone use

`Meepo` provides common methods and caching 
that can handle the most common scenarios.

* File template - use `Meepo` as entry, `#parse`, `#merge`
* Placeholder template - use `Holder` as entry, `#piece`
* String Parsing - use `Parser` to construct `Gene`, `#parse`

you can customize the use of `Parser` and `Gene` to combine the tool as you need.

## Mvc Integration

`spring-mvc` module integrates with `SpringMvc`, 
can be used independently or pre-processed with other templates.

```xml
<dependency>
    <groupId>pro.fessional.meepo</groupId>
    <artifactId>spring-mvc</artifactId>
    <version>${meepo-spring.version}</version>
</dependency>
```

## Spel Integration

`spring-spel` module registers the `spel` engine, provide spring `Bean` and `SpEL`.

```xml
<dependency>
    <groupId>pro.fessional.meepo</groupId>
    <artifactId>spring-spel</artifactId>
    <version>${meepo-spring.version}</version>
</dependency>
```

## Detailed Documents

* <https://wings.fessional.pro/b-meepo/>
* <https://github.com/fessionalpro/wings-doc/tree/main/src/b-meepo/>
* <https://gitee.com/fessionalpro/wings-doc/tree/main/src/b-meepo/>

# meepo changelog

<https://keepachangelog.com>

## [Unreleased]

The target release is `2.0.0`.

### Added

- Repository AI development conventions and practical configuration and email templates.
- Maven Central Portal publishing with signed releases, automatic develop SNAPSHOTs, local private repository publishing and Java 8/11/17/21 CI verification.
- Maven Wrapper with a pinned Maven 3.9.16 distribution and SHA-256 verification.
- Caller-supplied charset overloads for byte template inputs and outputs, defaulting to UTF-8.
- EACH traversal of finite Iterator, non-Collection Iterable and BaseStream inputs, including primitive streams.
- Cursor inputs are collected into ArrayList with null placeholders for skipped positions, sharing exact size, integer counts and forward/reverse traversal with collections.

### Changed

- Repository links now use the fessionalpro organization. Chinese Markdown uses the `.zh.md` suffix, with development conventions in `doc/AGENTS.zh.md` and an English root entry.
- Templates remain character-based; input charset exists only during decoding and Parser resource loading, independently of output encoding.
- Writer merging uses the original two-argument entry points. Ubuf selects cached bytes using its own output charset.
- Ubuf encodes character arrays and String slices through CharBuffer views and a reusable CharsetEncoder buffer.
- Utxt.getBytes(Charset) defaults to uncached caller-owned output. The boolean overload enables its latest soft encoding cache, selected explicitly by Ubuf.canCache. Encoding reads the original char array through Charset.encode and a CharBuffer view. Ubuf selects reusable encodings and handles output boundaries.
- Fixed node text fields use Utxt directly, with CharSequence access and read-only subsequences. The protected Exon.text9 and Tock.tock9 field types change from char[] to Utxt, and public String constructors remain available. Redundant protected char[] constructors in Exon, Tick, Tock and RnaDone are removed in favor of Utxt-based construction and sharing.
- Byte-output caches retain only the latest encoding and its charset in a soft reference, with atomic snapshot publication.
- Byte output uses `Gene.merge` overloads distinguished by OutputStream and Charset parameters; original main-branch signatures remain intact.
- `Meepo.steam` supports explicit output charset; Gene has no array- or InputStream-returning convenience methods.
- `Utxt` and `Ubuf` now mean user-charset text and output buffer; continuous encoders preserve BOM and stateful encoding behavior.
- InputStream output takes ownership of a completed render buffer, avoiding a final byte-array copy.
- Internal parsers transfer completed node lists to Gene; Cmap reuses its owned overlay when materializing defaults.
- Cursor EACH consumes its input before rendering. Callers retain responsibility for closing streams.

## [1.5.0] - 2024-01-01

bump version, ready for stable channel.

### Changed

- deprecated JsEngine.
- RnaManager NOT register JsEngine

## [1.4.1] - 1.4.14 snapshot

### Added

- Support literal number `BigDecimal`, `Double`, `Float`, `Long`, `Integer`
- Added `first` and `last` properties to `EACH`
- Added `fun:abs` function
- Support Boolean type `TRUE`/`FALSE`

## [1.4.0] - 2021-01-06

### Added

- Rich built-in functions, see docs for details
- Support for `placeholder template`, merging with map engine

### Changed

- Moved `JavaEval` to eval package
- MapEngine supports functions without a prefix

## [1.0.0] - 2020-11-24

### Added

- Static replacement and dynamic construction, flow, loops
- Multi-paragraph indentation function
- `java`, `js`, `exec` engines
- Memory, Thread and Performance Optimization

## [master]

[master]: https://github.com/fessionalpro/professional-meepo
[1.0.0]: https://github.com/fessionalpro/professional-meepo/releases/tag/1.0.0
[1.4.0]: https://github.com/fessionalpro/professional-meepo/releases/tag/1.4.0
[1.5.0]: https://github.com/fessionalpro/professional-meepo/releases/tag/1.5.0

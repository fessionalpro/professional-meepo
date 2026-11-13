/**
 * DNA directives: static transformations performed while parsing a template.
 * SET replaces matched text, RAW unwraps literal content, SON includes a child
 * template, and BKB/END manage immunity and named replacement lifetimes.
 * Resulting literal text can be reused across merges without model lookup.
 */
package pro.fessional.meepo.bind.dna;

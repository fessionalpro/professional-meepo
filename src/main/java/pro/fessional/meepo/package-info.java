/**
 * Template parsing and rendering with target-language comment syntax.
 * <p>The biological names describe the processing model: DNA supplies static,
 * parse-time transformations; RNA supplies runtime expression evaluation and
 * control flow. A {@link pro.fessional.meepo.sack.Gene} is the parsed template,
 * its {@link pro.fessional.meepo.bind.Exon} fragments are the retained pieces,
 * and an {@link pro.fessional.meepo.sack.Acid} supplies one merge's environment.
 * These are software metaphors, not a simulation of biological processes.</p>
 * <p>Meepo is the Dota hero: reusing a template to produce many results borrows
 * the clone theme of Divided We Stand. Package names also follow the hero:
 * bind from Earthbind, poof from Poof, sack from Ransack, and pack from Pack Rat.
 * BKB is Black King Bar, used here for regions immune to template processing.
 * Directive delimiters follow the substitution style of vi.</p>
 * <p>Read the implementation in this order: Meepo selects or caches a template;
 * Parser or Holder builds a Gene and warms expressions; Gene creates an Acid;
 * exons write characters or UTF-8 through the pack package.</p>
 * <dl>
 * <dt>DNA / RNA / Txt</dt><dd>Parse-time directives / runtime directives / output fragments.</dd>
 * <dt>Life / Tick / Tock</dt><dd>Match lifetime / parse-time matching / named runtime groups.</dd>
 * <dt>Clop</dt><dd>Closed-open source interval, [start, until).</dd>
 * <dt>Prc / Rng / Bar</dt><dd>Text processor / runtime node marker / indentation marker.</dd>
 * <dt>Suffix 9</dt><dd>Cached characters, held by Utxt in template nodes. Suffixes 0/1 denote inclusive/exclusive offsets.</dd>
 * </dl>
 */
package pro.fessional.meepo;

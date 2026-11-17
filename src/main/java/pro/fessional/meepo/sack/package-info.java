/**
 * Template assembly and execution state; sack is shortened from Meepo's Ransack.
 * <p>Parser handles comment directives, Holder handles placeholders, Gene stores
 * the resulting exons, and Acid owns one merge's mutable environment. Cmap is
 * the context map that overlays copied caller bindings on function defaults.</p>
 */
package pro.fessional.meepo.sack;

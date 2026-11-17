/**
 * Source fragments (exons), directive nodes and the metadata that binds them.
 * The package name borrows the bind in Meepo's Earthbind.
 * <p>DNA nodes perform parse-time transformations. RNA nodes retain dynamic work,
 * and Txt nodes write the resulting literal or evaluated text. A single directive
 * can create multiple output nodes when it matches multiple source positions.</p>
 */
package pro.fessional.meepo.bind;

/**
 * Output buffers and reusable encoded text for template merging.
 * <p>Pack borrows Meepo's Pack Rat name and the ordinary meaning of packing:
 * collect template fragments and assemble their character or encoded output.</p>
 * <p>Cbuf means character buffer, Utxt means user-charset text, and Ubuf means user-charset
 * output buffer. Cbuf and Ubuf belong to one render and one thread; Utxt owns
 * fixed template text and may share its lazily encoded bytes between renders.
 * This package handles output representation; merge context remains in sack.</p>
 */
package pro.fessional.meepo.pack;

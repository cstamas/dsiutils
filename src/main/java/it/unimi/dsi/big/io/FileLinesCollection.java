/*
 * DSI utilities
 *
 * Copyright (C) 2005-2023 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

package it.unimi.dsi.big.io;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesMutableStringIterable;
import it.unimi.dsi.io.SafelyCloseable;
import it.unimi.dsi.lang.MutableString;

/**
 * A wrapper exhibiting the lines of a file as a {@link java.util.Collection}.
 *
 * <P>
 * <strong>Warning</strong>: the lines returned by iterators generated by instances of this class
 * <em>are not cacheable</em>. The returned value is a {@link MutableString}
 * instance that is reused at each call, and that is <em>modified by a call to
 * {@link Iterator#hasNext() hasNext()}</em>. Thus, for instance,
 *
 * <pre>
 * ObjectIterators.unwrap(fileLinesColletion.iterator());
 * </pre>
 *
 * will not give the expected results. Use {@link #allLines()} to get the {@linkplain ObjectBigList
 * big list} of all lines (again, under the form of compact
 * {@link MutableString}s). Note also that {@link #toString()} will return a
 * single string containing all file lines separated by the string associated with the system
 * property <code>line.separator</code>.
 *
 * <P>
 * An instance of this class allows to access the lines of a file as a {@link java.util.Collection}.
 * Using {@linkplain java.util.Collection#contains(Object) direct access} is strongly
 * discouraged (it will require a full scan of the file), but the {@link #iterator()} can be
 * fruitfully used to scan the file, and can be called any number of times, as it opens an
 * independent input stream at each call. For the same reason, the returned iterator type
 * ({@link it.unimi.dsi.io.FileLinesCollection.FileLinesIterator}) is {@link Closeable}, and
 * should be closed after usage.
 *
 * <p>
 * Using a suitable {@linkplain #FileLinesCollection(CharSequence, String, boolean) constructor}, it
 * is possible to specify that the file is compressed in <code>gzip</code> format (in this case, it
 * will be opened using a {@link GZIPInputStream}).
 *
 * <P>
 * Note that the first call to {@link #size64()} will require a full file scan.
 *
 * @author Sebastiano Vigna
 * @since 2.0
 * @deprecated Please use {@link FileLinesMutableStringIterable} instead; the {@code zipped} option of this class
 *             can be simulated by passing a {@link GZIPInputStream} as decompressor.
 */
@Deprecated
public class FileLinesCollection extends AbstractCollection<MutableString> implements Size64 {
	/** The filename upon which this file-lines collection is based. */
	private final String filename;
	/** The encoding of {@link #filename}, or {@code null} for the standard platform encoding. */
	private final String encoding;
	/** The cached size of the collection. */
	private long size = -1;
	/** Whether {@link #filename} is zipped. */
	private final boolean zipped;

	/** Creates a file-lines collection for the specified filename with the specified encoding.
	 *
	 * @param filename a filename.
	 * @param encoding an encoding.
	 */
	public FileLinesCollection(final CharSequence filename, final String encoding) {
		this(filename, encoding, false);
	}

	/** Creates a file-lines collection for the specified filename with the specified encoding, optionally assuming
	 * that the file is compressed using <code>gzip</code> format.
	 *
	 * @param filename a filename.
	 * @param encoding an encoding.
	 * @param zipped whether <code>filename</code> is zipped.
	 */
	public FileLinesCollection(final CharSequence filename, final String encoding, final boolean zipped) {
		this.zipped = zipped;
		this.filename = filename.toString();
		this.encoding = encoding;
	}


	/**
	 * An iterator over the lines of a {@link FileLinesCollection}.
	 *
	 * <p>
	 * Instances of this class open an {@link java.io.InputStream}, and thus should be
	 * {@linkplain Closeable#close() closed} after usage. A &ldquo;safety-net&rdquo; finaliser tries to
	 * take care of the cases in which closing an instance is impossible. An exhausted iterator,
	 * however, will be closed automagically.
	 *
	 * @deprecated Please use
	 *             {@link FileLinesMutableStringIterable#iterator(java.io.InputStream, java.nio.charset.Charset, Class)};
	 *             the {@code zipped} option of this class can be simulated by passing a
	 *             {@link GZIPInputStream} as decompressor.
	 */

	@Deprecated
	public static final class FileLinesIterator implements Iterator<MutableString>, SafelyCloseable {
		private FastBufferedReader fbr;
		MutableString s = new MutableString(), next;

		boolean toAdvance = true;

		private FileLinesIterator(final String filename, final String encoding, final boolean zipped) {
			try {
				fbr = encoding != null
					? new FastBufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(new FileInputStream(filename)) : new FileInputStream(filename), encoding))
					: new FastBufferedReader(new FileReader(filename));
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasNext() {
			if (toAdvance) {
				try {
					next = fbr.readLine(s);
					if (next == null) close();
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
				toAdvance = false;
			}

			return next != null;
		}

		@Override
		public MutableString next() {
			if (! hasNext()) throw new NoSuchElementException();
			toAdvance = true;
			return s;
		}

		@Override
		public synchronized void close() {
			if (fbr == null) return;
			try {
				fbr.close();
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
			finally {
				fbr = null;
			}
		}

		@Override
		protected synchronized void finalize() throws Throwable {
			try {
				if (fbr != null) close();
			}
			finally {
				super.finalize();
			}
		}

	}

	@Override
	public FileLinesIterator iterator() {
		return new FileLinesIterator(filename, encoding, zipped);
	}

	@Override
	@Deprecated
	public synchronized int size() {
		return (int)Math.min(Integer.MAX_VALUE, size);
	}

	@Override
	public synchronized long size64() {
		if (size == -1) {
			final FileLinesIterator i = iterator();
			size = 0;
			while(i.hasNext()) {
				size++;
				i.next();
			}
			i.close();
		}
		return size;
	}

	/** Returns all lines of the file wrapped by this file-lines collection.
	 *
	 * @return all lines of the file wrapped by this file-lines collection.
	 */

	public ObjectBigList<MutableString> allLines() {
		final ObjectBigList<MutableString> result = new ObjectBigArrayBigList<>();
		for(final Iterator<MutableString> i = iterator(); i.hasNext();) result.add(i.next().copy());
		return result;
	}

	@Override
	@Deprecated
	public Object[] toArray() {
		throw new UnsupportedOperationException("Use allLines()");
	}

	@Override
	@Deprecated
	public <T> T[] toArray(final T[] a) {
		throw new UnsupportedOperationException("Use allLines()");
	}

	@Override
	public String toString() {
		final MutableString separator = new MutableString(System.getProperty("line.separator"));
		final MutableString s = new MutableString();
		for(final MutableString l: this) s.append(l).append(separator);
		return s.toString();
	}
}
/*
 * Copyright 2012-2020 ISP RAS (http://www.ispras.ru)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package ru.ispras.microtesk.mmu.model.sim;

import ru.ispras.fortress.data.types.bitvector.BitVector;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.fortress.util.Pair;
import ru.ispras.microtesk.utils.SparseArray;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@link Cache} represents an abstract partially associative cache memory.
 *
 * A cache unit is characterized by the following parameters (except the entry and address types):
 * <ol>
 * <li>{@code length} - the number of sets in the cache,
 * <li>{@code associativity} - the number of lines in each set,
 * <li>{@code policyId} - the entry replacement policy,
 * <li>{@code indexer} - the set indexer, and
 * <li>{@code matcher} - the line matcher.
 * </ol>
 *
 * @param <E> the entry type.
 * @param <A> the address type.
 *
 * @author <a href="mailto:andrewt@ispras.ru">Andrei Tatarnikov</a>
 */
public abstract class Cache<E extends Struct<?>, A extends Address<?>> extends Buffer<E, A> {

  /** Table of associative sets. */
  private SparseArray<CacheSet<E, A>> sets;
  private SparseArray<CacheSet<E, A>> savedSets;

  protected final int associativity;
  protected final CachePolicy policy;
  protected final Indexer<A> indexer;
  protected final Matcher<E, A> matcher;
  protected final Buffer<? extends Struct<?>, A> next;
  protected final Collection<Cache<? extends Struct<?>, A>> previous = new ArrayList<>();

  /**
   * Proxy class is used to simplify code of assignment expressions.
   */
  public final class Proxy {
    private final A address;

    private Proxy(final A address) {
      this.address = address;
    }

    public void assign(final E entry) {
      writeEntry(address, entry);
    }

    public void assign(final BitVector value) {
      writeEntry(address, value);
    }
  }

  /**
   * Constructs a buffer of the given length and associativity.
   *
   * @param entryCreator the entry creator.
   * @param addressCreator the address creator.
   * @param length the number of sets in the buffer.
   * @param associativity the number of lines in each set.
   * @param policy the cache policy.
   * @param indexer the set indexer.
   * @param matcher the line matcher.
   * @param next the next-level cache.
   */
  public Cache(
      final Struct<E> entryCreator,
      final Address<A> addressCreator,
      final BigInteger length,
      final int associativity,
      final CachePolicy policy,
      final Indexer<A> indexer,
      final Matcher<E, A> matcher,
      final Buffer<? extends Struct<?>, A> next) {
    super(entryCreator, addressCreator);

    InvariantChecks.checkNotNull(length);
    InvariantChecks.checkGreaterThanZero(associativity);
    InvariantChecks.checkNotNull(policy);
    InvariantChecks.checkNotNull(indexer);
    InvariantChecks.checkNotNull(matcher);

    this.sets = new SparseArray<>(length);
    this.savedSets = null;
    this.associativity = associativity;
    this.policy = policy;
    this.indexer = indexer;
    this.matcher = matcher;
    this.next = next;

    // The next buffer is allowed to be the main memory.
    if (next != null && next instanceof Cache) {
      final Cache<? extends Struct<?>, A> nextCache = (Cache<? extends Struct<?>, A>) next;
      nextCache.previous.add(this);
    }
  }

  protected final CacheSet<E, A> getSet(final BitVector index) {
    CacheSet<E, A> set = sets.get(index);

    if (null == set) {
      set = new CacheSet<>(
          entryCreator,
          addressCreator,
          associativity,
          policy,
          matcher,
          this,
          next
      );
      sets.set(index, set);
    }

    return set;
  }

  protected final void setSet(final BitVector index, final CacheSet<E, A> set) {
    sets.set(index, set);
  }

  @Override
  public final boolean isHit(final A address) {
    final BitVector index = indexer.getIndex(address);
    final CacheSet<E, A> set = sets.get(index);
    return null != set && set.isHit(address);
  }

  @Override
  public final E readEntry(final A address) {
    final BitVector index = indexer.getIndex(address);
    final CacheSet<E, A> set = getSet(index);
    return set.readEntry(address);
  }

  @Override
  public final void writeEntry(final A address, final BitVector entry) {
    final BitVector index = indexer.getIndex(address);
    final CacheSet<E, A> set = getSet(index);
    set.writeEntry(address, entry);
  }

  @Override
  public final void evictEntry(final A address) {
    final BitVector index = indexer.getIndex(address);
    final CacheSet<E, A> set = getSet(index);
    set.evictEntry(address);
  }

  public final Proxy writeEntry(final A address) {
    return new Proxy(address);
  }

  @Override
  public final Pair<BitVector, BitVector> seeEntry(final BitVector index, final BitVector way) {
    final CacheSet<E, A> set = sets.get(index);
    return set != null ? set.seeEntry(index, way) : null;
  }

  @Override
  public final String toString() {
    return String.format("%s %s", getClass().getSimpleName(), sets);
  }

  @Override
  public void setUseTempState(final boolean value) {
    final boolean isTempStateUsed = savedSets != null;
    if (value == isTempStateUsed) {
      return;
    }

    if (value) {
      savedSets = sets;
      sets = new SparseArray<>(sets.length()); // TODO: NEED A FULL COPY HERE
    } else {
      sets = savedSets;
      savedSets = null;
    }
  }

  @Override
  public void resetState() {
    sets = new SparseArray<>(sets.length());
    savedSets = null;
  }
}

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

/**
 * {@link CacheLine} represents an abstract cache line.
 *
 * @param <E> the entry type.
 * @param <A> the address type.
 *
 * @author <a href="mailto:andrewt@ispras.ru">Andrei Tatarnikov</a>
 */
public class CacheLine<E extends Struct<?>, A extends Address<?>>
    implements Buffer<E, A>, Snoopable<E, A> {

  /** Coherence protocol. */
  private final CoherenceProtocol protocol;
  /** Line matcher. */
  private final Matcher<E, A> matcher;
  /** Cache that contains this line. */
  private final CacheUnit<E, A> cache;

  /** Stored entry. */
  private E entry;
  /** Entry address. */
  private A address;

  /** Dirty bit used to implement the write-back policy. */
  private boolean dirty;
  /** Coherence protocol state. */
  private Enum<?> state;

  /**
   * Constructs an invalid cache line.
   *
   * @param policy the cache policy.
   * @param matcher the entry-address matcher.
   * @param cache the current cache.
   */
  public CacheLine(
      final CachePolicy policy,
      final Matcher<E, A> matcher,
      final CacheUnit<E, A> cache) {
    InvariantChecks.checkNotNull(policy);
    InvariantChecks.checkNotNull(matcher);
    InvariantChecks.checkNotNull(cache);

    this.matcher = matcher;
    this.cache = cache;

    this.protocol = policy.coherence.newProtocol();
    this.state = this.protocol.onReset();

    this.entry = null;
    this.address = null;
    this.dirty = false;
  }

  public final boolean isValid() {
    return entry != null && state != protocol.onReset();
  }

  public final E getEntry() {
    return entry;
  }

  public final void setEntry(final BitVector entry) {
    this.entry.asBitVector().assign(entry);
  }

  public final A getAddress() {
    return address;
  }

  public final void setAddress(final A address) {
    this.address = address;
  }

  public final boolean isDirty() {
    return dirty;
  }

  public final void setDirty(final boolean dirty) {
    this.dirty = dirty;
  }

  @Override
  public boolean isHit(final A address) {
    // After allocation, when the entry is invalid but not null, the method may return true.
    return entry != null ? matcher.areMatching(entry, address) : false;
  }

  @Override
  public E readEntry(final A address) {
    // Entry should be allocated.
    InvariantChecks.checkNotNull(isHit(address));

    final Struct<?> snoopEntry = cache.sendSnoopRead(address, isValid());
    InvariantChecks.checkTrue(isValid() || snoopEntry != null);

    if (!isValid()) {
      entry.asBitVector().assign(snoopEntry.asBitVector());
    }

    state = protocol.onRead(state);
    return entry;
  }

  @Override
  public void writeEntry(final A address, final BitVector newEntry) {
    // Entry should be allocated.
    InvariantChecks.checkNotNull(isHit(address));

    final Struct<?> snoopEntry = cache.sendSnoopWrite(address, newEntry, isValid());
    InvariantChecks.checkTrue(isValid() || snoopEntry != null);

    if (!isValid()) {
      entry.asBitVector().assign(snoopEntry.asBitVector());
    }

    // TODO: Implement partial assignment.
    entry.asBitVector().assign(newEntry);
    state = protocol.onWrite(state);
  }

  @Override
  public void allocEntry(final A address) {
    this.entry = cache.newEntry(address);
    this.address = address;

    state = protocol.onReset();
  }

  @Override
  public void evictEntry(final A address) {
    // Entry should be allocated and valid.
    InvariantChecks.checkNotNull(isHit(address) && isValid());

    cache.sendSnoopEvict(address, entry.asBitVector(), dirty);
    entry = null;

    state = protocol.onReset();
  }

  @Override
  public void resetState() {
    entry = null;
    address = null;
    dirty = false;
    state = protocol.onReset();
  }

  @Override
  public final E snoopRead(final A address) {
    final E result = isHit(address) ? entry : null;
    state = protocol.onSnoopRead(state);
    return result;
  }

  @Override
  public final E snoopWrite(final A address, final BitVector newEntry) {
    final E result = isHit(address) ? entry : null;
    state = protocol.onSnoopWrite(state);
    return result;
  }

  @Override
  public final void snoopEvict(final A address, final BitVector oldEntry) {
    state = protocol.onSnoopEvict(state);
  }

  @Override
  public String toString() {
    return String.format("Line [entry=%s]", entry);
  }
}

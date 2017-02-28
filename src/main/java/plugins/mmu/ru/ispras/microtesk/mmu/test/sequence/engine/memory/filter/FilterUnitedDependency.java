/*
 * Copyright 2015-2017 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.mmu.test.sequence.engine.memory.filter;

import java.util.Collection;
import java.util.Map;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.BufferUnitedDependency;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.BufferUnitedHazard;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryAccess;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBufferAccess;
import ru.ispras.microtesk.utils.function.BiPredicate;

/**
 * {@link FilterUnitedDependency} composes device-level filters into an access-level filter.
 * 
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class FilterUnitedDependency
    implements BiPredicate<MemoryAccess, BufferUnitedDependency> {

  /** The device-level filters to be composed. */
  private final Collection<BiPredicate<MemoryAccess, BufferUnitedHazard>> filters;

  /**
   * Constructs an execution-level filter from the collection of device-level filters.
   * 
   * @param filters the collection of device-level filters to be composed.
   * @throws IllegalArgumentException if {@code filters} is {@code null}.
   */
  public FilterUnitedDependency(
      final Collection<BiPredicate<MemoryAccess, BufferUnitedHazard>> filters) {
    InvariantChecks.checkNotNull(filters);
    this.filters = filters;
  }

  @Override
  public boolean test(final MemoryAccess access, final BufferUnitedDependency dependency) {
    final Map<MmuBufferAccess, BufferUnitedHazard> hazards = dependency.getBufferHazards();

    for (final BufferUnitedHazard hazard : hazards.values()) {
      for (final BiPredicate<MemoryAccess, BufferUnitedHazard> filter : filters) {
        if (!filter.test(access, hazard)) {
          return false;
        }
      }
    }

    return true;
  }
}

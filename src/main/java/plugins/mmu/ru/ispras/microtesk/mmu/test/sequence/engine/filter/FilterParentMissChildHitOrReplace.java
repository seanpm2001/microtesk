/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.mmu.test.sequence.engine.filter;

import ru.ispras.microtesk.mmu.translator.coverage.MemoryAccess;
import ru.ispras.microtesk.mmu.translator.coverage.MemoryHazard;
import ru.ispras.microtesk.mmu.translator.coverage.MemoryUnitedHazard;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuDevice;
import ru.ispras.microtesk.mmu.translator.ir.spec.basis.BufferAccessEvent;
import ru.ispras.microtesk.utils.function.BiPredicate;

/**
 * Filters off test templates, where there is a hit or a replace in a child device (e.g. DTLB) and
 * a miss in the parent device (e.g. JTLB).
 * 
 * <p>NOTE: Such test templates are unsatisfiable.</p>
 * 
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class FilterParentMissChildHitOrReplace implements BiPredicate<MemoryAccess, MemoryUnitedHazard> {
  @Override
  public boolean test(final MemoryAccess execution, final MemoryUnitedHazard hazard) {
    final MmuDevice view = hazard.getDevice();

    if (view != null && view.isView()) {
      final MmuDevice parent = view.getParent();

      final boolean viewAccess = execution.getEvent(view) == BufferAccessEvent.HIT ||
          !hazard.getRelation(MemoryHazard.Type.TAG_REPLACED).isEmpty();

      if (execution.getEvent(parent) == BufferAccessEvent.MISS && viewAccess) {
        // Filter off.
        return false;
      }
    }

    return true;
  }
}


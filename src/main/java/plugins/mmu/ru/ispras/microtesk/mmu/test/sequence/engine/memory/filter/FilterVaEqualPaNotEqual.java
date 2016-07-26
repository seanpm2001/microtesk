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

package ru.ispras.microtesk.mmu.test.sequence.engine.memory.filter;

import java.util.Map;
import java.util.Set;

import ru.ispras.microtesk.mmu.MmuPlugin;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryAccess;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryHazard;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryUnitedDependency;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryUnitedHazard;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAddressInstance;
import ru.ispras.microtesk.utils.function.BiPredicate;

/**
 * Filters off test templates, where {@code ADDR_EQUAL} is set for the virtual address and
 * {@code ADDR_NOT_EQUAL} is set for the physical address.
 * 
 * <p>NOTE: Such test templates are unsatisfiable.</p>
 * 
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class FilterVaEqualPaNotEqual implements BiPredicate<MemoryAccess, MemoryUnitedDependency> {
  @Override
  public boolean test(final MemoryAccess access, final MemoryUnitedDependency dependency) {
    final MmuAddressInstance va = MmuPlugin.getSpecification().getVirtualAddress();
    final MemoryUnitedHazard vaHazard = dependency.getHazard(va);

    final Set<Integer> vaEqualRelation =
        vaHazard != null ? vaHazard.getRelation(MemoryHazard.Type.ADDR_EQUAL) : null;

    if (vaEqualRelation == null) {
      return true;
    }

    for (final Map.Entry<MmuAddressInstance, MemoryUnitedHazard> addrEntry : dependency.getAddrHazards().entrySet()) {
      final MmuAddressInstance pa = addrEntry.getKey();
      final MemoryUnitedHazard paHazard = addrEntry.getValue();

      if (pa != va) {
        final Set<Integer> paEqualRelation = paHazard.getRelation(MemoryHazard.Type.ADDR_EQUAL);

        // VA.ADDR_EQUAL => PA.ADDR_EQUAL.
        if (paEqualRelation != null) {
          if (!paEqualRelation.containsAll(vaEqualRelation)) {
            // Filter off.
            return false;
          }
        }
      }
    }

    return true;
  }
}

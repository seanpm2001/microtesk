/*
 * Copyright 2016 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.mmu.translator.coverage;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.mmu.basis.BufferAccessEvent;
import ru.ispras.microtesk.mmu.basis.BufferEventPair;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAction;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBuffer;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBufferAccess;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuGuard;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuSubsystem;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuTransition;
import ru.ispras.microtesk.utils.function.BiFunction;

/**
 * {@link MemoryGraphAbstraction} contains different abstractions for  memory access path transitions.
 * 
 * <p>An abstraction function maps a transition into some abstract object or {@code null} if the
 * transition is insignificant. Given an abstraction function, it is possible to divide the set
 * of all possible paths into equivalence classes.</p>
 * 
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public enum MemoryGraphAbstraction implements BiFunction<MmuSubsystem, MmuTransition, Object> {
  TRIVIAL {
    @Override
    public Object apply(final MmuSubsystem memory, final MmuTransition transition) {
      return transition;
    }
  },

  UNIVERSAL {
    @Override
    public Object apply(final MmuSubsystem memory, final MmuTransition transition) {
      return null;
    }
  },

  BUFFER_EVENT_PAIR {
    @Override
    public Object apply(final MmuSubsystem memory, final MmuTransition transition) {
      InvariantChecks.checkNotNull(transition);

      final MmuGuard guard = transition.getGuard();

      if (guard == null) {
        return null;
      }

      final MmuBufferAccess access = guard.getBufferAccess();

      if (access == null) {
        return null;
      }

      final MmuBuffer buffer = access.getBuffer();
      final BufferAccessEvent event = guard.getEvent();
      
      return new BufferEventPair(buffer, event);
    }
  },

  TARGET_BUFFER_ACCESS {
    @Override
    public Object apply(final MmuSubsystem memory, final MmuTransition transition) {
      InvariantChecks.checkNotNull(memory);
      InvariantChecks.checkNotNull(transition);

      final MmuAction action = transition.getTarget();
      final MmuBufferAccess access = action.getBufferAccess();

      if (access == null || !access.getBuffer().equals(memory.getTargetBuffer())) {
        return null;
      }

      return access;
    }
  }
}

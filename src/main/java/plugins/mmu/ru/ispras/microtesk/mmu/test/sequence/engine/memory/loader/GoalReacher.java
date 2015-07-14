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

package ru.ispras.microtesk.mmu.test.sequence.engine.memory.loader;

import java.util.ArrayList;
import java.util.List;

import ru.ispras.microtesk.mmu.basis.BufferAccessEvent;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBuffer;

/**
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class GoalReacher implements Loader {
  private final MmuBuffer device;
  private final BufferAccessEvent event;
  private final long address;
  private final List<Long> loads = new ArrayList<>();

  public GoalReacher(final MmuBuffer device, final BufferAccessEvent event, final long address) {
    this.device = device;
    this.event = event;
    this.address = address;
  }

  public MmuBuffer getDevice() {
    return device;
  }
  
  public BufferAccessEvent getEvent() {
    return event;
  }

  public long getAddress() {
    return address;
  }

  public void addLoads(final BufferAccessEvent event, final long address, final List<Long> loads) {
    this.loads.addAll(loads);
  }

  @Override
  public List<Long> prepareLoads() {
    return loads;
  }
}

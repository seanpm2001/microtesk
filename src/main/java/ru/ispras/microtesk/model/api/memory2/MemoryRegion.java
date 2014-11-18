/*
 * Copyright 2014 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.model.api.memory2;

import ru.ispras.fortress.data.types.bitvector.BitVector;

public final class MemoryRegion {
  private final int index;
  private final BitVector data;

  MemoryRegion(int index) {
    this(index, null);
  }

  MemoryRegion(int index, BitVector data) {
    if (index < 0) {
      throw new IndexOutOfBoundsException("index < 0 : " + index);
    }

    this.index = index;
    this.data = data;
  }

  public int getIndex() {
    return index;
  }

  public boolean hasData() {
    return null != data;
  }

  public BitVector getData() {
    return data;
  }

  @Override
  public String toString() {
    return String.format("MemoryRegion [index=%d, data=%s]", index, data);
  }
}

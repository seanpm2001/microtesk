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

package ru.ispras.microtesk.translator.mmu.ir;

import static ru.ispras.fortress.util.InvariantChecks.checkNotNull;

import ru.ispras.fortress.data.types.bitvector.BitVector;

public final class Segment {
  private final String id;
  private final Var addressArg;

  private final BitVector rangeStart;
  private final BitVector rangeEnd;

  public Segment(
      String id,
      String addressArgId, Address addressArgType,
      BitVector rangeStart, BitVector rangeEnd) {

    checkNotNull(id);
    checkNotNull(addressArgId);
    checkNotNull(addressArgType);
    checkNotNull(rangeStart);
    checkNotNull(rangeEnd);

    if (addressArgType.getBitSize() != rangeStart.getBitSize() ||
        addressArgType.getBitSize() != rangeEnd.getBitSize()) {
      throw new IllegalArgumentException();      
    }

    this.id = id;
    this.addressArg = new Var(addressArgId, addressArgType.getType(), addressArgType);
    this.rangeStart = rangeStart;
    this.rangeEnd = rangeEnd;
  }

  public String getId() {
    return id;
  }

  public Var getAddressArg() {
    return addressArg;
  }

  public BitVector getRangeStart() {
    return rangeStart;
  }

  public BitVector getRangeEnd() {
    return rangeEnd;
  }

  @Override
  public String toString() {
    return String.format("segment %s(%s: %s(%d)) range = (%s, %s)",
        id, addressArg, rangeStart.toHexString(), rangeEnd.toHexString());
  }
}

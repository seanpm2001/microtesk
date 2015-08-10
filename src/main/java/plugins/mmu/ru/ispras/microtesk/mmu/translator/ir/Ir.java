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

package ru.ispras.microtesk.mmu.translator.ir;

import static ru.ispras.fortress.util.InvariantChecks.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import ru.ispras.fortress.expression.NodeValue;

public final class Ir {
  private final String modelName;
  private final Map<String, NodeValue> constants;
  private final Map<String, Address> addresses;
  private final Map<String, Segment> segments;
  private final Map<String, Buffer> buffers;
  private final Map<String, Memory> memories;
  private final Map<String, Type> types;

  public Ir(final String modelName) {
    checkNotNull(modelName);
    this.modelName = modelName;

    this.constants = new LinkedHashMap<>();
    this.addresses = new LinkedHashMap<>();
    this.segments = new LinkedHashMap<>();
    this.buffers = new LinkedHashMap<>();
    this.memories = new LinkedHashMap<>();
    this.types = new LinkedHashMap<>();
  }

  public String getModelName() {
    return modelName;
  }

  public Map<String, NodeValue> getConstants() {
    return Collections.unmodifiableMap(constants);
  }

  public Map<String, Address> getAddresses() {
    return Collections.unmodifiableMap(addresses);
  }

  public Map<String, Segment> getSegments() {
    return Collections.unmodifiableMap(segments);
  }

  public Map<String, Buffer> getBuffers() {
    return Collections.unmodifiableMap(buffers);
  }

  public Map<String, Memory> getMemories() {
    return Collections.unmodifiableMap(memories);
  }

  public Map<String, Type> getTypes() {
    return Collections.unmodifiableMap(types);
  }

  public void addConstant(final String id, final NodeValue value) {
    checkNotNull(id);
    checkNotNull(value);
    constants.put(id, value);
  }

  public void addAddress(final Address address) {
    checkNotNull(address);
    addresses.put(address.getId(), address);
  }

  public void addSegment(final Segment segment) {
    checkNotNull(segment);
    segments.put(segment.getId(), segment);
  }

  public void addBuffer(final Buffer buffer) {
    checkNotNull(buffer);
    buffers.put(buffer.getId(), buffer);
  }

  public void addMemory(final Memory memory) {
    checkNotNull(memory);
    memories.put(memory.getId(), memory);
  }

  public void addType(final String name, final Type type) {
    checkNotNull(name);
    checkNotNull(type);

    types.put(name, type);
  }

  @Override
  public String toString() {
    return String.format(
        "Mmu Ir:%n addresses=%s%n segments=%s%n buffers=%s%n memories=%s%n types=%s", 
        mapToString(addresses),
        mapToString(segments),
        mapToString(buffers),
        mapToString(memories),
        mapToString(types)
        );
  }

  public static <U, V> String mapToString(final Map<U, V> map) {
    final StringBuilder builder = new StringBuilder();
    builder.append(String.format("{%n"));
    for (final Map.Entry<U, V> entry : map.entrySet()) {
      builder.append(String.format("%s = %s%n", entry.getKey(), entry.getValue()));
    }
    builder.append("}");
    return builder.toString();
  }
}

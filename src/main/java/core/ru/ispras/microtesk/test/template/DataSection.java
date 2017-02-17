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

package ru.ispras.microtesk.test.template;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.model.api.memory.MemoryAllocator;
import ru.ispras.microtesk.test.LabelManager;

/**
 * The {@link DataSection} class describes data sections defined in
 * test templates or created by engines.
 * 
 * @author <a href="mailto:andrewt@ispras.ru">Andrei Tatarnikov</a>
 */
public final class DataSection {
  private final List<LabelValue> labelValues;
  private final List<DataDirective> directives;

  private final BigInteger physicalAddress;
  private final boolean global;
  private final boolean separateFile;

  private int sequenceIndex;
  private BigInteger allocationEndAddress;

  protected DataSection(
      final List<LabelValue> labelValues,
      final List<DataDirective> directives,
      final BigInteger physicalAddress,
      final boolean global,
      final boolean separateFile) {
    InvariantChecks.checkNotNull(directives);

    this.labelValues = Collections.unmodifiableList(labelValues);
    this.directives = Collections.unmodifiableList(directives);

    this.physicalAddress = physicalAddress;
    this.global = global;
    this.separateFile = separateFile;

    this.sequenceIndex = Label.NO_SEQUENCE_INDEX;
    this.allocationEndAddress = null;
  }

  protected DataSection(final DataSection other) {
    InvariantChecks.checkNotNull(other);

    try {
      this.labelValues = LabelValue.copyAll(other.labelValues);
      this.directives = copyAllDirectives(other.directives);
    } catch (final Exception e) {
      Logger.error("Failed to copy %s", other);
      throw e;
    }

    this.physicalAddress = other.physicalAddress;
    this.global = other.global;
    this.separateFile = other.separateFile;

    this.sequenceIndex = other.sequenceIndex;
  }

  private static List<DataDirective> copyAllDirectives(final List<DataDirective> directives) {
    InvariantChecks.checkNotNull(directives);

    if (directives.isEmpty()) {
      return Collections.emptyList();
    }

    final List<DataDirective> result = new ArrayList<>(directives.size());
    for (final DataDirective directive : directives) {
      result.add(directive.copy());
    }

    return result;
  }

  public int getSequenceIndex() {
    InvariantChecks.checkTrue(global ? sequenceIndex == Label.NO_SEQUENCE_INDEX : sequenceIndex >= 0);
    return sequenceIndex;
  }

  public void setSequenceIndex(final int value) {
    if (global ? value == Label.NO_SEQUENCE_INDEX : value >= 0) {
      sequenceIndex = value;
    }
  }

  public List<Label> getLabels() {
    final List<Label> result = new ArrayList<>(labelValues.size());

    for (final LabelValue labelValue : labelValues) {
      final Label label = labelValue.getLabel();
      InvariantChecks.checkNotNull(label);
      result.add(label);
    }

    return result;
  }

  public List<DataDirective> getDirectives() {
    return directives;
  }

  public boolean isGlobal() {
    return global;
  }

  public boolean isSeparateFile() {
    return separateFile;
  }

  public BigInteger getAllocationEndAddress() {
    return allocationEndAddress;
  }

  public void allocate(final MemoryAllocator allocator) {
    InvariantChecks.checkNotNull(allocator);

    final BigInteger oldAddress = allocator.getCurrentAddress();
    if (null != physicalAddress) {
      allocator.setCurrentAddress(physicalAddress);
    }

    try {
      for (final DataDirective directive : directives) {
        directive.apply(allocator);
      }
    } finally {
      allocationEndAddress = allocator.getCurrentAddress();
      if (null != physicalAddress) {
        allocator.setCurrentAddress(oldAddress);
      }
    }
  }

  public void registerLabels(final LabelManager labelManager) {
    InvariantChecks.checkNotNull(labelManager);

    final int sequenceIndex = getSequenceIndex();
    for (final LabelValue labelValue : labelValues) {
      final Label label = labelValue.getLabel();
      InvariantChecks.checkNotNull(label);

      final BigInteger address = labelValue.getAddress();
      InvariantChecks.checkNotNull(address);

      label.setSequenceIndex(sequenceIndex);
      labelManager.addLabel(label, address.longValue());
    }
  }

  @Override
  public String toString() {
    return String.format(
        "DataSection [global=%s, separateFile=%s, labelValues=%s, directives=%s]",
        global,
        separateFile,
        labelValues,
        directives
        );
  }
}

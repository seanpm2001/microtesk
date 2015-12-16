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

package ru.ispras.microtesk.mmu.test.sequence.engine.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ru.ispras.fortress.randomizer.Randomizer;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.basis.classifier.Classifier;
import ru.ispras.microtesk.mmu.basis.MemoryAccessConstraints;
import ru.ispras.microtesk.mmu.basis.MemoryOperation;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.filter.FilterBuilder;
import ru.ispras.microtesk.utils.function.BiPredicate;
import ru.ispras.microtesk.utils.function.Predicate;
import ru.ispras.microtesk.utils.function.TriPredicate;
import ru.ispras.testbase.knowledge.iterator.CollectionIterator;
import ru.ispras.testbase.knowledge.iterator.Iterator;
import ru.ispras.testbase.knowledge.iterator.ProductIterator;

/**
 * {@link MemoryAccessStructureIteratorEx} implements an iterator of memory access structures.
 * 
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class MemoryAccessStructureIteratorEx implements Iterator<MemoryAccessStructure> {
  private List<Map<MemoryOperation, Collection<MemoryAccessType>>> accessTypeGroups;
  private final Classifier<MemoryAccessPath> classifier;
  private final MemoryAccessConstraints constraints;

  /** Contains user-defined filters. */
  private final FilterBuilder filterBuilder = new FilterBuilder();

  private Iterator<List<MemoryAccessType>> typesIterator;
  private MemoryAccessStructureIterator structureIterator;

  private boolean hasValue;

  public MemoryAccessStructureIteratorEx(
      final List<Collection<MemoryAccessType>> accessTypes,
      final boolean randomDataType,
      final Classifier<MemoryAccessPath> classifier,
      final MemoryAccessConstraints constraints) {
    InvariantChecks.checkNotNull(accessTypes);
    InvariantChecks.checkNotEmpty(accessTypes);
    InvariantChecks.checkNotNull(classifier);
    // Parameter {@code constraints} can be null.
    // Parameter {@code settings} can be null.

    this.accessTypeGroups = randomDataType ? getAccessTypeGroups(accessTypes) : null;
    this.classifier = classifier;
    this.constraints = constraints;

    final ProductIterator<MemoryAccessType> typesIterator = new ProductIterator<>();

    if (randomDataType) {
      for (final Map<MemoryOperation, Collection<MemoryAccessType>> group : accessTypeGroups) {
        final Collection<MemoryAccessType> cases = new ArrayList<>();

        for (final Collection<MemoryAccessType> types : group.values()) {
          cases.add(types.iterator().next());
        }

        typesIterator.registerIterator(new CollectionIterator<>(cases));
      }
    } else {
      for (final Collection<MemoryAccessType> cases : accessTypes) {
        typesIterator.registerIterator(new CollectionIterator<>(cases));
      }
    }

    this.typesIterator = typesIterator;
  }

  private List<Map<MemoryOperation, Collection<MemoryAccessType>>> getAccessTypeGroups(
      final List<Collection<MemoryAccessType>> accessTypes) {
    final List<Map<MemoryOperation, Collection<MemoryAccessType>>> result = new ArrayList<>();

    for (final Collection<MemoryAccessType> cases : accessTypes) {
      final Map<MemoryOperation, Collection<MemoryAccessType>> groups = new HashMap<>();

      for (final MemoryAccessType type : cases) {
        Collection<MemoryAccessType> group = groups.get(type.getOperation());

        if (group == null) {
          groups.put(type.getOperation(), group = new HashSet<>());
        }

        group.add(type);
      }

      result.add(groups);
    }

    return result;
  }

  @Override
  public void init() {
    initTypes();
    initStructure();

    hasValue = true;
  }

  @Override
  public boolean hasValue() {
    return hasValue;
  }

  @Override
  public MemoryAccessStructure value() {
    return structureIterator.value();
  }

  @Override
  public void next() {
    if (nextStructure()) {
      return;
    }
    if (nextTypes()) {
      initStructure();
      return;
    }

    hasValue = false;
  }

  @Override
  public void stop() {
    hasValue = false;
  }

  @Override
  public MemoryAccessStructureIteratorEx clone() {
    throw new UnsupportedOperationException();
  }

  private void initTypes() {
    typesIterator.init();
  }

  private boolean nextTypes() {
    if (typesIterator.hasValue()) {
      typesIterator.next();
    }
    return typesIterator.hasValue();
  }

  private void initStructure() {
    structureIterator = new MemoryAccessStructureIterator(
        typesIterator.value(), null, classifier, constraints);

    structureIterator.addFilterBuilder(filterBuilder);
    structureIterator.init();
  }

  private boolean nextStructure() {
    if (structureIterator.hasValue()) {
      // Randomize data types if it is required.
      if (accessTypeGroups != null) {
        final List<MemoryAccessType> types = structureIterator.getAccessTypes();

        for (int i = 0; i < types.size(); i++) {
          final MemoryAccessType type = types.get(i);
          final MemoryOperation op = type.getOperation();
          final Map<MemoryOperation, Collection<MemoryAccessType>> groups = accessTypeGroups.get(i);
          final Collection<MemoryAccessType> cases = groups.get(op);

          types.set(i, Randomizer.get().choose(cases));
        }
      }
      structureIterator.next();
    }
    return structureIterator.hasValue();
  }

  //------------------------------------------------------------------------------------------------
  // Filter Registration
  //------------------------------------------------------------------------------------------------

  public void addAccessFilter(
      final Predicate<MemoryAccess> filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addAccessFilter(filter);
  }

  public void addHazardFilter(
      final TriPredicate<MemoryAccess, MemoryAccess, MemoryHazard> filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addHazardFilter(filter);
  }

  public void addDependencyFilter(
      final TriPredicate<MemoryAccess, MemoryAccess, MemoryDependency> filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addDependencyFilter(filter);
  }

  public void addUnitedHazardFilter(
      final BiPredicate<MemoryAccess, MemoryUnitedHazard> filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addUnitedHazardFilter(filter);
  }

  public void addUnitedDependencyFilter(
      final BiPredicate<MemoryAccess, MemoryUnitedDependency> filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addUnitedDependencyFilter(filter);
  }

  public void addStructureFilter(
      final Predicate<MemoryAccessStructure> filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addStructureFilter(filter);
  }

  public void addFilterBuilder(final FilterBuilder filter) {
    InvariantChecks.checkNotNull(filter);
    filterBuilder.addFilterBuilder(filter);
  }
}

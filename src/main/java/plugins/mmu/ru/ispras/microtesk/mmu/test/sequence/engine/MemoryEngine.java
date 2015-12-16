/*
 * Copyright 2006-2015 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.mmu.test.sequence.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.ispras.fortress.data.types.bitvector.BitVector;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.basis.classifier.Classifier;
import ru.ispras.microtesk.basis.classifier.ClassifierTrivial;
import ru.ispras.microtesk.basis.classifier.ClassifierUniversal;
import ru.ispras.microtesk.basis.solver.Solver;
import ru.ispras.microtesk.basis.solver.SolverResult;
import ru.ispras.microtesk.mmu.MmuPlugin;
import ru.ispras.microtesk.mmu.basis.DataType;
import ru.ispras.microtesk.mmu.basis.MemoryAccessConstraints;
import ru.ispras.microtesk.mmu.basis.MemoryOperation;
import ru.ispras.microtesk.mmu.model.api.BufferObserver;
import ru.ispras.microtesk.mmu.model.api.MmuModel;
import ru.ispras.microtesk.mmu.settings.MmuSettingsUtils;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryAccessPath;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryAccessStructure;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryAccessStructureIterator;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.MemoryAccessType;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.allocator.AddressAllocator;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.allocator.EntryIdAllocator;
import ru.ispras.microtesk.mmu.test.sequence.engine.memory.classifier.ClassifierEventBased;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAddressType;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBuffer;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuSubsystem;
import ru.ispras.microtesk.settings.GeneratorSettings;
import ru.ispras.microtesk.settings.RegionSettings;
import ru.ispras.microtesk.test.sequence.engine.Engine;
import ru.ispras.microtesk.test.sequence.engine.EngineContext;
import ru.ispras.microtesk.test.sequence.engine.EngineResult;
import ru.ispras.microtesk.test.template.Call;
import ru.ispras.microtesk.test.template.Primitive;
import ru.ispras.microtesk.test.template.Situation;
import ru.ispras.microtesk.utils.Range;
import ru.ispras.microtesk.utils.function.Predicate;
import ru.ispras.testbase.knowledge.iterator.Iterator;

/**
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class MemoryEngine implements Engine<MemorySolution> {
  public static final String ID = "memory";

  public static final String PARAM_CLASSIFIER = "classifier";
  public static final String PARAM_CLASSIFIER_TRIVIAL = "trivial";
  public static final String PARAM_CLASSIFIER_UNIVERSAL = "universal";
  public static final String PARAM_CLASSIFIER_EVENT_BASED = "event-based";
  public static final Classifier<MemoryAccessPath> PARAM_CLASSIFIER_DEFAULT =
      new ClassifierTrivial<MemoryAccessPath>();

  public static final String PARAM_PAGE_MASK = "page_mask";
  public static final long PARAM_PAGE_MASK_DEFAULT = 0x0fff;

  public static final String PARAM_ALIGN = "align";
  public static final DataType PARAM_ALIGN_DEFAULT = null;

  public static boolean isMemoryAccessWithSituation(final Call abstractCall) {
    InvariantChecks.checkNotNull(abstractCall);

    if (!abstractCall.isLoad() && !abstractCall.isStore()) {
      return false;
    }

    final Primitive primitive = abstractCall.getRootOperation();
    InvariantChecks.checkNotNull(primitive, "Primitive is null");

    final Situation situation = primitive.getSituation();

    return situation != null;
  }

  public static MemoryAccessConstraints getMemoryAccessConstants(final Call abstractCall) {
    if (!isMemoryAccessWithSituation(abstractCall)) {
      return null;
    }

    final Primitive primitive = abstractCall.getRootOperation();
    final Situation situation = primitive.getSituation();

    final Object attribute = situation.getAttribute("path");
    if (null == attribute) {
      return null;
    }

    if (attribute instanceof MemoryAccessConstraints) {
      return (MemoryAccessConstraints) attribute;
    }

    Logger.warning("Unexpected format of the path attribute of a test situation: %s", attribute);
    return null;
  }

  private static Classifier<MemoryAccessPath> getClassifier(final Object value) {
    final String id = value != null ? value.toString() : null;

    if (PARAM_CLASSIFIER_TRIVIAL.equals(id)) {
      return new ClassifierTrivial<MemoryAccessPath>();
    }
    if (PARAM_CLASSIFIER_UNIVERSAL.equals(id)) {
      return new ClassifierUniversal<MemoryAccessPath>();
    }
    if (PARAM_CLASSIFIER_EVENT_BASED.equals(id)) {
      return new ClassifierEventBased();
    }

    return PARAM_CLASSIFIER_DEFAULT;
  }

  private static long getPageMask(final Object value) {
    if (value == null) {
      return PARAM_PAGE_MASK_DEFAULT;
    }

    final Number id =
        value instanceof Number ? (Number) value : Long.parseLong(value.toString(), 16);

    return id.longValue();
  }

  private static DataType getAlign(final Object value) {
    if (value == null) {
      return PARAM_ALIGN_DEFAULT;
    }

    final Number id =
        value instanceof Number ? (Number) value : Long.parseLong(value.toString(), 10);

    return DataType.type(id.intValue());
  }

  private Classifier<MemoryAccessPath> classifier = PARAM_CLASSIFIER_DEFAULT;
  private long pageMask = PARAM_PAGE_MASK_DEFAULT;
  private DataType align = PARAM_ALIGN_DEFAULT;

  private AddressAllocator addressAllocator;
  private EntryIdAllocator entryIdAllocator;

  @Override
  public Class<MemorySolution> getSolutionClass() {
    return MemorySolution.class;
  }

  @Override
  public void configure(final Map<String, Object> attributes) {
    InvariantChecks.checkNotNull(attributes);

    classifier = getClassifier(attributes.get(PARAM_CLASSIFIER));
    pageMask = getPageMask(attributes.get(PARAM_PAGE_MASK));
    align = getAlign(attributes.get(PARAM_ALIGN));
  }

  @Override
  public EngineResult<MemorySolution> solve(
      final EngineContext engineContext, final List<Call> abstractSequence) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(abstractSequence);

    final Iterator<MemoryAccessStructure> structureIterator =
        getStructureIterator(engineContext, abstractSequence);

    final Iterator<MemorySolution> solutionIterator =
        getSolutionIterator(engineContext, structureIterator);

    final GeneratorSettings settings = engineContext.getSettings();
    final Map<MmuAddressType, Collection<? extends Range<Long>>> addressToRegions = new HashMap<>();
    final Collection<RegionSettings> regions = new ArrayList<>();

    for (final RegionSettings region : settings.getMemory().getRegions()) {
      if (region.isEnabled() && region.getType() == RegionSettings.Type.DATA) {
        regions.add(region);
      }
    }

    Logger.debug("Memory engine: regions=%s", regions);

    final MmuSubsystem memory = MmuPlugin.getSpecification();

    for (final MmuAddressType addrType : memory.getSortedListOfAddresses()) {
      if (addrType.equals(memory.getVirtualAddress())) {
        addressToRegions.put(addrType, memory.getSegments()); // TODO: memory.getSegments(addrType)
      } else if (addrType.equals(memory.getPhysicalAddress())) {
        addressToRegions.put(addrType, regions);
      } else {
        addressToRegions.put(addrType, Collections.<RegionSettings>emptyList());
      }
    }

    this.addressAllocator = new AddressAllocator(memory, addressToRegions);
    this.entryIdAllocator = new EntryIdAllocator(memory);

    return new EngineResult<MemorySolution>(solutionIterator);
  }

  public Collection<Long> getAllAddresses(
      final MmuAddressType addressType, final RegionSettings region) {
    InvariantChecks.checkNotNull(addressType);
    InvariantChecks.checkNotNull(region);

    return addressAllocator.getAllAddresses(addressType, region);
  }

  private Iterator<MemoryAccessStructure> getStructureIterator(
      final EngineContext engineContext,
      final List<Call> abstractSequence) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(abstractSequence);

    final MemoryEngineContext customContext =
        (MemoryEngineContext) engineContext.getCustomContext(ID);

    // TODO: Compatibility with MMU TestGen.
    final Iterator<MemoryAccessStructure> structureIterator =
        customContext != null ? customContext.getStructureIterator() : null;

    if (structureIterator != null) {
      return structureIterator;
    }

    final List<MemoryAccessType> accessTypes = new ArrayList<>();
    final List<MemoryAccessConstraints> accessConstraints = new ArrayList<>();

    for (final Call abstractCall : abstractSequence) {
      if(!isMemoryAccessWithSituation(abstractCall)) {
        // Skip non memory access instructions and memory accesses instructions without situations.
        continue;
      }

      final MemoryOperation operation =
          abstractCall.isLoad() ? MemoryOperation.LOAD : MemoryOperation.STORE;

      final MemoryAccessConstraints constraints =
          getMemoryAccessConstants(abstractCall);

      final int blockSizeInBits = abstractCall.getBlockSize();
      InvariantChecks.checkTrue((blockSizeInBits & 7) == 0);

      final int blockSizeInBytes = blockSizeInBits >>> 3;
      accessTypes.add(new MemoryAccessType(operation, DataType.type(blockSizeInBytes)));

      accessConstraints.add(constraints);
    }

    Logger.debug("Creating memory access iterator: %s", accessTypes);
    Logger.debug("Memory access constraints: %s", accessConstraints);

    final GeneratorSettings settings = engineContext.getSettings();
    final MemoryAccessConstraints globalConstraints = null != settings ?
        MmuSettingsUtils.getConstraints(MmuPlugin.getSpecification(), settings) : null;

    return new MemoryAccessStructureIterator(
        accessTypes, accessConstraints, classifier, globalConstraints);
  }

  private Iterator<MemorySolution> getSolutionIterator(
      final EngineContext engineContext,
      final Iterator<MemoryAccessStructure> structureIterator) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(structureIterator);

    // TODO: Remove the custom context (it is required for MMU TestGen only).
    final MemoryEngineContext customContext;

    if (engineContext.getCustomContext(ID) != null) {
      customContext = (MemoryEngineContext) engineContext.getCustomContext(ID);
    } else {
      final MmuSubsystem memory = MmuPlugin.getSpecification();
      final Map<MmuAddressType, Predicate<Long>> hitCheckers = new LinkedHashMap<>();

      for (final MmuAddressType addressType : memory.getSortedListOfAddresses()) {
        hitCheckers.put(addressType, new Predicate<Long>() {
          @Override
          public boolean test(final Long address) {
            final MmuModel model = MmuPlugin.getModel();
            final MmuSubsystem memory = MmuPlugin.getSpecification();

            for (final MmuBuffer buffer : memory.getSortedListOfBuffers()) {
              if (buffer.getAddress().equals(addressType) && buffer != memory.getTargetBuffer()) {
                Logger.debug("Hit checker: %s", buffer);

                final BufferObserver observer = model.getBufferObserver(buffer.getName());

                if (observer.isHit(BitVector.valueOf(address, addressType.getWidth()))) {
                  return true;
                }
              }
            }

            return false;
          }
        });
      }

      customContext = new MemoryEngineContext(null, hitCheckers);
    }

    return new Iterator<MemorySolution>() {
      private MemorySolution solution = null;

      private MemorySolution getSolution() {
        while (structureIterator.hasValue()) {
          final MemoryAccessStructure structure = structureIterator.value();

          // Reset the allocation tables before solving the constraint.
          addressAllocator.reset();
          entryIdAllocator.reset();

          final MemorySolver solver = new MemorySolver(
              structure, customContext, addressAllocator, entryIdAllocator,
              pageMask, align, engineContext.getSettings());

          final SolverResult<MemorySolution> result = solver.solve(Solver.Mode.MAP);
          InvariantChecks.checkNotNull(result);

          if (result.getStatus() == SolverResult.Status.SAT) {
            solution = result.getResult();
            break;
          }

          structureIterator.next();
        }

        return structureIterator.hasValue() ? solution : null;
      }

      @Override
      public void init() {
        structureIterator.init();
        solution = getSolution();
      }

      @Override
      public boolean hasValue() {
        return solution != null;
      }

      @Override
      public MemorySolution value() {
        return solution;
      }

      @Override
      public void next() {
        structureIterator.next();
        solution = getSolution();
      }

      @Override
      public void stop() {
        solution = null;
      }

      @Override
      public Iterator<MemorySolution> clone() {
        throw new UnsupportedOperationException();
      }
    };
  }
}

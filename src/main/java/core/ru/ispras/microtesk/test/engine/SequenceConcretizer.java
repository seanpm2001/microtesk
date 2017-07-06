/*
 * Copyright 2017 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.test.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.fortress.util.Pair;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.model.ConfigurationException;
import ru.ispras.microtesk.model.InstructionCall;
import ru.ispras.microtesk.model.IsaPrimitive;
import ru.ispras.microtesk.model.memory.LocationAccessor;
import ru.ispras.microtesk.options.Option;
import ru.ispras.microtesk.test.Code;
import ru.ispras.microtesk.test.CodeAllocator;
import ru.ispras.microtesk.test.ConcreteSequence;
import ru.ispras.microtesk.test.Executor;
import ru.ispras.microtesk.test.GenerationAbortedException;
import ru.ispras.microtesk.test.LabelManager;
import ru.ispras.microtesk.test.Printer;
import ru.ispras.microtesk.test.SelfCheck;
import ru.ispras.microtesk.test.template.AbstractCall;
import ru.ispras.microtesk.test.template.Argument;
import ru.ispras.microtesk.test.template.ConcreteCall;
import ru.ispras.microtesk.test.template.DataSection;
import ru.ispras.microtesk.test.template.Primitive;
import ru.ispras.testbase.knowledge.iterator.Iterator;

final class SequenceConcretizer implements Iterator<ConcreteSequence>{
  private final EngineContext engineContext;
  private final boolean isTrivial;
  private final Iterator<AbstractSequence> sequenceIterator;

  public SequenceConcretizer(
      final EngineContext engineContext,
      final boolean isTrivial,
      final Iterator<AbstractSequence> sequenceIterator) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(sequenceIterator);

    this.engineContext = engineContext;
    this.isTrivial = isTrivial;
    this.sequenceIterator = sequenceIterator;
  }

  @Override
  public void init() {
    sequenceIterator.init();
  }

  @Override
  public boolean hasValue() {
    return sequenceIterator.hasValue();
  }

  @Override
  public ConcreteSequence value() {
    final AbstractSequence abstractSequence = sequenceIterator.value();
    return concretizeSequence(abstractSequence);
  }

  @Override
  public void next() {
    sequenceIterator.next();
  }

  @Override
  public void stop() {
    sequenceIterator.stop();
  }

  @Override
  public Iterator<ConcreteSequence> clone() {
    throw new UnsupportedOperationException();
  }

  private ConcreteSequence concretizeSequence(final AbstractSequence abstractSequence) {
    InvariantChecks.checkNotNull(abstractSequence);

    // Makes a copy as the adapter may modify the abstract sequence.
    final AbstractSequence abstractSequenceCopy = new AbstractSequence(
        abstractSequence.getSection(), AbstractCall.copyAll(abstractSequence.getSequence()));

    final boolean isDebug = Logger.isDebug();
    Logger.setDebug(engineContext.getOptions().getValueAsBoolean(Option.DEBUG));

    try {
      engineContext.getModel().setUseTempState(true);
      return processSequence(engineContext, abstractSequenceCopy);
    } catch (final ConfigurationException e) {
      throw new GenerationAbortedException(e);
    } finally {
      Logger.setDebug(isDebug);
      engineContext.getModel().setUseTempState(false);
    }
  }

  private ConcreteSequence processSequence(
      final EngineContext engineContext,
      final AbstractSequence abstractSequence) throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(abstractSequence);

    final int sequenceIndex =
        engineContext.getStatistics().getSequences();

    final List<ConcreteCall> concreteCalls =
        EngineUtils.makeConcreteCalls(engineContext, abstractSequence.getSequence());

    final ConcreteSequence.Builder builder =
        new ConcreteSequence.Builder(abstractSequence.getSection());
    builder.add(concreteCalls);

    final ConcreteSequence concreteSequence = builder.build();

    if (Logger.isDebug()) {
      Printer.getConsole(engineContext.getOptions(), engineContext.getStatistics()).
          printSequence(engineContext.getModel().getPE(), builder.build());
    }

    final ConcreteSequenceCreator creator =
        new ConcreteSequenceCreator(sequenceIndex, abstractSequence, concreteSequence);

    execute(
        engineContext,
        creator,
        engineContext.getCodeAllocationAddress(),
        concreteSequence,
        sequenceIndex
        );

    engineContext.setCodeAllocationAddress(creator.getAllocationAddress());
    final ConcreteSequence result = creator.createTestSequence();

    // TODO: temporary implementation of self-checks.
    if (engineContext.getOptions().getValueAsBoolean(Option.SELF_CHECKS)) {
      final List<SelfCheck> selfChecks = createSelfChecks(abstractSequence.getSequence());
      result.setSelfChecks(selfChecks);
    }

    return result;
  }

  private void execute(
      final EngineContext engineContext,
      final ExecutorListener listener,
      final long allocationAddress,
      final ConcreteSequence concreteSequence,
      final int sequenceIndex) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(listener);
    InvariantChecks.checkNotNull(concreteSequence);

    if (concreteSequence.isEmpty()) {
      listener.setAllocationAddress(allocationAddress);
      return;
    }

    final List<ConcreteCall> sequence = concreteSequence.getAll();
    final LabelManager labelManager = new LabelManager(engineContext.getLabelManager());
    allocateData(engineContext, labelManager, sequence, sequenceIndex);

    final boolean isFetchDecodeEnabled =
        engineContext.getOptions().getValueAsBoolean(Option.FETCH_DECODE_ENABLED);

    final CodeAllocator codeAllocator = new CodeAllocator(
        engineContext.getModel(), labelManager, isFetchDecodeEnabled);

    codeAllocator.init();
    codeAllocator.setAddress(allocationAddress);
    codeAllocator.allocateSequence(concreteSequence, sequenceIndex);

    final ConcreteCall first = sequence.get(0);
    final ConcreteCall last = sequence.get(sequence.size() - 1);

    final long startAddress = first.getAddress();
    final long endAddress = last.getAddress() + last.getByteSize();

    listener.setAllocationAddress(endAddress);

    if (isTrivial) {
      return;
    }

    final Code code = codeAllocator.getCode();
    final Executor executor = new Executor(engineContext, labelManager, true);

    executor.setPauseOnUndefinedLabel(false);
    executor.setListener(listener);

    long address = startAddress;
    do {
      listener.resetLastExecutedCall();
      final Executor.Status status = executor.execute(code, address);

      final boolean isValidAddress =
          code.hasAddress(status.getAddress()) || status.getAddress() == endAddress;

      if (isValidAddress) {
        address = status.getAddress();
      } else {
        if (Logger.isDebug()) {
          Logger.debug(
              "Jump to address %s located outside of the sequence.", status);
        }

        final ConcreteCall lastExecutedCall = listener.getLastExecutedCall();
        final long nextAddress = lastExecutedCall.getAddress() + lastExecutedCall.getByteSize();

        if (Logger.isDebug()) {
          Logger.debug(
              "Execution will be continued from the next instruction 0x%016x.", nextAddress );
        }

        address = nextAddress;
      }
    } while (address != endAddress);
  }

  private static void allocateData(
      final EngineContext engineContext,
      final LabelManager labelManager,
      final List<ConcreteCall> sequence,
      final int sequenceIndex) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(labelManager);
    InvariantChecks.checkNotNull(sequence);

    for (final ConcreteCall call : sequence) {
      if (call.getData() != null) {
        final DataSection data = call.getData();
        data.setSequenceIndex(sequenceIndex);
        data.allocate(engineContext.getModel().getMemoryAllocator());
        data.registerLabels(labelManager);
      }
    }
  }

  private static class ExecutorListener implements Executor.Listener {
    private ConcreteCall lastExecutedCall = null;
    private long allocationAddress = 0;

    @Override
    public void onBeforeExecute(final EngineContext context, final ConcreteCall concreteCall) {
      // Empty
    }

    @Override
    public void onAfterExecute(final EngineContext context, final ConcreteCall concreteCall) {
      lastExecutedCall = concreteCall;
    }

    public final ConcreteCall getLastExecutedCall() {
      return lastExecutedCall;
    }

    public final void resetLastExecutedCall() {
      lastExecutedCall = null;
    }

    public final long getAllocationAddress() {
      return allocationAddress;
    }

    public final void setAllocationAddress(final long value) {
      allocationAddress = value;
    }
  }

  private final class ConcreteSequenceCreator extends ExecutorListener {
    private final int sequenceIndex;
    private final AbstractSequence abstractSequence;
    private final Map<ConcreteCall, Pair<AbstractCall, ConcreteCall>> callMap;
    private final Set<AddressingModeWrapper> initializedModes;
    private final ExecutorListener listenerForInitializers;
    private final ConcreteSequence.Builder testSequenceBuilder;

    private ConcreteSequenceCreator(
        final int sequenceIndex,
        final AbstractSequence abstractSequence,
        final ConcreteSequence concreteSequence) {
      InvariantChecks.checkNotNull(abstractSequence);
      InvariantChecks.checkNotNull(concreteSequence);
      InvariantChecks.checkTrue(abstractSequence.getSequence().size() ==
                                concreteSequence.getAll().size());

      this.sequenceIndex = sequenceIndex;
      this.abstractSequence = abstractSequence;
      this.callMap = new IdentityHashMap<>();
      this.initializedModes = new HashSet<>();
      this.listenerForInitializers = new ExecutorListener();

      for (int index = 0; index < abstractSequence.getSequence().size(); ++index) {
        final AbstractCall abstractCall = abstractSequence.getSequence().get(index);
        final ConcreteCall concreteCall = concreteSequence.getAll().get(index);

        InvariantChecks.checkNotNull(abstractCall);
        InvariantChecks.checkNotNull(concreteCall);

        if (abstractCall.getAttributes().containsKey("dependsOn")) {
          final int dependencyIndex = (int) abstractCall.getAttributes().get("dependsOn");
          callMap.put(concreteCall, new Pair<>(abstractSequence.getSequence().get(dependencyIndex),
                                               concreteSequence.getAll().get(dependencyIndex)));
        } else {
          callMap.put(concreteCall, new Pair<>(abstractCall, concreteCall));
        }
      }

      this.testSequenceBuilder = new ConcreteSequence.Builder(abstractSequence.getSection());
      this.testSequenceBuilder.add(concreteSequence.getAll());
    }

    public ConcreteSequence createTestSequence() {
      return testSequenceBuilder.build();
    }

    @Override
    public void onBeforeExecute(
        final EngineContext engineContext,
        final ConcreteCall concreteCall) {
      InvariantChecks.checkNotNull(concreteCall);
      InvariantChecks.checkNotNull(engineContext);

      final Pair<AbstractCall, ConcreteCall> callEntry = callMap.get(concreteCall);
      if (null == callEntry) {
        return; // Already processed
      }

      try {
        processCall(engineContext, callEntry.first, callEntry.second);
      } catch (final ConfigurationException e) {
        throw new GenerationAbortedException(
            "Failed to generate test data for " + concreteCall.getText(), e);
      } finally {
        callMap.put(concreteCall, null);
        if (concreteCall != callEntry.second) {
          callMap.put(callEntry.second, null);
        }
      }
    }

    private void processCall(
        final EngineContext engineContext,
        final AbstractCall abstractCall,
        final ConcreteCall concreteCall) throws ConfigurationException {
      InvariantChecks.checkNotNull(engineContext);
      InvariantChecks.checkNotNull(abstractCall);
      InvariantChecks.checkNotNull(concreteCall);

      // Not executable calls do not need test data
      if (!abstractCall.isExecutable()) {
        return;
      }

      if (Logger.isDebug()) {
        Logger.debug("%nGenerating test data for %s...", concreteCall.getText());
      }

      final Primitive abstractPrimitive = abstractCall.getRootOperation();
      EngineUtils.checkRootOp(abstractPrimitive);

      final InstructionCall instructionCall = concreteCall.getExecutable();
      InvariantChecks.checkNotNull(instructionCall);

      final IsaPrimitive concretePrimitive = instructionCall.getRootPrimitive();
      InvariantChecks.checkNotNull(concretePrimitive);

      processPrimitive(engineContext, abstractCall, abstractPrimitive, concretePrimitive);
    }

    private void processPrimitive(
        final EngineContext engineContext,
        final AbstractCall abstractCall,
        final Primitive abstractPrimitive,
        final IsaPrimitive concretePrimitive) throws ConfigurationException {
      InvariantChecks.checkNotNull(engineContext);
      InvariantChecks.checkNotNull(abstractPrimitive);
      InvariantChecks.checkNotNull(concretePrimitive);

      // Unrolls shortcuts to establish correspondence between abstract and concrete primitives
      final IsaPrimitive fixedConcretePrimitive =
          findConcretePrimitive(abstractPrimitive, concretePrimitive);

      InvariantChecks.checkNotNull(
          fixedConcretePrimitive, abstractPrimitive.getName() + " not found.");

      for (final Argument argument : abstractPrimitive.getArguments().values()) {
        if (Argument.Kind.OP == argument.getKind()) {
          final String argumentName = argument.getName();
          final Primitive abstractArgument = (Primitive) argument.getValue();

          final IsaPrimitive concreteArgument =
              fixedConcretePrimitive.getArguments().get(argumentName);

          processPrimitive(engineContext, abstractCall, abstractArgument, concreteArgument);
        }
      }

      final List<AbstractCall> initializer = EngineUtils.makeInitializer(
          engineContext,
          abstractCall,
          abstractSequence,
          abstractPrimitive,
          abstractPrimitive.getSituation(),
          initializedModes,
          fixedConcretePrimitive
          );

      processInitializer(engineContext, initializer);
    }

    private IsaPrimitive findConcretePrimitive(
        final Primitive abstractPrimitive,
        final IsaPrimitive concretePrimitive) {
      InvariantChecks.checkNotNull(abstractPrimitive);
      InvariantChecks.checkNotNull(concretePrimitive);

      if (abstractPrimitive.getName().equals(concretePrimitive.getName())) {
        return concretePrimitive;
      }

      for (final IsaPrimitive concreteArgument : concretePrimitive.getArguments().values()) {
        final IsaPrimitive result = findConcretePrimitive(abstractPrimitive, concreteArgument);
        if (null != result) {
          return result;
        }
      }

      return null;
    }

    private void processInitializer(
        final EngineContext engineContext,
        final List<AbstractCall> abstractCalls) throws ConfigurationException {
      InvariantChecks.checkNotNull(engineContext);
      InvariantChecks.checkNotNull(abstractCalls);

      final List<ConcreteCall> concreteCalls =
          EngineUtils.makeConcreteCalls(engineContext, abstractCalls);

      final ConcreteSequence.Builder builder =
          new ConcreteSequence.Builder(abstractSequence.getSection());

      builder.add(concreteCalls);
      final ConcreteSequence concreteSequence = builder.build();

      testSequenceBuilder.addToPrologue(concreteCalls);

      final LocationAccessor programCounter = engineContext.getModel().getPE().accessLocation("PC");
      final BigInteger programCounterValue = programCounter.getValue();

      if (!concreteCalls.isEmpty()) {
        Logger.debug("Executing initializing code...");
      }

      try {
        execute(
            engineContext,
            listenerForInitializers,
            getAllocationAddress(),
            concreteSequence,
            sequenceIndex
            );
      } finally {
        programCounter.setValue(programCounterValue);
        setAllocationAddress(listenerForInitializers.getAllocationAddress());
        Logger.debug("");
      }
    }
  }

  private static List<SelfCheck> createSelfChecks(final List<AbstractCall> abstractSequence) {
    InvariantChecks.checkNotNull(abstractSequence);

    final Set<AddressingModeWrapper> modes = EngineUtils.getOutAddressingModes(abstractSequence);
    final List<SelfCheck> selfChecks = new ArrayList<>(modes.size());

    for (final AddressingModeWrapper mode : modes) {
      selfChecks.add(new SelfCheck(mode));
    }

    return selfChecks;
  }
}
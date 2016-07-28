/*
 * Copyright 2014-2015 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.fortress.util.Pair;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.model.api.exception.ConfigurationException;
import ru.ispras.microtesk.model.api.state.LocationAccessor;
import ru.ispras.microtesk.model.api.state.ModelStateObserver;
import ru.ispras.microtesk.model.api.tarmac.Tarmac;
import ru.ispras.microtesk.model.api.tarmac.Record;
import ru.ispras.microtesk.test.sequence.engine.EngineContext;
import ru.ispras.microtesk.test.sequence.engine.utils.EngineUtils;
import ru.ispras.microtesk.test.template.ConcreteCall;
import ru.ispras.microtesk.test.template.DataSection;
import ru.ispras.microtesk.test.template.Label;
import ru.ispras.microtesk.test.template.LabelReference;
import ru.ispras.microtesk.test.template.Output;

/**
 * The role of the Executor class is to execute (simulate) sequences of instruction calls (concrete
 * calls). It executes instruction by instruction, perform control transfers by labels (if needed)
 * and prints information about important events to the simulator log (currently, the console).
 * 
 * @author <a href="mailto:andrewt@ispras.ru">Andrei Tatarnikov</a>
 */
final class Executor {
  private final EngineContext context;
  private Map<String, List<ConcreteCall>> exceptionHandlers;

  /**
   * Constructs an Executor object.
   * 
   * @param logExecution Specifies whether printing to the simulator log is enabled.
   * 
   * @throws IllegalArgumentException if the {@code observer} parameter is {@code null}.
   */
  public Executor(final EngineContext context) {
    InvariantChecks.checkNotNull(context);

    this.context = context;
    this.exceptionHandlers = null;
  }

  private ModelStateObserver getStateObserver() {
    return context.getModel().getStateObserver();
  }

  private LocationAccessor getPC() throws ConfigurationException {
    return getStateObserver().accessLocation("PC");
  }

  public void setExceptionHandlers(final Map<String, List<ConcreteCall>> handlers) {
    InvariantChecks.checkNotNull(handlers);
    this.exceptionHandlers = handlers;
  }

  /**
   * Executes the specified sequence of instruction calls (concrete calls) and prints information
   * about important events to the simulator log.
   * 
   * @param sequence Sequence of executable (concrete) instruction calls.
   * @param sequenceIndex
   * @param abortOnUndefinedLabel Aborts generation when a reference to an undefined label is
   *        detected. This is the default behavior, which can changed in special cases (e.g.
   *        self-checks that contain references to labels defined in epilogue).
   * 
   * @throws IllegalArgumentException if the parameter is {@code null}.
   * @throws ConfigurationException if during the interaction with the microprocessor model an error
   *         caused by an invalid format of the request has occurred (typically, it happens when
   *         evaluating an {@link Output} object causes an invalid request to the model state
   *         observer).
   */
  public List<ConcreteCall> execute(
      final List<ConcreteCall> externalCode,
      final TestSequence sequence,
      final int index,
      final boolean abortOnUndefinedLabel) {
    try {
      InvariantChecks.checkNotNull(externalCode);
      InvariantChecks.checkNotNull(sequence);
      return executeSequence(externalCode, sequence, index, abortOnUndefinedLabel);
    } catch (final ConfigurationException e) {
      final java.io.StringWriter writer = new java.io.StringWriter();
      e.printStackTrace(new java.io.PrintWriter(writer));
      throw new GenerationAbortedException(String.format(
          "Simulation failed: %s%n%s", e.getMessage(), writer));
    }
  }

  private List<ConcreteCall> executeSequence(
      final List<ConcreteCall> externalCode,
      final TestSequence sequence,
      final int sequenceIndex,
      final boolean abortOnUndefinedLabel) throws ConfigurationException {
    if (sequence.isEmpty()) {
      return Collections.emptyList();
    }

    final LabelManager labelManager = new LabelManager(context.getLabelManager());

    final List<ConcreteCall> sequenceCode =
        expandDataSections(sequence.getAll(), labelManager, sequenceIndex);

    final Map<Long, Integer> addressMap = new LinkedHashMap<>();
    final List<ConcreteCall> calls = new ArrayList<>();

    registerCalls(calls, addressMap, labelManager, externalCode, sequenceIndex);
    registerCalls(calls, addressMap, labelManager, sequenceCode, sequenceIndex);

    final int startIndex = externalCode.size();
    final int endIndex = calls.size() - 1;

    final Map<String, Long> exceptionHandlerAddresses = new HashMap<>();
    registerExceptionHandlers(calls, labelManager, addressMap, exceptionHandlers, exceptionHandlerAddresses);

    patchLabels(sequenceCode, labelManager, addressMap, abortOnUndefinedLabel);
    // TODO: patch labels in exception handler code (need refactoring to have it in separate collection)

    if (TestSettings.isSimulationDisabled()) {
      logText("Simulation is disabled");
      return sequenceCode;
    }

    context.getStatistics().pushActivity(Statistics.Activity.SIMULATING);
    executeCalls(new ExecutorCode(calls, addressMap), exceptionHandlerAddresses, startIndex, endIndex);
    context.getStatistics().popActivity();

    return sequenceCode;
  }

  private void executeCalls(
      final ExecutorCode code,
      final Map<String, Long> exceptionHandlerAddresses,
      final int startIndex,
      final int endIndex) throws ConfigurationException {
    List<LabelReference> labelRefs = null;
    int labelRefsIndex = 0;
    int index = startIndex;

    // Number of non-executable instructions between labelRefsIndex and index (in delay slot)
    int nonExecutableCount = 0; 

    final int branchExecutionLimit = TestSettings.getBranchExecutionLimit();

    final ConcreteCall invalidCall =
        EngineUtils.makeSpecialConcreteCall(context, "invalid_instruction");

    while (code.isInBounds(index) ||
           null != invalidCall && invalidCall.getExecutionCount() == 0) {

      final ConcreteCall call =
          code.isInBounds(index) ? code.getCall(index) : invalidCall;

      if (call != invalidCall) {
        final long address = call.getAddress();
        getPC().setValue(BigInteger.valueOf(address));
      }

      if (branchExecutionLimit > 0 && call.getExecutionCount() >= branchExecutionLimit) {
        throw new GenerationAbortedException(String.format(
            "Instruction %s reached its limit on execution count (%d). " +
            "Probably, the program entered an endless loop. Generation was aborted.",
            call.getText(), branchExecutionLimit));
      }

      if (call.getOrigin() != null) {
        logText(String.format(TestSettings.getOriginFormat(), call.getOrigin()));
      }

      if (call.getAlignment() != null) {
        logText(String.format(TestSettings.getAlignFormat(), call.getAlignment()));
      }

      logOutputs(call.getOutputs());
      logLabels(call.getLabels());

      if (invalidCall != call && null != call.getText()) {
        logText(String.format("0x%016x %s", call.getAddress(), call.getText()));
      }

      if (!call.isExecutable()) {
        if (index == endIndex) break;
        index++;
        nonExecutableCount++;
        continue;
      }

      if (labelRefs != null) {
        // nonExecutableCount is excluded from number of instructions presumably in delay slot
        final int delta = index - labelRefsIndex - nonExecutableCount;
        if ((delta < 0) || (delta > context.getDelaySlotSize())) {
          labelRefs = null;
          labelRefsIndex = 0;
          nonExecutableCount = 0;
        }
      }

      if (!call.getLabelReferences().isEmpty()) {
        labelRefs = call.getLabelReferences();
        labelRefsIndex = index;
        nonExecutableCount = 0;
      }

      Tarmac.setEnabled(true);
      if (invalidCall != call) {
        logCall(call);
      }

      final String exception = call.execute();
      Tarmac.setEnabled(false);

      if (null == exception) {
        // NORMAL EXECUTION

        final long address = getPC().getValue().longValue();
        final boolean isJump = address != call.getAddress() + call.getByteSize();

        if (!isJump) {
          // If there are no transfers, continue to the next instruction if there is such.
          if (index == endIndex) break;
          index = getNextCallIndex(code, index, null != invalidCall); 
          continue;
        }

        if (null != labelRefs && !labelRefs.isEmpty()) {
          final LabelReference reference = labelRefs.get(0);
          final LabelReference.Target target = reference.getTarget();

          // Resets labels to jump (they are no longer needed after being used).
          labelRefs = null;

          if (null != target) {
            index = target.getPosition();
            final long nextAddress = code.getCallAddress(index);
            logText(String.format("Jump to label %s: 0x%x", target.getLabel().getUniqueName(), nextAddress));
            continue;
          }

          throw new GenerationAbortedException(String.format(
              "Label '%s' passed to '%s' (0x%x) is not defined or%n" +
              "is not accessible in the scope of the current test sequence.",
              reference.getReference().getName(), call.getText(), call.getAddress()));
        }

        // If no label references are found within the delay slot we try to use PC to jump
        final long nextAddress = getPC().getValue().longValue();
        if (code.hasAddress(nextAddress)) {
          logText(String.format("Jump to address: 0x%x", nextAddress));
          final int nextIndex = code.getCallIndex(nextAddress);
          index = nextIndex;
        } else if (null != invalidCall) {
          index = -1;
        } else {
          throw new GenerationAbortedException(String.format(
              "Simulation error. There is no executable code at 0x%x", nextAddress));
        }
      } else {
        // EXCEPTION IS DETECTED

        // Resets labels to jump (they are no longer needed after being used).
        labelRefs = null;

        if (exceptionHandlerAddresses.containsKey(exception)) {
          final long handlerAddress = exceptionHandlerAddresses.get(exception);
          final int handlerIndex = code.getCallIndex(handlerAddress);

          logText(String.format("Jump to exception handler for %s: 0x%x", exception, handlerAddress));
          index = handlerIndex;
        } else if (call == invalidCall) {
          Logger.error(
              "Exception handler for %s is not found. Execution will be terminated.", exception);
        } else {
          Logger.error("Exception handler for %s is not found. " + MSG_HAVE_TO_CONTINUE, exception);
          if (index == endIndex) break;
          index = getNextCallIndex(code, index, null != invalidCall);
        }
      }
    }
  }

  private static int getNextCallIndex(
      final ExecutorCode code,
      final int currentIndex,
      final boolean isInvalidCallHandled) {
    InvariantChecks.checkNotNull(code);
    InvariantChecks.checkGreaterOrEqZero(currentIndex);

    final ConcreteCall currentCall = code.getCall(currentIndex);
    final long nextAddress = currentCall.getAddress() + currentCall.getByteSize();

    final int nextIndex = currentIndex + 1;
    final ConcreteCall nextCall = code.getCall(nextIndex);

    if (nextCall.getAddress() == nextAddress) {
      return nextIndex;
    }

    // If the next call is aligned, it is not a problem.
    if (null != nextCall.getAlignment() && null == nextCall.getOrigin()) {
      return nextIndex;
    }

    if (!isInvalidCallHandled) {
      throw new GenerationAbortedException(String.format(
          "Simulation error. There is no executable code at 0x%x", nextAddress));
    }

    return -1;
  }

  private void logCall(final ConcreteCall call) {
    if (!call.isExecutable()) {
      return;
    }

    context.getStatistics().incTraceLength();
    if (Tarmac.isEnabled()) {
      Tarmac.addRecord(Record.newInstruction(call));
    }
  }

  private List<ConcreteCall> expandDataSections(
      final List<ConcreteCall> calls,
      final LabelManager labelManager,
      final int sequenceIndex) {
    context.getDataManager().setTestCaseIndex(sequenceIndex);

    final List<ConcreteCall> result = new ArrayList<>();
    for (final ConcreteCall call : calls) {
      if (call.getData() != null) {
        final DataSection data = call.getData();
        context.getDataManager().processData(labelManager, data);

        for (final Pair<Label, BigInteger> labelInfo : data.getLabelsWithAddresses()) {
          final Label label = labelInfo.first;
          final long address = labelInfo.second.longValue();

          label.setSequenceIndex(sequenceIndex);
          labelManager.addLabel(label, address);
        }

        continue;
      }

      result.add(call);
    }

    return result;
  }

  private static void registerCalls(
      final List<ConcreteCall> calls,
      final Map<Long, Integer> addressMap,
      final LabelManager labelManager,
      final List<ConcreteCall> sequence,
      final int sequenceIndex) {
    for (final ConcreteCall call : sequence) {
      calls.add(call);

      final int index = calls.size() - 1;
      registerCallAddress(addressMap, calls, call, index);

      final long address = call.getAddress();
      labelManager.addAllLabels(call.getLabels(), address, sequenceIndex);
    }
  }

  private static void registerCallAddress(
      final Map<Long, Integer> addressMap,
      final List<ConcreteCall> calls,
      final ConcreteCall call,
      final int index) {
    final long address = call.getAddress();
    if (!addressMap.containsKey(address)) {
      addressMap.put(address, index);
      return;
    }

    final int conflictIndex = addressMap.get(address);
    final ConcreteCall conflictCall = calls.get(conflictIndex);

    // Several calls can be mapped to the same address when a pseudo call with
    // zero size (label, compiler directive, text output etc.) precedes
    // an executable (non-zero size) call. This is considered normal. When such
    // a situation is detected, address mapping is not changed in order to allow
    // jumping on the beginning of calls mapped to the same address.

    boolean isConflictLegal = false;

    // Tests whether all calls mapped to the given address and preceding the current
    // call (ending with the conflict call) are zero-size. If the condition holds,
    // this is considered legal.
    for (int testIndex = index - 1; testIndex >= 0; --testIndex) {
      final ConcreteCall testCall = calls.get(testIndex);

      if (testCall.getByteSize() > 0 || testCall.getAddress() != address) {
        break;
      }

      if (testIndex == conflictIndex) {
        isConflictLegal = true;
        break;
      }
    }

    if (!isConflictLegal) {
      Logger.error(
          "Mapping '%s' (index %d): Address 0x%x is already used by '%s' (index %d).",
          call.getText(), index, address, conflictCall.getText(), conflictIndex);
    }
  }

  private static void registerExceptionHandlers(
      final List<ConcreteCall> calls,
      final LabelManager labelManager,
      final Map<Long, Integer> addressMap,
      final Map<String, List<ConcreteCall>> exceptionHandlers,
      final Map<String, Long> exceptionHandlerAddresses) {
    if (exceptionHandlers != null) {
      final Set<Object> handlerSet = new HashSet<>();
      for (final Map.Entry<String, List<ConcreteCall>> e : exceptionHandlers.entrySet()) {
        final String handlerName = e.getKey();
        final List<ConcreteCall> handlerCalls = e.getValue();
 
        if (handlerCalls.isEmpty()) {
          Logger.warning("Empty exception handler: %s", handlerName);
          continue;
        }

        if (handlerSet.contains(handlerCalls)) {
          continue;
        }

        exceptionHandlerAddresses.put(handlerName, handlerCalls.get(0).getAddress());
        registerCalls(calls, addressMap, labelManager, handlerCalls, Label.NO_SEQUENCE_INDEX);

        handlerSet.add(handlerCalls);
      }
    }
  }

  private static void patchLabels(
      final List<ConcreteCall> calls,
      final LabelManager labelManager,
      final Map<Long, Integer> addressMap,
      final boolean abortOnUndefined) {
    // Resolves all label references and patches the instruction call text accordingly.
    for (final ConcreteCall call : calls) {
      for (final LabelReference labelRef : call.getLabelReferences()) {
        labelRef.resetTarget();

        final Label source = labelRef.getReference();
        final LabelManager.Target target = labelManager.resolve(source);

        final String uniqueName;
        final String searchPattern;
        final String patchedText;

        if (null != target) { // Label is found
          uniqueName = target.getLabel().getUniqueName();
          final long address = target.getAddress();

          // For code labels
          if (addressMap.containsKey(address)) {
            final int index = addressMap.get(address);
            labelRef.setTarget(target.getLabel(), index);
          }

          if (null != labelRef.getArgumentValue()) {
            searchPattern = String.format("<label>%d", labelRef.getArgumentValue());
          } else {
            labelRef.getPatcher().setValue(BigInteger.ZERO);
            searchPattern = "<label>0";
          }

          patchedText = call.getText().replace(searchPattern, uniqueName);
          labelRef.getPatcher().setValue(BigInteger.valueOf(address));
        } else { // Label is not found
          if (abortOnUndefined) {
            throw new GenerationAbortedException(String.format(
                "Label '%s' passed to '%s' (0x%x) is not defined or%n" +
                "is not accessible in the scope of the current test sequence.",
                source.getName(), call.getText(), call.getAddress()));
          }

          uniqueName = source.getName();
          searchPattern = "<label>0";

          patchedText = call.getText().replace(searchPattern, uniqueName);
        }

        call.setText(patchedText);
      }

      // Kill all unused "<label>" markers.
      if (null != call.getText()) {
        call.setText(call.getText().replace("<label>", ""));
      }
    }
  }

  /**
   * Evaluates and prints the collection of {@link Output} objects.
   * 
   * @param o List of {@link Output} objects.
   * 
   * @throws IllegalArgumentException if the parameter is {@code null}.
   * @throws ConfigurationException if failed to evaluate the information in an Output
   *         object due to an incorrect request to the model state observer.
   */
  private void logOutputs(
      final List<Output> outputs) throws ConfigurationException {
    InvariantChecks.checkNotNull(outputs);

    for (final Output output : outputs) {
      if (output.isRuntime()) {
        logText(output.evaluate(getStateObserver()));
      }
    }
  }

  private void logLabels(final List<Label> labels) {
    InvariantChecks.checkNotNull(labels);
    for (final Label label : labels) {
      logText(label.getUniqueName() + ":");
    }
  }

  /**
   * Prints the text to the simulator log if logging is enabled.
   * 
   * @param text Text to be printed.
   */
  private void logText(final String text) {
    if (text != null) {
      Logger.debug(text);
    }
  }

  private static final String MSG_HAVE_TO_CONTINUE =
      "Have to continue to the next instruction.";
}

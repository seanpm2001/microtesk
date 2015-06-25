/*
 * Copyright 2009-2015 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.test.sequence.engine;

import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.allocateModes;
import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.getTestData;
import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.makeConcreteCall;
import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.makeStreamInit;
import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.makeStreamRead;
import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.makeStreamWrite;
import static ru.ispras.microtesk.test.sequence.engine.common.EngineUtils.setUnknownImmValues;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.ispras.fortress.data.types.bitvector.BitVector;
import ru.ispras.fortress.expression.Node;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.model.api.ArgumentMode;
import ru.ispras.microtesk.model.api.exception.ConfigurationException;
import ru.ispras.microtesk.test.TestSequence;
import ru.ispras.microtesk.test.sequence.Sequence;
import ru.ispras.microtesk.test.sequence.engine.branch.BranchEntry;
import ru.ispras.microtesk.test.sequence.engine.branch.BranchExecution;
import ru.ispras.microtesk.test.sequence.engine.branch.BranchStructure;
import ru.ispras.microtesk.test.sequence.engine.branch.BranchTrace;
import ru.ispras.microtesk.test.sequence.engine.common.TestBaseQueryCreator;
import ru.ispras.microtesk.test.template.Argument;
import ru.ispras.microtesk.test.template.Call;
import ru.ispras.microtesk.test.template.ConcreteCall;
import ru.ispras.microtesk.test.template.Primitive;
import ru.ispras.microtesk.test.template.Situation;
import ru.ispras.microtesk.test.testbase.BranchDataGenerator;
import ru.ispras.microtesk.utils.FortressUtils;
import ru.ispras.testbase.TestData;

/**
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class BranchAdapter implements Adapter<BranchSolution> {
  public static final String TEST_DATA_PREFIX = "branch_data_";
  public static final boolean USE_DELAY_SLOTS = true;

  @Override
  public Class<BranchSolution> getSolutionClass() {
    return BranchSolution.class;
  }

  @Override
  public AdapterResult adapt(
      final EngineContext engineContext,
      final Sequence<Call> abstractSequence,
      final BranchSolution solution) {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(abstractSequence);
    InvariantChecks.checkNotNull(solution);

    final BranchStructure branchStructure = solution.getBranchStructure();
    InvariantChecks.checkTrue(abstractSequence.size() == branchStructure.size());

    // Allocate uninitialized addressing modes.
    allocateModes(abstractSequence);

    final TestSequence.Builder testSequenceBuilder = new TestSequence.Builder();

    // Maps branch indices to control code.
    final Map<Integer, Sequence<Call>> steps = new LinkedHashMap<>();
    // Contains positions of the delay slots.
    final Set<Integer> delaySlots = new HashSet<>();

    // Construct the control code to enforce the given execution trace.
    int branchNumber = 0;

    for (int i = 0; i < abstractSequence.size(); i++) {
      final Call abstractCall = abstractSequence.get(i);
      final BranchEntry branchEntry = branchStructure.get(i);

      if (!branchEntry.isIfThen()) {
        continue;
      }

      final String testDataArray = String.format("%s%d", TEST_DATA_PREFIX, branchNumber);
      branchNumber++;

      final BranchTrace branchTrace = branchEntry.getBranchTrace();
      final Set<Integer> blockCoverage = branchEntry.getBlockCoverage();
      final Set<Integer> slotCoverage = branchEntry.getSlotCoverage();

      final List<Call> controlCode = makeStreamRead(engineContext, testDataArray);

      boolean isInserted = false;

      // Insert the control code into the basic block if it is possible.
      if (!isInserted && blockCoverage != null) {
        for (final int block : blockCoverage) {
          Sequence<Call> step = steps.get(block);
          if (step == null) {
            steps.put(block, step = new Sequence<Call>());
          }

          step.addAll(controlCode);
          isInserted = true;
        }
      }

      boolean isBasicBlock = isInserted;

      // Insert the control code into the delay slot if it is possible.
      if (USE_DELAY_SLOTS && !isInserted && slotCoverage != null) {
        if (controlCode.size() <= engineContext.getDelaySlotSize()) {
          final int slotPosition = i + 1;

          Sequence<Call> step = steps.get(slotPosition);
          if (step == null) {
            steps.put(slotPosition, step = new Sequence<Call>());
          }

          delaySlots.add(slotPosition);

          step.addAll(controlCode);
          isInserted = true;
        }
      }

      if (!isInserted) {
        return new AdapterResult("Cannot construct the control code");
      }

      try {
        updatePrologue(
            engineContext,
            testSequenceBuilder,
            abstractCall,
            branchTrace,
            isBasicBlock,
            testDataArray);
      } catch (final ConfigurationException e) {
        return new AdapterResult("Cannot convert the abstract sequence into the concrete one");
      }
    }

    // Insert the control code into the sequence.
    int correction = 0;

    final Sequence<Call> modifiedSequence = new Sequence<Call>();
    modifiedSequence.addAll(abstractSequence);

    for (final Map.Entry<Integer, Sequence<Call>> entry : steps.entrySet()) {
      final int position = entry.getKey();
      final Sequence<Call> controlCode = entry.getValue();

      modifiedSequence.addAll(position + correction, controlCode);

      if (delaySlots.contains(position)) {
        // Remove the old delay slot.
        for (int i = 0; i < controlCode.size(); i++) {
          modifiedSequence.remove(position + correction + controlCode.size());
        }
      } else {
        // Update the correction offset.
        correction += controlCode.size();
      }
    }

    try {
      updateBody(engineContext, testSequenceBuilder, modifiedSequence);
    } catch (final ConfigurationException e) {
      // Cannot convert the abstract code into the concrete code.
      return new AdapterResult("Cannot convert the abstract sequence into the concrete one");
    }

    Logger.debug("%nReturn the test sequence");
    return new AdapterResult(testSequenceBuilder.build());
  }

  private void updatePrologue(
      final EngineContext engineContext,
      final TestSequence.Builder testSequenceBuilder,
      final Call abstractCall)
          throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testSequenceBuilder);
    InvariantChecks.checkNotNull(abstractCall);

    final ConcreteCall concreteCall = makeConcreteCall(engineContext, abstractCall);
    testSequenceBuilder.addToPrologue(concreteCall);
  }

  private void updatePrologue(
      final EngineContext engineContext,
      final TestSequence.Builder testSequenceBuilder,
      final List<Call> abstractSequence)
          throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testSequenceBuilder);
    InvariantChecks.checkNotNull(abstractSequence);

    for (final Call abstractCall : abstractSequence) {
      updatePrologue(engineContext, testSequenceBuilder, abstractCall);
    }
  }

  private void updatePrologue(
      final EngineContext engineContext,
      final TestSequence.Builder testSequenceBuilder,
      final Call abstractCall,
      final boolean branchCondition,
      final String testDataArray)
        throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testSequenceBuilder);
    InvariantChecks.checkNotNull(abstractCall);
    InvariantChecks.checkNotNull(testDataArray);

    final Primitive primitive = abstractCall.getRootOperation();
    InvariantChecks.checkNotNull(primitive);

    final Situation situation = primitive.getSituation();
    InvariantChecks.checkNotNull(situation);

    // Specify the situation's parameter (branch condition).
    situation.getAttributes().put(BranchDataGenerator.PARAM_CONDITION,
        branchCondition ?
            BranchDataGenerator.PARAM_CONDITION_THEN :
            BranchDataGenerator.PARAM_CONDITION_ELSE);

    final TestBaseQueryCreator queryCreator =
        new TestBaseQueryCreator(engineContext, situation, primitive);

    final TestData testData = getTestData(engineContext, primitive, queryCreator);
    Logger.debug(testData.toString());

    // Set unknown immediate values (if there are any).
    setUnknownImmValues(queryCreator.getUnknownImmValues(), testData);

    // Initialize test data to ensure branch execution.
    for (final Map.Entry<String, Node> testDatum : testData.getBindings().entrySet()) {
      final String name = testDatum.getKey();
      final Argument argument = queryCreator.getModes().get(name);

      if (argument.getKind() != Argument.Kind.MODE || argument.getMode() == ArgumentMode.OUT) {
        continue;
      }

      final BitVector value = FortressUtils.extractBitVector(testDatum.getValue());
      final List<Call> writeDataStream = makeStreamWrite(engineContext, testDataArray, value);

      updatePrologue(engineContext, testSequenceBuilder, writeDataStream);
    }
  }

  private void updatePrologue(
      final EngineContext engineContext,
      final TestSequence.Builder testSequenceBuilder,
      final Call abstractBranchCall,
      final BranchTrace branchTrace,
      final boolean controlCodeInBasicBlock,
      final String testDataArray)
        throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testSequenceBuilder);
    InvariantChecks.checkNotNull(abstractBranchCall);

    final List<Call> initDataStream = makeStreamInit(engineContext, testDataArray);
    updatePrologue(engineContext, testSequenceBuilder, initDataStream);

    for (int i = 0; i < branchTrace.size(); i++) {
      final BranchExecution execution = branchTrace.get(i);

      final boolean branchCondition = execution.value();

      final int count = controlCodeInBasicBlock ?
          execution.getBlockCoverageCount() : execution.getSlotCoverageCount();

      for (int j = 0; j < count; j++) {
        updatePrologue(
            engineContext,
            testSequenceBuilder,
            abstractBranchCall,
            branchCondition,
            testDataArray);
      }
    }

    updatePrologue(engineContext, testSequenceBuilder, initDataStream);
  }

  private void updateBody(
      final EngineContext engineContext,
      final TestSequence.Builder testSequenceBuilder,
      final Call abstractCall)
          throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testSequenceBuilder);
    InvariantChecks.checkNotNull(abstractCall);

    final ConcreteCall concreteCall = makeConcreteCall(engineContext, abstractCall);
    testSequenceBuilder.add(concreteCall);
  }

  private void updateBody(
      final EngineContext engineContext,
      final TestSequence.Builder testSequenceBuilder,
      final Sequence<Call> abstractSequence)
          throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testSequenceBuilder);
    InvariantChecks.checkNotNull(abstractSequence);

    for (final Call abstractCall : abstractSequence) {
      updateBody(engineContext, testSequenceBuilder, abstractCall);
    }
  }
}

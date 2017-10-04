/*
 * Copyright 2015-2017 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.mmu.test.engine.memory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.ispras.fortress.data.Data;
import ru.ispras.fortress.data.DataType;
import ru.ispras.fortress.data.Variable;
import ru.ispras.fortress.expression.Node;
import ru.ispras.fortress.expression.NodeOperation;
import ru.ispras.fortress.expression.NodeValue;
import ru.ispras.fortress.expression.NodeVariable;
import ru.ispras.fortress.expression.StandardOperation;
import ru.ispras.fortress.transformer.ValueProvider;
import ru.ispras.fortress.util.BitUtils;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.basis.solver.integer.IntegerConstraint;
import ru.ispras.microtesk.basis.solver.integer.IntegerDomainConstraint;
import ru.ispras.microtesk.basis.solver.integer.IntegerFormulaBuilderSimple;
import ru.ispras.microtesk.basis.solver.integer.IntegerRange;
import ru.ispras.microtesk.basis.solver.integer.IntegerRangeConstraint;
import ru.ispras.microtesk.mmu.MmuPlugin;
import ru.ispras.microtesk.mmu.basis.BufferAccessEvent;
import ru.ispras.microtesk.mmu.basis.MemoryAccessContext;
import ru.ispras.microtesk.mmu.basis.MemoryAccessStack;
import ru.ispras.microtesk.mmu.basis.MemoryDataType;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAction;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAddressInstance;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBinding;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBuffer;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBufferAccess;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuGuard;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuProgram;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuSegment;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuStruct;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuSubsystem;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuTransition;
import ru.ispras.microtesk.utils.FortressUtils;

/**
 * {@link SymbolicExecutor} implements a simple symbolic executor of memory access structures.
 * 
 * @author <a href="mailto:kamkin@ispras.ru">Alexander Kamkin</a>
 */
public final class SymbolicExecutor {
  private final SymbolicRestrictor restrictor;
  private final SymbolicResult result;

  public SymbolicExecutor(
      final SymbolicRestrictor restrictor,
      final SymbolicResult result) {
    InvariantChecks.checkNotNull(restrictor);
    InvariantChecks.checkNotNull(result);

    this.restrictor = restrictor;
    this.result = result;
  }

  public SymbolicExecutor(final SymbolicResult result) {
    InvariantChecks.checkNotNull(result);

    this.restrictor = null;
    this.result = result;
  }

  public SymbolicResult getResult() {
    return result;
  }

  public Boolean execute(final MemoryDataType dataType) {
    InvariantChecks.checkNotNull(dataType);
    return executeAlignment(result, null, dataType, -1);
  }

  public Boolean execute(final IntegerConstraint constraint) {
    InvariantChecks.checkNotNull(constraint);
    return executeFormula(result, null, constraint.getFormula(), -1);
  }

  public Boolean execute(final Node condition) {
    InvariantChecks.checkNotNull(condition);
    return executeCondition(result, null, condition, -1);
  }

  public Boolean execute(final AccessPath.Entry entry) {
    InvariantChecks.checkNotNull(entry);
    return executeEntry(result, null, entry, -1);
  }

  public Boolean execute(final Access access, final boolean finalize) {
    InvariantChecks.checkNotNull(access);

    final Boolean status = executeAccess(result, null, access, -1);

    if (finalize) {
      result.includeOriginalVariables();
    }

    return status;
  }

  public Boolean execute(final List<Access> structure, final boolean finalize) {
    InvariantChecks.checkNotNull(structure);
    InvariantChecks.checkGreaterThanZero(structure.size());

    final Boolean status = executeStructure(result, null, structure);

    if (finalize) {
      result.includeOriginalVariables();
    }

    return status;
  }

  private Boolean executeStructure(
      final SymbolicResult result,
      final Set<Variable> defines,
      final List<Access> structure) {

    for (int j = 0; j < structure.size(); j++) {
      final Access access2 = structure.get(j);

      for (int i = 0; i < j; i++) {
        final Access access1 = structure.get(i);
        final BufferDependency dependency = access2.getDependency(i);

        if (dependency != null) {
          // It does not execute the paths (only the dependency).
          executeDependency(result, defines, access1, i, access2, j, dependency);
        }
      }

      executeAccess(result, defines, access2, j);
    }

    return result.hasConflict() ? Boolean.FALSE : null;
  }

  private Boolean executeAccess(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Access access,
      final int pathIndex) {

    executeAlignment(result, defines, access.getType().getDataType(), pathIndex);
    return executePath(result, defines, access.getPath(), pathIndex);
  }

  private Boolean executePath(
      final SymbolicResult result,
      final Set<Variable> defines,
      final AccessPath path,
      final int pathIndex) {

    for (final AccessPath.Entry entry : path.getEntries()) {
      if (result.hasConflict()) {
        return Boolean.FALSE;
      }

      executeEntry(result, defines, entry, pathIndex);
    }

    return result.hasConflict() ? Boolean.FALSE : null;
  }

  private Boolean executeAlignment(
      final SymbolicResult result,
      final Set<Variable> defines,
      final MemoryDataType dataType,
      final int pathIndex) {

    final int lowerZeroBit = 0;
    final int upperZeroBit = dataType.getLowerAddressBit() - 1;

    if (upperZeroBit < 0) {
      return null;
    }

    final MmuAddressInstance addrType = MmuPlugin.getSpecification().getVirtualAddress();

    final Node field = FortressUtils.makeNodeExtract(
        addrType.getVariable(),
        lowerZeroBit,
        upperZeroBit);

    final IntegerConstraint constraint =
        new IntegerDomainConstraint(field, BigInteger.ZERO);

    return executeFormula(result, defines, constraint.getFormula(), pathIndex);
  }

  private Boolean executeDependency(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Access access1,
      final int pathIndex1,
      final Access access2,
      final int pathIndex2,
      final BufferDependency dependency) {

    for (final BufferHazard.Instance hazard : dependency.getHazards()) {
      if (result.hasConflict()) {
        return Boolean.FALSE;
      }

      executeHazard(result, defines, hazard, pathIndex1, pathIndex2);
    }

    return result.hasConflict() ? Boolean.FALSE : null;
  }

  private Boolean executeHazard(
      final SymbolicResult result, 
      final Set<Variable> defines,
      final BufferHazard.Instance hazard,
      final int pathIndex1,
      final int pathIndex2) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    final MmuBuffer buffer = hazard.getPrimaryAccess().getBuffer();

    final NodeOperation condition = (NodeOperation) hazard.getCondition();
    InvariantChecks.checkTrue(
           condition.getOperationId() == StandardOperation.AND
        || condition.getOperationId() == StandardOperation.OR);

    if (condition != null) {
      final MemoryAccessContext context1 = result.getContext(pathIndex1);
      final MemoryAccessContext context2 = result.getContext(pathIndex2);

      final List<Node> clauseBuilder = new ArrayList<>(condition.getOperandCount());

      for (final Node atom : condition.getOperands()) {
        final NodeOperation equality = (NodeOperation) atom;
        InvariantChecks.checkTrue(
               equality.getOperationId() == StandardOperation.EQ
            || equality.getOperationId() == StandardOperation.NOTEQ);

        final NodeOperation lhs = (NodeOperation) equality.getOperand(0);
        InvariantChecks.checkTrue(lhs.getOperationId() == StandardOperation.BVCONCAT);

        final NodeOperation rhs = (NodeOperation) equality.getOperand(1);
        InvariantChecks.checkTrue(rhs.getOperationId() == StandardOperation.BVCONCAT);

        InvariantChecks.checkTrue(lhs.getOperandCount() == rhs.getOperandCount());

        for (int i = 0; i < lhs.getOperandCount(); i++) {
          final NodeOperation term1 = (NodeOperation) lhs.getOperand(i);
          final NodeOperation term2 = (NodeOperation) rhs.getOperand(i);

          final String instanceId1 = MmuBufferAccess.getId(buffer, context1);
          final String instanceId2 = MmuBufferAccess.getId(buffer, context2);

          final Node instance1 = context1.getInstance(instanceId1, term1);
          final Node instance2 = context2.getInstance(instanceId2, term2);

          final Node field1 = result.getVersion(instance1, pathIndex1);
          final Node field2 = result.getVersion(instance2, pathIndex2);

          if (equality.getOperationId() == StandardOperation.EQ) {
            clauseBuilder.add(FortressUtils.makeNodeEqual(field1, field2));
          } else {
            clauseBuilder.add(FortressUtils.makeNodeNotEqual(field1, field2));
          }

          result.addOriginalVariable(
              result.getOriginal(FortressUtils.getVariable(instance1), pathIndex1));
          result.addOriginalVariable(
              result.getOriginal(FortressUtils.getVariable(instance2), pathIndex2));
        }
      }

      if (condition.getOperationId() == StandardOperation.AND) {
        result.addFormula(FortressUtils.makeNodeAnd(clauseBuilder));
      } else {
        result.addFormula(FortressUtils.makeNodeOr(clauseBuilder));
      }
    }

    return result.hasConflict() ? Boolean.FALSE : null;
  }

  private Boolean executeFormula(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Node formula,
      final int pathIndex) {
    result.addFormula(result.getVersion(formula, pathIndex));
    return result.hasConflict() ? Boolean.FALSE : null;
  }

  private void restrictTransition(
      final SymbolicResult result,
      final Set<Variable> defines,
      final boolean isStart,
      final MmuTransition transition,
      final int pathIndex) {
    final MemoryAccessContext context = result.getContext(pathIndex);

    if (restrictor != null) {
      final Collection<IntegerConstraint> constraints =
          restrictor.getConstraints(isStart, transition, context);

      for (final IntegerConstraint constraint : constraints) {
        executeFormula(result, defines, constraint.getFormula(), pathIndex);
      }
    }
  }

  private void restrictProgram(
      final SymbolicResult result,
      final Set<Variable> defines,
      final boolean isStart,
      final MmuProgram program,
      final int pathIndex) {
    final MemoryAccessContext context = result.getContext(pathIndex);

    if (restrictor != null) {
      final Collection<IntegerConstraint> constraints =
          restrictor.getConstraints(isStart, program, context);

      for (final IntegerConstraint constraint : constraints) {
        executeFormula(result, defines, constraint.getFormula(), pathIndex);
      }
    }
  }

  private Boolean executeEntry(
      final SymbolicResult result,
      final Set<Variable> defines,
      final AccessPath.Entry entry,
      final int pathIndex) {

    final MmuSubsystem memory = MmuPlugin.getSpecification();

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    result.accessBuffer(entry, pathIndex);

    final boolean isStart = entry.isStart();
    final MmuProgram program = entry.getProgram();
    final MemoryAccessContext context = result.getContext(pathIndex);

    Boolean status = Boolean.TRUE;

    switch(entry.getKind()) {
    case NORMAL:
      status = executeProgram(result, defines, program, pathIndex);
      restrictProgram(result, defines, isStart, program, pathIndex);

      return status;

    case CALL:
      InvariantChecks.checkTrue(program.isAtomic());
      final MmuTransition transition = program.getTransition();

      // Execute the action as usual.
      status = executeTransition(result, defines, transition, pathIndex, true, false);
      restrictTransition(result, defines, isStart, transition, pathIndex);

      // Compose bindings.
      final MmuAction action = transition.getTarget();

      final MmuBufferAccess oldBufferAccess = action.getBufferAccess(context);
      final MmuAddressInstance oldFormalArg = oldBufferAccess.getAddress();

      // Call.
      result.updateStack(entry, pathIndex);

      final MmuBufferAccess newBufferAccess = action.getBufferAccess(context);
      final MmuAddressInstance newFormalArg = newBufferAccess.getAddress();

      final Collection<MmuBinding> bindings1 = newFormalArg.bindings(oldFormalArg);
      executeBindings(result, defines, bindings1, pathIndex);

      // The new virtual address is equal to the buffer access address.
      final MmuAddressInstance virtualAddress = memory.getVirtualAddress();
      final MmuAddressInstance newVirtualAddress = virtualAddress.getInstance(null, context);

      final Collection<MmuBinding> bindings2 = newVirtualAddress.bindings(newFormalArg);
      executeBindings(result, defines, bindings2, pathIndex);

      return status;

    case RETURN:
      // The entry read from the buffer is the data read from the memory.
      final Variable preVariable = context.getInstance(null, memory.getDataVariable());

      // Return.
      final MemoryAccessStack.Frame frame = result.updateStack(entry, pathIndex);

      final MmuTransition callTransition = frame.getTransition();
      final MmuAction callAction = callTransition.getTarget();

      final MmuBufferAccess postBufferAccess = callAction.getBufferAccess(context);

      final MmuStruct postEntry = postBufferAccess.getEntry();
      final List<Variable> postFields = postEntry.getFields();

      int bit = 0;
      final Collection<MmuBinding> bindings = new ArrayList<>();

      // Reverse order.
      for (int i = postFields.size() - 1; i >= 0; i--) {
        final Variable postVariable = postFields.get(i);
        final int sizeInBits = postVariable.getType().getSize() - 1;

        final NodeOperation postField = new NodeOperation(
            StandardOperation.BVEXTRACT,
            new NodeVariable(postVariable),
            new NodeValue(Data.newInteger(0)),
            new NodeValue(Data.newInteger(sizeInBits - 1)));

        final NodeOperation preField = new NodeOperation(
            StandardOperation.BVEXTRACT,
            new NodeVariable(preVariable),
            new NodeValue(Data.newInteger(0)),
            new NodeValue(Data.newInteger((bit + sizeInBits) - 1)));

        // Buffer.Entry = Memory.DATA.
        final MmuBinding binding = new MmuBinding(postField, preField);

        bindings.add(binding);
        bit += sizeInBits;
      }

      executeBindings(result, defines, bindings, pathIndex);

      // Variables=Buffer.Entry.
      status = executeTransition(result, defines, callTransition, pathIndex, false, true);

      return status;

    default:
      return status;
    }
  }

  private Boolean executeProgram(
      final SymbolicResult result,
      final Set<Variable> defines,
      final MmuProgram program,
      final int pathIndex) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    if (program.isAtomic()) {
      return executeTransition(result, defines, program.getTransition(), pathIndex, true, true);
    }

    for (final Collection<MmuProgram> statement : program.getStatements()) {
      if (result.hasConflict()) {
        return Boolean.FALSE;
      }

      executeStatement(result, defines, statement, pathIndex);
    }

    return result.hasConflict() ? Boolean.FALSE : null;
  }

  private Boolean executeStatement(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Collection<MmuProgram> statement,
      final int pathIndex) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    if (statement.isEmpty()) {
      result.setConflict(true);
      return Boolean.FALSE;
    }

    if (statement.size() == 1) {
      return executeProgram(result, defines, statement.iterator().next(), pathIndex);
    }

    final List<SymbolicResult> switchResults = new ArrayList<>(statement.size());
    final List<Set<Variable>> switchDefines = new ArrayList<>(statement.size());

    for (final MmuProgram program : statement) {
      final IntegerFormulaBuilderSimple caseBuilder = new IntegerFormulaBuilderSimple();
      final SymbolicResult caseResult = new SymbolicResult(caseBuilder, result);
      final Set<Variable> caseDefines = new LinkedHashSet<>();

      executeProgram(caseResult, caseDefines, program, pathIndex);

      if (!caseResult.hasConflict()) {
        switchResults.add(caseResult);
        switchDefines.add(caseDefines);
      }
    }

    if (switchResults.isEmpty()) {
      result.setConflict(true);
      return Boolean.FALSE;
    }

    final Set<Variable> allDefines = new LinkedHashSet<>();

    for (final Set<Variable> caseDefines : switchDefines) {
      allDefines.addAll(caseDefines);
    }

    if (defines != null) {
      defines.addAll(allDefines);
    }

    // Construct PHI functions.
    for (final Variable originalVariable : allDefines) {
      final List<Integer> indices = new ArrayList<>(switchDefines.size());

      int maxVersionNumber = 0;

      for (int i = 0; i < switchDefines.size(); i++) {
        final Set<Variable> caseDefines = switchDefines.get(i);

        if (caseDefines.contains(originalVariable)) {
          final SymbolicResult caseResult = switchResults.get(i);
          final int versionNumber = caseResult.getVersionNumber(originalVariable);

          if (versionNumber > maxVersionNumber) {
            maxVersionNumber = versionNumber;
          }

          indices.add(i);
        }
      }

      for (final int i : indices) {
        final SymbolicResult caseResult = switchResults.get(i);
        final int versionNumber = caseResult.getVersionNumber(originalVariable);

        if (versionNumber < maxVersionNumber) {
          final Variable oldVersion = caseResult.getVersion(originalVariable);
          caseResult.setVersionNumber(originalVariable, maxVersionNumber);
          final Variable newVersion = caseResult.getVersion(originalVariable);

          caseResult.addFormula(
              FortressUtils.makeNodeEqual(
                  FortressUtils.makeNodeVariable(newVersion),
                  FortressUtils.makeNodeVariable(oldVersion)));
        }
      }

      result.setVersionNumber(originalVariable, maxVersionNumber);
    }

    if (switchResults.size() == 1) {
      // There is only one control flow.
      final SymbolicResult caseResult = switchResults.get(0);
      final IntegerFormulaBuilderSimple caseBuilder =
          (IntegerFormulaBuilderSimple) caseResult.getBuilder();
      final Node caseFormula = caseBuilder.build();

      result.addFormula(caseFormula);
      // TODO: Constant propagation can be optimized.
      result.getConstants().putAll(caseResult.getConstants());
    } else {
      // Join the control flows.
      final int width = getWidth(statement.size());
      final Node phi = getPhiField(width);

      final List<Node> switchBuilder = new ArrayList<>();

      // Switch: (PHI == 0) | ... | (PHI == N-1)
      for (int i = 0; i < switchResults.size(); i++) {
        switchBuilder.add(FortressUtils.makeNodeEqual(phi, FortressUtils.makeNodeValue(i)));
      }
  
      result.addFormula(FortressUtils.makeNodeOr(switchBuilder));

      for (int i = 0; i < switchResults.size(); i++) {
        final SymbolicResult caseResult = switchResults.get(i);
        final IntegerFormulaBuilderSimple caseBuilder =
            (IntegerFormulaBuilderSimple) caseResult.getBuilder();
        final Node caseFormula = caseBuilder.build();

        // Case: (PHI == i) -> CASE(i).
        final Node ifThenFormula = getIfThenFormula(phi, i, caseFormula);
        result.addFormula(ifThenFormula);
      }
    }

    return null;
  }

  private Boolean executeTransition(
      final SymbolicResult result,
      final Set<Variable> defines,
      final MmuTransition transition,
      final int pathIndex,
      final boolean executeCall,
      final boolean executeReturn) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    Boolean status = null;

    if (executeCall) {
      final MmuGuard guard = transition.getGuard();
      if (guard != null) {
        status = executeGuard(result, defines, guard, pathIndex);
      } else {
        status = Boolean.TRUE;
      }
    }

    if (status == Boolean.FALSE) {
      return status;
    }

    final MmuAction action = transition.getTarget();
    if (action != null) {
      executeAction(result, defines, action, pathIndex, executeCall, executeReturn);
    }

    return status;
  }

  
  
  private Boolean executeGuard(
      final SymbolicResult result,
      final Set<Variable> defines,
      final MmuGuard guard,
      final int pathIndex) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    Boolean status = Boolean.TRUE;

    final MemoryAccessContext context = result.getContext(pathIndex);
    final MmuBufferAccess bufferAccess = guard.getBufferAccess(context);
 
    // Buffer(Argument) => (Address == Argument).
    if (bufferAccess != null) {
      final MmuAddressInstance formalArg = bufferAccess.getAddress();
      final MmuAddressInstance actualArg = bufferAccess.getArgument();

      if (formalArg != null && actualArg != null) {
        final Collection<MmuBinding> bindings = formalArg.bindings(actualArg);
        executeBindings(result, defines, bindings, pathIndex);
      }

      if (bufferAccess.getEvent() == BufferAccessEvent.HIT) {
        final Collection<MmuBinding> bindings = bufferAccess.getMatchBindings();
        status = executeBindings(result, defines, bindings, pathIndex);
      }
    }

    if (status == Boolean.FALSE) {
      return status;
    }

    final MmuSegment segment = guard.getSegment();
    if (segment != null) {
      if (guard.isHit()) {
        final MmuAddressInstance address = segment.getVaType().getInstance(null, context);

        final IntegerRangeConstraint constraint =
            new IntegerRangeConstraint(address.getVariable(),
            new IntegerRange(segment.getMin(), segment.getMax()));

        status = executeFormula(result, defines, constraint.getFormula(), pathIndex);
      } else {
        // FIXME: Handle segment miss.
        // InvariantChecks.checkTrue(false);
      }
    }

    if (status == Boolean.FALSE) {
      return status;
    }

    final Node condition = guard.getCondition(null, context);
    if (condition != null) {
      status = executeCondition(result, defines, condition, pathIndex);
    }

    return status;
  }

  private Boolean executeAction(
      final SymbolicResult result,
      final Set<Variable> defines,
      final MmuAction action,
      final int pathIndex,
      final boolean executeCall,
      final boolean executeReturn) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    final MemoryAccessContext context = result.getContext(pathIndex);
    final MmuBufferAccess bufferAccess = action.getBufferAccess(context);

    if (executeCall) {
      // Buffer(Argument) => (Address == Argument).
      if (bufferAccess != null) {
        final MmuAddressInstance formalArg = bufferAccess.getAddress();
        final MmuAddressInstance actualArg = bufferAccess.getArgument();

        if (formalArg != null && actualArg != null) {
          final Collection<MmuBinding> bindings = formalArg.bindings(actualArg);
          executeBindings(result, defines, bindings, pathIndex);
        }
      }
    }

    if (executeReturn) {
      // Variable = Buffer(...).Entry => (Variable == Entry).
      final String lhsInstanceId =
          bufferAccess != null && bufferAccess.getEvent() == BufferAccessEvent.WRITE
            ? bufferAccess.getId() : null;

      final String rhsInstanceId =
          bufferAccess != null && bufferAccess.getEvent() == BufferAccessEvent.READ
            ? bufferAccess.getId() : null;

      final Map<Node, MmuBinding> assignments =
          action.getAssignments(lhsInstanceId, rhsInstanceId, context);

      if (assignments != null) {
        executeBindings(result, defines, assignments.values(), pathIndex);

        if (bufferAccess != null && bufferAccess.getEvent() == BufferAccessEvent.READ) {
          final MmuBuffer lhs = bufferAccess.getBuffer();
          final MmuStruct rhs = bufferAccess.getEntry();

          // Add special bindings: Buffer.Field = BufferAccess.Field.
          // This introduces variables {Buffer.Field} to be used from test templates.
          executeBindings(result, defines, lhs.bindings(rhs), pathIndex);
        }
      }
    }

    return null;
  }

  private Boolean executeCondition(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Node condition,
      final int pathIndex) {
    final NodeOperation clause = (NodeOperation) condition;
    InvariantChecks.checkTrue(
           clause.getOperationId() == StandardOperation.AND
        || clause.getOperationId() == StandardOperation.OR);

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    // Try to calculate the condition based on the derived constants.
    final Boolean value = FortressUtils.check(condition,
        new ValueProvider() {
          @Override
          public Data getVariableValue(final Variable original) {
            final Variable version = result.getVersion(original);
            return Data.newBitVector(result.getConstant(version), original.getType().getSize());
          }
        });

    if (value != null) {
      // If the result is false, there is a conflict.
      // If the result is true, the condition is redundant.
      result.setConflict(!value.booleanValue());
      return value;
    }

    final List<Node> clauseBuilder = new ArrayList<>();

    for (final Node atom : clause.getOperands()) {
      if (result.hasConflict()) {
        return Boolean.FALSE;
      }

      executeConditionAtom(result, defines, clause.getOperationId(), clauseBuilder, atom, pathIndex);
    }

    if (!clauseBuilder.isEmpty()) {
      if (clause.getOperationId() == StandardOperation.AND) {
        result.addFormula(FortressUtils.makeNodeAnd(clauseBuilder));
      } else {
        result.addFormula(FortressUtils.makeNodeOr(clauseBuilder));
      }
    }

    return null;
  }

  private Boolean executeConditionAtom(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Enum<?> operation,
      final List<Node> clauseBuilder,
      final Node atom,
      final int pathIndex) {
    final NodeOperation equality = (NodeOperation) atom;
    InvariantChecks.checkTrue(
           equality.getOperationId() == StandardOperation.EQ
        || equality.getOperationId() == StandardOperation.NOTEQ);

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    final Node lhs = equality.getOperand(0);
    final Node rhs = equality.getOperand(1);

    final NodeOperation lhsExpr = (NodeOperation) lhs;
    InvariantChecks.checkTrue(lhsExpr.getOperationId() == StandardOperation.BVCONCAT);

    if (rhs.getKind() == Node.Kind.VALUE) {
        boolean isTrue = false;

        final NodeValue rhsValue = (NodeValue) rhs;
        final BigInteger rhsConst = rhsValue.getInteger();

        int offset = 0;
        for (final Node term : lhsExpr.getOperands()) {
          final int lo = offset;
          final int hi = offset + (FortressUtils.getBitSize(term) - 1);

          final Node field = result.getVersion(term, pathIndex);
          final BigInteger value = BitUtils.getField(rhsConst, lo, hi);

          // Check whether the field's value is known (via constant propagation).
          final BigInteger constant = result.getConstants().get(FortressUtils.getVariable(field));
  
          if (constant != null) {
            final int fieldlowerBit = FortressUtils.getLowerBit(field);
            final int fieldUpperBit = FortressUtils.getUpperBit(field);

            final BigInteger fieldConst = BitUtils.getField(constant, fieldlowerBit, fieldUpperBit);

            final boolean truthValue =
                (value.equals(fieldConst) == (equality.getOperationId() == StandardOperation.EQ));

            if (!truthValue && operation == StandardOperation.AND) {
              // Condition is always false.
              result.setConflict(true);
              return Boolean.FALSE;
            }

            if (truthValue && operation == StandardOperation.OR) {
              // Condition is always true.
              // Formally, the empty OR clause is false, but it is simply ignored.
              clauseBuilder.clear();
              isTrue = true;
            }
          }

          if (!isTrue && constant == null) {
            if (equality.getOperationId() == StandardOperation.EQ) {
              clauseBuilder.add(
                  FortressUtils.makeNodeEqual(field, FortressUtils.makeNodeValue(value)));
            } else {
              clauseBuilder.add(
                  FortressUtils.makeNodeNotEqual(field, FortressUtils.makeNodeValue(value)));
            }
          }

          offset += FortressUtils.getBitSize(term);
          result.addOriginalVariable(result.getOriginal(FortressUtils.getVariable(term), pathIndex));
        }
    } else {
      final NodeOperation rhsExpr = (NodeOperation) rhs;
        InvariantChecks.checkTrue(rhsExpr.getOperationId() == StandardOperation.BVCONCAT);
        InvariantChecks.checkTrue(rhsExpr.getOperandCount() == rhsExpr.getOperandCount());

        for (int i = 0; i < lhsExpr.getOperandCount(); i++) {
          final Node lhsTerm = lhsExpr.getOperand(i);
          final Node rhsTerm = rhsExpr.getOperand(i);
          InvariantChecks.checkTrue(
              FortressUtils.getBitSize(lhsTerm) == FortressUtils.getBitSize(rhsTerm));

          final Node lhsField = result.getVersion(lhsTerm, pathIndex);
          final Node rhsField = result.getVersion(rhsTerm, pathIndex);

          if (equality.getOperationId() == StandardOperation.EQ) {
            clauseBuilder.add(
                FortressUtils.makeNodeEqual(lhsField, rhsField));
          } else {
            clauseBuilder.add(
                FortressUtils.makeNodeNotEqual(lhsField, rhsField));
          }

          result.addOriginalVariable(
              result.getOriginal(FortressUtils.getVariable(lhsTerm), pathIndex));
          result.addOriginalVariable(
              result.getOriginal(FortressUtils.getVariable(rhsTerm), pathIndex));
        }
    }

    return null;
  }

  private Boolean executeBindings(
      final SymbolicResult result,
      final Set<Variable> defines,
      final Collection<MmuBinding> bindings,
      final int pathIndex) {

    if (result.hasConflict()) {
      return Boolean.FALSE;
    }

    final List<Node> clauseBuilder = new ArrayList<>();

    for (final MmuBinding binding : bindings) {
      final Node lhs = binding.getLhs();
      final Node rhs = binding.getRhs();

      final Variable oldLhsVar = result.getVersion(FortressUtils.getVariable(lhs), pathIndex);
      final Variable lhsOriginal = result.getOriginal(FortressUtils.getVariable(lhs), pathIndex);

      result.addOriginalVariable(lhsOriginal);

      if (defines != null) {
        defines.add(lhsOriginal);
      }

      if (rhs == null) {
        continue;
      }

      final Variable newLhsVar = result.getNextVersion(FortressUtils.getVariable(lhs), pathIndex);
      final List<Node> rhsTerms = new ArrayList<>();

      // Equation for the prefix part.
      final int lhsLowerBit = FortressUtils.getLowerBit(lhs);
      final int lhsUpperBit = FortressUtils.getUpperBit(lhs);

      if (lhsLowerBit > 0) {
        final Node oldLhsPre = FortressUtils.makeNodeExtract(oldLhsVar, 0, lhsLowerBit - 1);
        final Node newLhsPre = FortressUtils.makeNodeExtract(newLhsVar, 0, lhsLowerBit - 1);

        clauseBuilder.add(FortressUtils.makeNodeEqual(newLhsPre, oldLhsPre));
        rhsTerms.add(oldLhsPre);
      }

      int offset = lhsLowerBit;

      // Equations for the middle part.
      final NodeOperation terms = (NodeOperation) rhs;
      InvariantChecks.checkNotNull(terms.getOperationId() == StandardOperation.BVCONCAT);

      for (final Node term : terms.getOperands()) {
        final Node field = result.getVersion(term, pathIndex);

        result.addOriginalVariable(result.getOriginal(FortressUtils.getVariable(term), pathIndex));

        final int upper = offset + (FortressUtils.getBitSize(field) - 1);
        final int trunc = upper > lhsUpperBit ? lhsUpperBit : upper;

        final Node lhsField = FortressUtils.makeNodeExtract(newLhsVar, offset, trunc);
        final Node rhsField = trunc == upper
            ? field
            : FortressUtils.makeNodeExtract(
                FortressUtils.getVariable(field),
                FortressUtils.getLowerBit(field),
                FortressUtils.getUpperBit(field) - (upper - trunc)
              );

        clauseBuilder.add(FortressUtils.makeNodeEqual(lhsField, rhsField));
        rhsTerms.add(rhsField);

        offset += FortressUtils.getBitSize(field);

        // Truncate the upper bits of the expression.
        if (offset > lhsUpperBit) {
          break;
        }
      } // For each right-hand-side term.

      if (offset <= lhsUpperBit) {
        clauseBuilder.add(
            FortressUtils.makeNodeEqual(
                FortressUtils.makeNodeExtract(newLhsVar, offset, lhsUpperBit),
                FortressUtils.makeNodeValue(0)));
      }

      // Equation for the suffix part.
      if (lhsUpperBit < oldLhsVar.getType().getSize() - 1) {
        final Node oldLhsPost =
            FortressUtils.makeNodeExtract(oldLhsVar, lhsUpperBit, oldLhsVar.getType().getSize() - 1);
        final Node newLhsPost =
            FortressUtils.makeNodeExtract(newLhsVar, lhsUpperBit, newLhsVar.getType().getSize() - 1);

        clauseBuilder.add(FortressUtils.makeNodeEqual(newLhsPost, oldLhsPost));
        rhsTerms.add(oldLhsPost);
      }

      // Update the version of the left-hand-side variable.
      result.defineVersion(FortressUtils.getVariable(lhs), pathIndex);

      // Try to propagate constants.
      final Map<Variable, BigInteger> constants = result.getConstants();
      final Node rhsExpr = FortressUtils.makeNodeConcat(rhsTerms);

      final BigInteger constant = FortressUtils.evaluate(rhsExpr,
          new ValueProvider() {
            @Override
            public Data getVariableValue(final Variable variable) {
              return Data.newBitVector(constants.get(variable), variable.getType().getSize());
            }
          });

      if (constant != null) {
        // Constant propagation.
        result.addConstant(newLhsVar, constant);
      }
    } // For each binding.

    if (!clauseBuilder.isEmpty()) {
      result.addFormula(FortressUtils.makeNodeAnd(clauseBuilder));
    }

    return Boolean.TRUE;
  }

  private static int uniqueId = 0;

  private static int getWidth(final int size) {
    int width = 0;
    int value = size;

    while ((value >>= 1) != 0) {
      width++;
    }

    if (((size - 1) & size) != 0) {
      width++;
    }

    return width;
  }

  private static Node getPhiField(final int width) {
    final Variable variable = new Variable(
        String.format("phi_%d", uniqueId++), DataType.BIT_VECTOR(width));
    return FortressUtils.makeNodeVariable(variable);
  }

  private static Node getIfThenField(final Variable phi, final int i) {
    final Variable variable = new Variable(
        String.format("%s_%d", phi.getName(), i), DataType.BIT_VECTOR(1));
    return FortressUtils.makeNodeVariable(variable);
  }

  private static Node getIfThenFormula(
      final Node phi, final int i, final Node formula) {
    final List<Node> ifThenBuilder = new ArrayList<>();

    final List<Node> clauseBuilder1 = new ArrayList<>();
    final List<Node> clauseBuilder2 = new ArrayList<>();

    // Introduce a Boolean variable: C == (PHI == i).
    final Node condition = getIfThenField(FortressUtils.getVariable(phi), i);

    clauseBuilder1.add(
        FortressUtils.makeNodeEqual(condition, FortressUtils.makeNodeValue(1)));
    clauseBuilder1.add(
        FortressUtils.makeNodeNotEqual(phi, FortressUtils.makeNodeValue(i)));

    clauseBuilder2.add(
        FortressUtils.makeNodeNotEqual(condition, FortressUtils.makeNodeValue(1)));
    clauseBuilder2.add(
        FortressUtils.makeNodeEqual(phi, FortressUtils.makeNodeValue(i)));

    ifThenBuilder.add(FortressUtils.makeNodeOr(clauseBuilder1));
    ifThenBuilder.add(FortressUtils.makeNodeOr(clauseBuilder2));

    // Transform the formula according to the rule:
    // C -> (x[1] & ... & x[n]) == (~C | x[1]) & ... & (~C | x[n]).
    final NodeOperation clauses = (NodeOperation) formula;
    InvariantChecks.checkTrue(clauses.getOperationId() == StandardOperation.AND);

    for (final Node operand : clauses.getOperands()) {
      final NodeOperation clause = (NodeOperation) operand;
      InvariantChecks.checkTrue(
          clause.getOperationId() == StandardOperation.AND
       || clause.getOperationId() == StandardOperation.OR);

      if (clause.getOperationId() == StandardOperation.OR) {
        final List<Node> clauseBuilder = new ArrayList<>();

        clauseBuilder.add(FortressUtils.makeNodeEqual(condition, FortressUtils.makeNodeValue(0)));
        clauseBuilder.addAll(clause.getOperands());

        ifThenBuilder.add(FortressUtils.makeNodeOr(clauseBuilder));
      } else {
        for (final Node equation : clause.getOperands()) {
          final List<Node> clauseBuilder = new ArrayList<>();

          clauseBuilder.add(FortressUtils.makeNodeEqual(condition, FortressUtils.makeNodeValue(0)));
          clauseBuilder.add(equation);

          ifThenBuilder.add(FortressUtils.makeNodeOr(clauseBuilder));
        }
      }
    }

    return FortressUtils.makeNodeAnd(ifThenBuilder);
  }
}

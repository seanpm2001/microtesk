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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.mmu.basis.BufferAccessEvent;
import ru.ispras.microtesk.mmu.basis.DataType;
import ru.ispras.microtesk.mmu.basis.MemoryOperation;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAction;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuAddressType;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuBuffer;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuGuard;
import ru.ispras.microtesk.mmu.translator.ir.spec.MmuTransition;

/**
 * This class describes an execution path, which is a sequence of transitions.
 * 
 * @author <a href="mailto:protsenko@ispras.ru">Alexander Protsenko</a>
 */
public final class MemoryAccess {

  /** The operation being executed. */
  private final MemoryOperation operation;

  /**
   * The size of loaded/stored data ({@code BYTE}, {@code WORD}, etc.) or {@code null} if size is
   * not of importance.
   */
  private DataType dataType;

  /** The virtual address. */
  private final MmuAddressType startAddress;

  /** The list of transitions. */
  private final List<MmuTransition> transitions = new ArrayList<>();

  /**
   * Constructs an execution.
   * 
   * @param operation the operation being executed.
   * @param type the operation type.
   */
  public MemoryAccess(final MemoryOperation operation, final DataType type,
      final MmuAddressType startAddress) {
    InvariantChecks.checkNotNull(operation);
    InvariantChecks.checkNotNull(startAddress);

    this.operation = operation;
    this.dataType = type;
    this.startAddress = startAddress;
  }

  /**
   * Constructs an execution.
   * 
   * @param operation the operation being executed.
   */
  public MemoryAccess(final MemoryOperation operation, final MmuAddressType startAddress) {
    this(operation, null, startAddress);
  }

  /**
   * Returns the execution operation.
   * 
   * @return the operation.
   */
  public MemoryOperation getOperation() {
    return operation;
  }

  /**
   * Returns the execution data type.
   * 
   * @return the operation.
   */
  public DataType getDataType() {
    return dataType;
  }

  /**
   * Sets the execution data type.
   * 
   * @param type the data type to be set.
   */
  public void setDataType(final DataType type) {
    this.dataType = type;
  }

  /**
   * TODO:
   */
  public MmuAddressType getStartAddress() {
    return startAddress;
  }

  /**
   * Returns the list of the devices used in the execution path (with no duplicates) in order of
   * their occurrence in the path.
   * 
   * @return the list of devices.
   */
  // TODO: Optimization is needed!
  public List<MmuBuffer> getDevices() {
    final List<MmuBuffer> result = new ArrayList<>();
    final List<MmuTransition> transitions = getTransitions();

    final Set<MmuBuffer> handledDevices = new HashSet<>();

    for (final MmuTransition transition : transitions) {
      final MmuGuard guard = transition.getGuard();
      final MmuBuffer guardDevice = guard != null ? guard.getDevice() : null;

      if (guardDevice != null && !handledDevices.contains(guardDevice)) {
        handledDevices.add(guardDevice);
        result.add(guardDevice);
      }

      final MmuAction source = transition.getSource();
      final MmuBuffer sourceDevice = source.getDevice();

      if (sourceDevice != null && !handledDevices.contains(sourceDevice)) {
        handledDevices.add(sourceDevice);
        result.add(sourceDevice);
      }

      final MmuAction target = transition.getTarget();
      final MmuBuffer targetDevice = target.getDevice();

      if (targetDevice != null && !handledDevices.contains(targetDevice)) {
        handledDevices.add(targetDevice);
        result.add(targetDevice);
      }
    }

    return result;
  }

  /**
   * Returns the list of address types used in this execution.
   * 
   * @return the address list.
   */
  public List<MmuAddressType> getAddresses() {
    final List<MmuAddressType> result = new ArrayList<>();
    final Set<MmuAddressType> addresses = new HashSet<>();
    final List<MmuTransition> transitions = getTransitions();

    for (final MmuTransition transition : transitions) {
      final MmuAction action = transition.getSource();
      final MmuBuffer device = action.getDevice();
      
      if (device != null) {
        final MmuAddressType address = device.getAddress();

        if (!addresses.contains(address)) {
          addresses.add(address);
          result.add(address);
        }
      }
    }

    return result;
  }

  /**
   * Returns the execution actions.
   * 
   * @return the list of actions.
   */
  public List<MmuAction> getActions() {
    final List<MmuAction> actions = new ArrayList<>();
    for (final MmuTransition transition : transitions) {
      actions.add(transition.getSource());
    }
    if (!transitions.isEmpty()) {
      final MmuTransition transition = transitions.get(transitions.size() - 1);
      actions.add(transition.getTarget());
    }
    return actions;
  }

  /**
   * Returns the transitions of the execution path.
   * 
   * @return the transitions.
   */
  public List<MmuTransition> getTransitions() {
    return transitions;
  }

  /**
   * Adds the transition to the execution path.
   * 
   * @param transition the transition to be added.
   */
  public void addTransition(final MmuTransition transition) {
    InvariantChecks.checkNotNull(transition);

    transitions.add(transition);
  }

  /**
   * Adds the transitions to the execution path.
   * 
   * @param addTransitions the transitionList to be added.
   * @throws IllegalArgumentException if {@code transitions} is null.
   */
  public void addTransitions(final List<MmuTransition> addTransitions) {
    InvariantChecks.checkNotNull(addTransitions);

    this.transitions.addAll(addTransitions);
  }

  /**
   * Returns the event of the device.
   * 
   * @param device the device
   * @return the event.
   * @throws IllegalArgumentException if {@code device} is null.
   */
  public BufferAccessEvent getEvent(final MmuBuffer device) {
    InvariantChecks.checkNotNull(device);

    for (final MmuTransition transition : transitions) {
      final MmuGuard guard = transition.getGuard();

      if ((guard != null) && (guard.getDevice() != null) && (guard.getDevice().equals(device))) {
        return guard.getEvent();
      }
    }

    return null;
  }

  /**
   * Checks whether the execution contains a transition.
   * 
   * @param transition the transition
   * @return {@code true} if the execution contains the transition; {@code false} otherwie.
   * @throws IllegalArgumentException if {@code transition} is null.
   */
  public boolean contains(final MmuTransition transition) {
    InvariantChecks.checkNotNull(transition);

    return transitions.contains(transition);
  }

  /**
   * Tells whether or not this execution contains the given action.
   * 
   * @param action the action
   * @return {@code true} if the execution contains the action; {@code false} otherwise.
   * @throws IllegalArgumentException if {@code action} is null.
   */
  public boolean contains(final MmuAction action) {
    InvariantChecks.checkNotNull(action);

    final List<MmuAction> actions = getActions();
    return actions.contains(action);
  }

  /**
   * Tells whether or not this execution contains the given address.
   * 
   * @param address the address to be checked.
   * @return {@code true} if the execution contains the address; {@code false} otherwise.
   * @throws IllegalArgumentException if {@code address} is null.
   */
  public boolean contains(final MmuAddressType address) {
    InvariantChecks.checkNotNull(address);

    final Collection<MmuAddressType> addresses = getAddresses();
    return addresses.contains(address);
  }

  /**
   * Tells whether or not this execution contains the given device.
   * 
   * @param device the device to be checked.
   * @return {@code true} if the execution contains the device; {@code false} otherwise.
   * @throws IllegalArgumentException if {@code device} is null.
   */
  public boolean contains(final MmuBuffer device) {
    InvariantChecks.checkNotNull(device);

    final Collection<MmuBuffer> devices = getDevices();
    return devices.contains(device);
  }

  @Override
  public String toString() {
    final String separator = ", ";
    final StringBuilder builder = new StringBuilder();

    final MemoryOperation operation = getOperation();
    final DataType type = getDataType();

    builder.append(operation);
    builder.append(separator);
    builder.append(type);

    for (final MmuTransition transition : transitions) {
      final MmuGuard guard = transition.getGuard();

      if (guard != null && guard.getOperation() == null) {
        builder.append(separator);
        builder.append(guard);
      }
    }

    final MmuTransition finalTransition = transitions.get(transitions.size() - 1);
    final MmuAction finalAction = finalTransition.getTarget();

    builder.append(separator);
    builder.append(finalAction.getName());

    return builder.toString();
  }
}

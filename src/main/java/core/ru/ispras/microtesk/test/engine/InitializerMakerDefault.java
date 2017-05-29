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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.ispras.fortress.data.types.bitvector.BitVector;
import ru.ispras.fortress.expression.Node;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.model.ArgumentMode;
import ru.ispras.microtesk.model.ConfigurationException;
import ru.ispras.microtesk.test.engine.utils.AddressingModeWrapper;
import ru.ispras.microtesk.test.engine.utils.EngineUtils;
import ru.ispras.microtesk.test.template.AbstractCall;
import ru.ispras.microtesk.test.template.Argument;
import ru.ispras.microtesk.test.template.Primitive;
import ru.ispras.microtesk.utils.FortressUtils;
import ru.ispras.testbase.TestData;

/**
 * @author <a href="mailto:andrewt@ispras.ru">Andrei Tatarnikov</a>
 */
public class InitializerMakerDefault implements InitializerMaker {

  @Override
  public List<AbstractCall> makeInitializer(
      final EngineContext engineContext,
      final TestData testData,
      final Map<String, Argument> arguments,
      final Set<AddressingModeWrapper> initializedModes) throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testData);
    InvariantChecks.checkNotNull(initializedModes);

    final List<AbstractCall> result = new ArrayList<>();
    for (final Map.Entry<String, Node> e : testData.getBindings().entrySet()) {
      final String name = e.getKey();
      final Argument argument = arguments.get(name);

      if (null == argument || ArgumentMode.OUT == argument.getMode()) {
        continue;
      }

      final Primitive mode = (Primitive) argument.getValue();
      final AddressingModeWrapper targetMode = new AddressingModeWrapper(mode);

      if (initializedModes.contains(targetMode)) {
        Logger.debug("%s has already been used to set up the processor state. " +
                     "No initialization code will be created.", targetMode);
        continue;
      }

      final BitVector value = FortressUtils.extractBitVector(e.getValue());
      Logger.debug("Creating code to assign %s to %s...", value, targetMode);

      final List<AbstractCall> initializingCalls =
          EngineUtils.makeInitializer(engineContext, mode, value);

      result.addAll(initializingCalls);
      initializedModes.add(targetMode);
    }

    return result;
  }
}

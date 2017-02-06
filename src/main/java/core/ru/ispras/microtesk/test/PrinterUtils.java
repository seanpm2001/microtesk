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

package ru.ispras.microtesk.test;

import java.io.IOException;
import java.util.List;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.Logger;
import ru.ispras.microtesk.model.api.ConfigurationException;
import ru.ispras.microtesk.options.Option;
import ru.ispras.microtesk.test.sequence.engine.EngineContext;
import ru.ispras.microtesk.test.template.DataSection;

/**
 * The {@link PrinterUtils} class provides utility methods for printing test programs and their
 * parts to files and to console. 
 * 
 * @author <a href="mailto:andrewt@ispras.ru">Andrei Tatarnikov</a>
 */
final class PrinterUtils {
  public static void printSequenceToConsole(
      final EngineContext engineContext,
      final TestSequence sequence) throws ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(sequence);

    if (engineContext.getOptions().getValueAsBoolean(Option.VERBOSE)) {
      Logger.debugHeader("Constructed %s", sequence.getTitle());

      final Printer consolePrinter =
          Printer.getConsole(engineContext.getOptions(), engineContext.getStatistics());

      consolePrinter.printSequence(engineContext.getModel().getPE(), sequence);
    }
  }

  public static void printDataSection(
      final EngineContext engineContext,
      final DataSection data) throws IOException, ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(data);

    final Statistics statistics = engineContext.getStatistics();
    statistics.pushActivity(Statistics.Activity.PRINTING);

    Printer printer = null;
    try {
      printer = Printer.newDataFile(engineContext.getOptions(), statistics.getDataFiles());
      printer.printDataDirectives(data.getDirectives());
      statistics.incDataFiles();
    } finally {
      if (null != printer) {
        printer.close();
      }
      Logger.debugBar();
      statistics.popActivity();
    }
  }

  public static void printExceptionHandler(
      final EngineContext engineContext,
      final String id,
      final List<TestSequence> sequences) throws IOException, ConfigurationException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(id);
    InvariantChecks.checkNotNull(sequences);

    final Statistics statistics = engineContext.getStatistics();
    statistics.pushActivity(Statistics.Activity.PRINTING);

    Printer printer = null;
    try { 
      printer = Printer.newExcHandlerFile(engineContext.getOptions(), id);
      for (final TestSequence sequence : sequences) {
        statistics.incInstructions(sequence.getInstructionCount());
        printer.printSequence(engineContext.getModel().getPE(), sequence);
      }
    } finally {
      if (null != printer) {
        printer.close();
      }
      Logger.debugBar();
      statistics.popActivity();
    }
  }

  public static void printTestProgram(
      final EngineContext engineContext,
      final TestProgram testProgram) throws ConfigurationException, IOException {
    InvariantChecks.checkNotNull(engineContext);
    InvariantChecks.checkNotNull(testProgram);

    final Statistics statistics = engineContext.getStatistics();
    statistics.pushActivity(Statistics.Activity.PRINTING);

    final int programIndex = statistics.getPrograms();
    final Printer printer = Printer.newCodeFile(engineContext.getOptions(), programIndex);

    try {
      statistics.incPrograms();

      for (int index = 0; index < testProgram.getEntryCount(); ++index) {
        final TestSequence sequence = testProgram.getEntry(index);
        Logger.debugHeader("Printing %s to %s", sequence.getTitle(), printer.getFileName());
        printer.printSequence(engineContext.getModel().getPE(), sequence);
      }

      final List<DataSection> globalData = testProgram.getGlobalData();
      final List<DataSection> localData = testProgram.getLocalData();
      printer.printData(globalData, localData);
    } finally {
      printer.close();
      statistics.popActivity();
    }
  }
}
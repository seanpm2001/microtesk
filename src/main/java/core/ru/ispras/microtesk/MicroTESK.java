/*
 * Copyright 2012-2016 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.runtime.RecognitionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;

import ru.ispras.fortress.solver.Environment;
import ru.ispras.microtesk.settings.GeneratorSettings;
import ru.ispras.microtesk.settings.SettingsParser;
import ru.ispras.microtesk.test.Statistics;
import ru.ispras.microtesk.test.TestEngine;
import ru.ispras.microtesk.test.TestSettings;
import ru.ispras.microtesk.test.sequence.GeneratorConfig;
import ru.ispras.microtesk.test.sequence.engine.Adapter;
import ru.ispras.microtesk.test.sequence.engine.Engine;
import ru.ispras.microtesk.translator.Translator;
import ru.ispras.microtesk.translator.TranslatorContext;
import ru.ispras.microtesk.translator.generation.PackageInfo;
import ru.ispras.microtesk.utils.FileUtils;
import ru.ispras.testbase.TestBaseRegistry;
import ru.ispras.testbase.generator.DataGenerator;
import ru.ispras.testbase.stub.TestBase;

public final class MicroTESK {
  private MicroTESK() {}

  public static void main(final String[] args) {
    final Parameters params;

    try {
      params = new Parameters(args);
    } catch (final ParseException e) {
      Logger.error("Incorrect command line: " + e.getMessage());
      Parameters.help();
      return;
    }

    if (params.hasOption(Parameters.HELP)) {
      Parameters.help();
      return;
    }

    if (params.hasOption(Parameters.VERBOSE)) {
      Logger.setDebug(true);
    }

    try {
      final List<Plugin> plugins = Config.loadPlugins();
      registerPlugins(plugins);

      if (params.hasOption(Parameters.GENERATE)) {
        generate(params, plugins);
      } else {
        translate(params);
      }
    } catch (final ParseException e) {
      Logger.error("Incorrect command line or configuration file: " + e.getMessage());
      Parameters.help();
      System.exit(-1);
    } catch (final Throwable e) {
      Logger.exception(e);
      System.exit(-1);
    }
  }

  private static final List<Translator<?>> translators = new ArrayList<>();

  private static void registerPlugins(final List<Plugin> plugins) {
    for (final Plugin plugin : plugins) {
      // Register the translator.
      final Translator<?> translator = plugin.getTranslator();

      if (translator != null) {
        translators.add(translator);
      }

      // Register the engines.
      final GeneratorConfig<?> generatorConfig = GeneratorConfig.get();
      final Map<String, Engine<?>> engines = plugin.getEngines();

      for (final Map.Entry<String, Engine<?>> entry : engines.entrySet()) {
        generatorConfig.registerEngine(entry.getKey(), entry.getValue());
      }

      // Register the adapters.
      final Map<String, Adapter<?>> adapters = plugin.getAdapters();

      for (final Map.Entry<String, Adapter<?>> entry : adapters.entrySet()) {
        generatorConfig.registerAdapter(entry.getKey(), entry.getValue());
      }

      // Register the data generators.
      final TestBaseRegistry testBaseRegistry = TestBase.get().getRegistry();
      final Map<String, DataGenerator> dataGenerators = plugin.getDataGenerators();

      if (dataGenerators != null) {
        for (final Map.Entry<String, DataGenerator> entry : dataGenerators.entrySet()) {
          testBaseRegistry.registerGenerator(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private static void translate(final Parameters params) throws RecognitionException {
    final TranslatorContext context = new TranslatorContext();
    for (final Translator<?> translator : translators) {
      if (params.hasOption(Parameters.INCLUDE)) {
        translator.addPath(params.getOptionValue(Parameters.INCLUDE));
      }

      if (params.hasOption(Parameters.OUTDIR)) {
        translator.setOutDir(params.getOptionValue(Parameters.OUTDIR));
      }

      translator.setContext(context);
      for (final String fileName : params.getArgs()) {
        final String fileDir = FileUtils.getFileDir(fileName);
        if (null != fileDir) {
          translator.addPath(fileDir);
        }
      }

      if (!translator.start(params.getArgs())) {
        Logger.error("TRANSLATION WAS ABORTED");
        return;
      }
    }

    // Copy user-defined Java code is copied to the output folder.
    if (params.hasOption(Parameters.EXTDIR)) {
      final String extensionDir = params.getOptionValue(Parameters.EXTDIR);
      final File extensionDirFile = new File(extensionDir);

      if (!extensionDirFile.exists() || !extensionDirFile.isDirectory()) {
        Logger.error("The extension folder %s does not exists or is not a folder.", extensionDir);
        return;
      }

      final String outDir = (params.hasOption(Parameters.OUTDIR) ?
          params.getOptionValue(Parameters.OUTDIR) : PackageInfo.DEFAULT_OUTDIR) + "/src/java";

      final File outDirFile = new File(outDir);

      try {
        FileUtils.copyDirectory(extensionDirFile, outDirFile);
        Logger.message("Copied %s to %s", extensionDir, outDir);
      } catch (final IOException e) {
        Logger.error("Failed to copy %s to %s", extensionDir, outDir);
      }
    }
  }

  private static void generate(
      final Parameters params, final List<Plugin> plugins) throws ParseException, Throwable {
    final String[] args = params.getArgs();
    if (args.length != 2) {
      Logger.error("Wrong number of generator arguments. Two arguments are required.");
      Logger.message("Argument format: <model name>, <template file>");
      return;
    }

    final String modelName = args[0];
    final String templateFile = args[1];

    if (params.hasOption(Parameters.OUTDIR)) {
      TestSettings.setOutDir(params.getOptionValue(Parameters.OUTDIR));
    }

    if (params.hasOption(Parameters.RANDOM)) {
      final int randomSeed = params.getOptionValueAsInt(Parameters.RANDOM);
      TestEngine.setRandomSeed(randomSeed);
    } else {
      reportUndefinedOption(Parameters.RANDOM);
    }
 
    if (params.hasOption(Parameters.LIMIT)) {
      final int branchExecutionLimit = params.getOptionValueAsInt(Parameters.LIMIT);
      TestSettings.setBranchExecutionLimit(branchExecutionLimit);
    } else {
      reportUndefinedOption(Parameters.LIMIT);
    }

    if (params.hasOption(Parameters.SOLVER)) {
      TestEngine.setSolver(params.getOptionValue(Parameters.SOLVER));
    }

    if (params.hasOption(Parameters.CODE_EXT)) {
      TestSettings.setCodeFileExtension(params.getOptionValue(Parameters.CODE_EXT));
    } else {
      reportUndefinedOption(Parameters.CODE_EXT);
    }
 
    if (params.hasOption(Parameters.CODE_PRE)) {
      TestSettings.setCodeFilePrefix(params.getOptionValue(Parameters.CODE_PRE));
    } else {
      reportUndefinedOption(Parameters.CODE_PRE);
    }

    if (params.hasOption(Parameters.DATA_EXT)) {
      TestSettings.setDataFileExtension(params.getOptionValue(Parameters.DATA_EXT));
    } else {
      reportUndefinedOption(Parameters.DATA_EXT);
    }
 
    if (params.hasOption(Parameters.DATA_PRE)) {
      TestSettings.setDataFilePrefix(params.getOptionValue(Parameters.DATA_PRE));
    } else {
      reportUndefinedOption(Parameters.DATA_PRE);
    }

    if (params.hasOption(Parameters.EXCEPT_PRE)) {
      TestSettings.setExceptionFilePrefix(params.getOptionValue(Parameters.EXCEPT_PRE));
    } else {
      reportUndefinedOption(Parameters.EXCEPT_PRE);
    }

    if (params.hasOption(Parameters.CODE_LIMIT)) {
      final int programLengthLimit = params.getOptionValueAsInt(Parameters.CODE_LIMIT);
      TestSettings.setProgramLengthLimit(programLengthLimit);
    } else {
      reportUndefinedOption(Parameters.CODE_LIMIT);
    }

    if (params.hasOption(Parameters.TRACE_LIMIT)) {
      final int traceLengthLimit = params.getOptionValueAsInt(Parameters.TRACE_LIMIT);
      TestSettings.setTraceLengthLimit(traceLengthLimit);
    } else {
      reportUndefinedOption(Parameters.TRACE_LIMIT);
    }

    TestSettings.setCommentsEnabled(params.hasOption(Parameters.COMMENTS_ENABLED));
    TestSettings.setCommentsDebug(params.hasOption(Parameters.COMMENTS_DEBUG));
    TestSettings.setSimulationDisabled(params.hasOption(Parameters.NO_SIMULATION));

    if (params.hasOption(Parameters.SOLVER_DEBUG)) {
      Environment.setDebugMode(true);
    }

    if (params.hasOption(Parameters.TARMAC_LOG)) {
      TestSettings.setTarmacLog(true);
    }

    if (params.hasOption(Parameters.SELF_CHECKS)) {
      TestSettings.setSelfChecks(true);
    }

    if (params.hasOption(Parameters.DEFAULT_TEST_DATA)) {
      TestSettings.setDefaultTestData(true);
    }

    if (params.hasOption(Parameters.ARCH_DIRS)) {
      final String archDirs = params.getOptionValue(Parameters.ARCH_DIRS);
      final String[] archDirsArray = archDirs.split(":");

      for (final String archDir : archDirsArray) {
        final String[] archDirArray = archDir.split("=");

        if (archDirArray != null && archDirArray.length > 1 && modelName.equals(archDirArray[0])) {
          final File archFile = new File(archDirArray[1]);

          final String archPath = archFile.isAbsolute() ? archDirArray[1] : String.format("%s%s%s",
              SysUtils.getHomeDir(), File.separator, archDirArray[1]); 

          final GeneratorSettings settings = SettingsParser.parse(archPath);
          TestEngine.setGeneratorSettings(settings);
        }
      }
    }

    final Statistics statistics = TestEngine.generate(modelName, templateFile, plugins);
    if (null == statistics) {
      return;
    }

    final long time = statistics.getTotalTime();
    final long rate = statistics.getRate();

    Logger.message("Generation Statistics");
    Logger.message("Generation time: %s", Statistics.timeToString(time));
    Logger.message("Generation rate: %d instructions/second", rate);

    Logger.message("Programs/stimuli/instructions: %d/%d/%d",
        statistics.getPrograms(), statistics.getSequences(), statistics.getInstructions());

    if (params.hasOption(Parameters.TIME_STATISTICS)) {
      Logger.message(System.lineSeparator() + "Time Statistics");
      for (final Statistics.Activity activity : Statistics.Activity.values()) {
        Logger.message(statistics.getTimeMetricText(activity));
      }
    }

    if (params.hasOption(Parameters.RATE_LIMIT)) {
      final long rateLimit = params.getOptionValueAsInt(Parameters.RATE_LIMIT);
      if (rate < rateLimit && statistics.getInstructions() >= 1000) { 
        // Makes sense only for sequences of significant length (>= 1000)
        Logger.error("Generation rate is too slow. At least %d is expected.", rateLimit);
        System.exit(-1);
      }
    }
  }

  private static void reportUndefinedOption(final Option option) {
    Logger.warning("The --%s option is undefined.", option.getLongOpt());
  }
}

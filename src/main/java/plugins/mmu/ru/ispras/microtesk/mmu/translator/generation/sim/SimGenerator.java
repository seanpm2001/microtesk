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

package ru.ispras.microtesk.mmu.translator.generation.sim;

import java.io.IOException;

import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.mmu.translator.ir.Address;
import ru.ispras.microtesk.mmu.translator.ir.Buffer;
import ru.ispras.microtesk.mmu.translator.ir.Ir;
import ru.ispras.microtesk.mmu.translator.ir.Memory;
import ru.ispras.microtesk.mmu.translator.ir.Segment;
import ru.ispras.microtesk.mmu.translator.ir.Type;
import ru.ispras.microtesk.translator.TranslatorHandler;
import ru.ispras.microtesk.translator.generation.FileGenerator;
import ru.ispras.microtesk.translator.generation.PackageInfo;
import ru.ispras.microtesk.translator.generation.STBuilder;
import ru.ispras.microtesk.translator.generation.STFileGenerator;

public final class SimGenerator implements TranslatorHandler<Ir> {
  private static final String MMU_STG_DIR = "stg/mmu/";

  private static final String JAVA_COMMON_STG =
      PackageInfo.COMMON_TEMPLATE_DIR + "JavaCommon.stg";

  private static final String MMU_COMMON_STG = 
      MMU_STG_DIR + "Common.stg";

  private static final String[] COMMON_STGS =
      new String[] {JAVA_COMMON_STG, MMU_COMMON_STG};

  private static final String[] BUFFER_STGS = 
      new String[] {JAVA_COMMON_STG, MMU_COMMON_STG, MMU_STG_DIR + "Buffer.stg"};

  private final String outDir;
  private final String packageName;

  public SimGenerator(final String outDir, final String modelName) {
    InvariantChecks.checkNotNull(outDir);
    InvariantChecks.checkNotNull(modelName);

    this.outDir = String.format("%s/%s/mmu/sim", PackageInfo.getModelOutDir(outDir), modelName);
    this.packageName = String.format("%s.%s.sim.mmu", PackageInfo.MODEL_PACKAGE, modelName);
  }

  @Override
  public void processIr(final Ir ir) {
    InvariantChecks.checkNotNull(ir);

    try {
      processStructs(ir);
      processAddresses(ir);
      processBuffers(ir);
      processSegments(ir);
      processMemories(ir);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processStructs(final Ir ir) throws IOException {
    for (final Type type : ir.getTypes().values()) {
      if (!ir.getAddresses().containsKey(type.getId())) {
        final FileGenerator fileGenerator = newStructGenerator(type);
        fileGenerator.generate();
      }
    }
  }

  private void processAddresses(final Ir ir) throws IOException {
    for (final Address address : ir.getAddresses().values()) {
      final FileGenerator fileGenerator = newAddressGenerator(address);
      fileGenerator.generate();
    }
  }

  private void processBuffers(final Ir ir) throws IOException {
    for (final Buffer buffer : ir.getBuffers().values()) {
      final FileGenerator fileGenerator = newBufferGenerator(buffer);
      fileGenerator.generate();
    }
  }

  private void processSegments(final Ir ir) throws IOException {
    for (final Segment segment : ir.getSegments().values()) {
      final FileGenerator fileGenerator = newSegmentGenerator(segment);
      fileGenerator.generate();
    }
  }

  private void processMemories(final Ir ir) throws IOException {
    for (final Memory memory : ir.getMemories().values()) {
      final FileGenerator fileGenerator = newMemoryGenerator(memory);
      fileGenerator.generate();
    }
  }

  private String getOutputFileName(final String name) {
    return String.format("%s/%s%s", outDir, name, PackageInfo.JAVA_EXT);
  }

  private FileGenerator newAddressGenerator(final Address address) {
    InvariantChecks.checkNotNull(address);

    final String outputFileName = getOutputFileName(address.getId());
    final STBuilder builder = new STBStruct(packageName, address);

    return new STFileGenerator(outputFileName, COMMON_STGS, builder);
  }

  private FileGenerator newStructGenerator(final Type structType) {
    InvariantChecks.checkNotNull(structType);

    final String outputFileName = getOutputFileName(structType.getId());
    final STBuilder builder = new STBStruct(packageName, structType);

    return new STFileGenerator(outputFileName, COMMON_STGS, builder);
  }

  private FileGenerator newBufferGenerator(final Buffer buffer) {
    InvariantChecks.checkNotNull(buffer);

    final String outputFileName = getOutputFileName(buffer.getId());
    final STBuilder builder = new STBBuffer(packageName, buffer);

    return new STFileGenerator(outputFileName, BUFFER_STGS, builder);
  }

  private FileGenerator newSegmentGenerator(final Segment segment) {
    InvariantChecks.checkNotNull(segment);

    final String outputFileName = getOutputFileName(segment.getId());
    final STBuilder builder = new STBSegment(packageName, segment);

    return new STFileGenerator(outputFileName, COMMON_STGS, builder);
  }

  private FileGenerator newMemoryGenerator(final Memory memory) {
    InvariantChecks.checkNotNull(memory);

    final String outputFileName = getOutputFileName(memory.getId());
    final STBuilder builder = new STBMemory(packageName, memory);

    return new STFileGenerator(outputFileName, COMMON_STGS, builder);
  }
}

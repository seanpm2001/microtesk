/*
 * Copyright 2014-2016 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.test.template;

import static ru.ispras.fortress.util.InvariantChecks.checkNotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class AbstractCallBuilder {
  private final BlockId blockId;

  private Where where;
  private String text;
  private Primitive rootOperation;

  private final List<Label> labels;
  private final List<LabelReference> labelRefs;
  private final List<Output> outputs;

  private boolean relativeOrigin;
  private BigInteger origin;
  private BigInteger alignment;
  private BigInteger alignmentInBytes;

  private PreparatorReference preparatorReference;

  protected AbstractCallBuilder(final BlockId blockId) {
    checkNotNull(blockId);

    this.blockId = blockId;
    this.where = null;
    this.text = null;
    this.rootOperation = null;
    this.labels = new ArrayList<>();
    this.labelRefs = new ArrayList<>();
    this.outputs = new ArrayList<>();
    this.origin = null;
    this.alignment = null;
    this.alignmentInBytes = null;
    this.preparatorReference = null;
  }

  public BlockId getBlockId() {
    return blockId;
  }

  public void setWhere(final Where where) {
    checkNotNull(where);
    this.where = where;
  }

  public void setText(final String text) {
    checkNotNull(text);
    this.text = text;
  }

  public void setRootOperation(final Primitive rootOperation) {
    checkNotNull(rootOperation);

    if (rootOperation.getKind() != Primitive.Kind.OP) {
      throw new IllegalArgumentException("Illegal kind: " + rootOperation.getKind());
    }

    this.rootOperation = rootOperation;
  }

  public void addLabel(final Label label) {
    checkNotNull(label);
    labels.add(label);
  }

  public void addLabelReference(final LabelValue label) {
    checkNotNull(label);

    final LabelReference labelRef = new LabelReference(label);
    labelRefs.add(labelRef);
  }

  public void addOutput(final Output output) {
    checkNotNull(output);
    outputs.add(output);
  }

  public void setOrigin(final BigInteger value, final boolean isRelative) {
    checkNotNull(value);
    origin = value;
    relativeOrigin = isRelative;
  }

  public void setAlignment(final BigInteger value, final BigInteger valueInBytes) {
    checkNotNull(value);
    checkNotNull(valueInBytes);

    alignment = value;
    alignmentInBytes = valueInBytes;
  }

  public void setPreparatorReference(final PreparatorReference preparatorReference) {
    checkNotNull(preparatorReference);
    this.preparatorReference = preparatorReference;
  }

  public AbstractCall build() {
    return new AbstractCall(
        where,
        text,
        rootOperation,
        labels,
        labelRefs,
        outputs,
        relativeOrigin,
        origin,
        alignment,
        alignmentInBytes,
        preparatorReference,
        null,
        null,
        null,
        null,
        false
        );
  }
}
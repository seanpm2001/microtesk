/*
 * Copyright 2014-2017 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.translator.nml.coverage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ru.ispras.fortress.expression.NodeOperation;
import ru.ispras.fortress.expression.NodeVariable;

final class BlockBuilder {
  private List<NodeOperation> statements;
  private Map<String, NodeVariable> inputs;
  private Map<String, NodeVariable> outputs;
  private List<NodeVariable> intermediates;

  BlockBuilder() {
    this.statements = new ArrayList<>();
    this.inputs = new TreeMap<>();
    this.outputs = new TreeMap<>();
    this.intermediates = new ArrayList<>();
  }

  void add(final NodeOperation s) {
    statements.add(s);
  }

  void addAll(final Collection<NodeOperation> nodes) {
    statements.addAll(nodes);
  }

  public List<NodeOperation> getStatements() {
    return statements;
  }

  public Block build() {
    collectData(statements);
    return new Block(statements, inputs, outputs, intermediates);
  }

  private void collectData(final List<NodeOperation> statements) {
    /* TODO populate input/output maps and intermediates list */
  }
}

/*
 * Copyright 2014 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.translator.simnml.coverage.ssa;

import ru.ispras.fortress.expression.Node;
import ru.ispras.fortress.expression.NodeOperation;
import ru.ispras.fortress.expression.NodeVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class Utility {
  static <T> List<T> appendList(List<T> lhs, List<T> rhs) {
    if (rhs.isEmpty())
      return lhs;

    if (lhs.isEmpty())
      return new ArrayList<T>(rhs);

    lhs.addAll(rhs);

    return lhs;
  }

  static <T> List<T> appendElement(List<T> lhs, T elem) {
    if (lhs.isEmpty())
      lhs = new ArrayList<T>();

    lhs.add(elem);

    return lhs;
  }

  public static Pair<String, String> splitOnLast(String name, char c) {
    final int index = name.lastIndexOf(c);
    if (index < 0) {
      return new Pair<>(name, "");
    }
    return splitOnIndex(name, index);
  }

  public static Pair<String, String> splitOnFirst(String name, char c) {
    final int index = name.indexOf(c);
    if (index < 0) {
      return new Pair<>("", name);
    }
    return splitOnIndex(name, index);
  }

  private static Pair<String, String> splitOnIndex(String name, int index) {
    return new Pair<>(name.substring(0, index), name.substring(index + 1));
  }

  public static String prettyString(Node node) {
    final StringBuilder builder = new StringBuilder();
    prettyString(node, 0, builder);

    return builder.toString();
  }

  private static void prettyString(Node node, int indent, StringBuilder builder) {
    if (node.getKind() != Node.Kind.OPERATION) {
      builder.append(node.toString());
      return;
    }
    final NodeOperation op = (NodeOperation) node;
    for (Node operand : op.getOperands()) {
      if (operand.getKind() == Node.Kind.OPERATION) {
        multiLineOperation(op, indent, builder);
        return;
      }
    }
    singleLineOperation(op, builder);
  }

  private static void singleLineOperation(NodeOperation op, StringBuilder builder) {
    builder.append('(').append(op.getOperationId().toString());
    for (Node operand : op.getOperands()) {
      builder.append(' ').append(operand.toString());
    }
    builder.append(')');
  }

  private static void multiLineOperation(NodeOperation op, int indent, StringBuilder builder) {
    final int nspaces = indent + op.getOperationId().toString().length() + 2;

    builder.append('(').append(op.getOperationId().toString());
    if (op.getOperandCount() > 0) {
      builder.append(' ');
      prettyString(op.getOperand(0), nspaces, builder);
    }

    for (int i = 1; i < op.getOperandCount(); ++i) {
      builder.append('\n');
      ntimesChar(' ', nspaces, builder);
      prettyString(op.getOperand(i), nspaces, builder);
    }
    builder.append(')');
  }

  private static void ntimesChar(char c, int n, StringBuilder builder) {
    for (int i = 0; i < n; ++i) {
      builder.append(c);
    }
  }
}

/*
 * Copyright 2013-2016 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.translator.nml.ir.shared;

import static ru.ispras.fortress.util.InvariantChecks.checkNotNull;
import ru.ispras.fortress.data.DataType;
import ru.ispras.fortress.expression.NodeVariable;
import ru.ispras.microtesk.translator.nml.ir.expr.Expr;

public final class LetConstant {
  public static final LetConstant FLOAT_ROUNDING_MODE =
      new LetConstant("float_rounding_mode", DataType.INTEGER);

  public static final LetConstant FLOAT_EXCEPTION_FLAGS =
      new LetConstant("float_exception_flags", DataType.INTEGER);

  private final String name;
  private final Expr expr;

  LetConstant(final String name, final Expr expr) {
    checkNotNull(name);
    checkNotNull(expr);

    this.name = name;
    this.expr = expr;
  }

  private LetConstant(final String name, final DataType type) {
    this(name, new Expr(new NodeVariable(name, type)));
  }

  public String getName() {
    return name;
  }

  public Expr getExpr() {
    return expr;
  }

  @Override
  public String toString() {
    return String.format(
        "LetConstant [name=%s, value=%s]", name, expr.getNode());
  }
}

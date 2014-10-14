/*
 * Copyright 2012-2014 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.translator.simnml.ir.shared;

import ru.ispras.microtesk.model.api.memory.MemoryKind;
import ru.ispras.microtesk.translator.simnml.antlrex.WalkerContext;
import ru.ispras.microtesk.translator.simnml.antlrex.WalkerFactoryBase;
import ru.ispras.microtesk.translator.simnml.ir.expression.Expr;

public final class MemoryExprFactory extends WalkerFactoryBase {
  private static final Expr DEFAULT_SIZE = Expr.newConstant(1);

  public MemoryExprFactory(WalkerContext context) {
    super(context);
  }

  public MemoryExpr createMemory(MemoryKind kind, Type type, Expr size) {
    return new MemoryExpr(kind, type, size);
  }

  public MemoryExpr createMemory(MemoryKind kind, Type type) {
    return new MemoryExpr(kind, type, DEFAULT_SIZE);
  }
}

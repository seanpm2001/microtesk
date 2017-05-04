/*
 * Copyright 2015-2016 ISP RAS (http://www.ispras.ru)
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

package ru.ispras.microtesk.translator.nml.ir.analysis;

import ru.ispras.fortress.expression.ExprTreeVisitorDefault;
import ru.ispras.fortress.expression.ExprTreeWalker;
import ru.ispras.fortress.expression.NodeOperation;
import ru.ispras.fortress.expression.NodeVariable;
import ru.ispras.fortress.expression.StandardOperation;
import ru.ispras.fortress.util.InvariantChecks;
import ru.ispras.microtesk.model.memory.Memory;
import ru.ispras.microtesk.translator.nml.NmlSymbolKind;
import ru.ispras.microtesk.translator.nml.ir.Ir;
import ru.ispras.microtesk.translator.nml.ir.expr.Expr;
import ru.ispras.microtesk.translator.nml.ir.expr.Location;
import ru.ispras.microtesk.translator.nml.ir.expr.LocationSourceMemory;
import ru.ispras.microtesk.translator.nml.ir.expr.NodeInfo;
import ru.ispras.microtesk.translator.nml.ir.shared.LetLabel;
import ru.ispras.microtesk.translator.nml.ir.shared.MemoryExpr;

public final class IrInquirer {
  private static final String PC_LABEL = "PC";

  private final Ir ir;

  public IrInquirer(final Ir ir) {
    InvariantChecks.checkNotNull(ir);
    this.ir = ir;
  }

  public boolean isPC(final Expr expr) {
    InvariantChecks.checkNotNull(expr);

    final PCDetector visitor = new PCDetector();
    final ExprTreeWalker walker = new ExprTreeWalker(visitor);

    walker.visit(expr.getNode());
    return visitor.isDetected();
  }

  public boolean isPC(final Location location) {
    InvariantChecks.checkNotNull(location);
    return isRegister(location) && (isExplicitPC(location) || isLabelledAsPC(location));
  }

  public static boolean isRegister(final Location location) {
    InvariantChecks.checkNotNull(location);

    final MemoryExpr memory = getMemoryExpr(location);
    return memory != null && memory.getKind() == Memory.Kind.REG;
  }

  public static boolean isMemory(final Location location) {
    InvariantChecks.checkNotNull(location);

    final MemoryExpr memory = getMemoryExpr(location);
    return memory != null && memory.getKind() == Memory.Kind.MEM;
  }

  private static MemoryExpr getMemoryExpr(final Location location) {
    if (location.getSource().getSymbolKind() != NmlSymbolKind.MEMORY) {
      return null;
    }
    return ((LocationSourceMemory) location.getSource()).getMemory();
  }

  private static boolean isExplicitPC(final Location location) {
    return location.getName().equals(PC_LABEL);
  }

  private boolean isLabelledAsPC(final Location location) {
    if (!ir.getLabels().containsKey(PC_LABEL)) {
      return false;
    }

    final LetLabel label = ir.getLabels().get(PC_LABEL);
    if (!label.getMemoryName().equals(location.getName())) {
      return false;
    }

    final int locationIndex;

    final Expr indexExpr = location.getIndex();
    if (null != indexExpr) {
      if (!indexExpr.isConstant()) {
        return false;
      }
      locationIndex = indexExpr.integerValue();
    } else {
      locationIndex = 0;
    }

    return label.getIndex() == locationIndex;
  }

  private final class PCDetector extends ExprTreeVisitorDefault {
    private boolean detected = false;

    public boolean isDetected() {
      return detected;
    }

    @Override
    public void onVariable(final NodeVariable variable) {
      final NodeInfo nodeInfo = (NodeInfo) variable.getUserData();
      final Location location = (Location) nodeInfo.getSource();

      if (isPC(location)) {
        detected = true;
        setStatus(Status.ABORT);
      }
    }

    @Override
    public void onOperationBegin(final NodeOperation node) {
      if (node.getOperationId() != StandardOperation.BVCONCAT) {
        setStatus(Status.SKIP);
      }
    }

    @Override
    public void onOperationEnd(final NodeOperation node) {
      if (node.getOperationId() != StandardOperation.BVCONCAT) {
        setStatus(Status.OK);
      }
    }
  }
}

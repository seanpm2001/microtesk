package ru.ispras.microtesk.test;

import ru.ispras.microtesk.test.sequence.Sequence;
import ru.ispras.microtesk.test.template.Call;

public interface Solver<T> {
  T solve(Sequence<Call> abstractSequence);
}

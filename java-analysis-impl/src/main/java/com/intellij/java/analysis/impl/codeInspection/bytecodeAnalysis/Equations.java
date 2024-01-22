package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.Optional;

class Equations {
  @Nonnull
  final List<? extends DirectionResultPair> results;
  final boolean stable;

  Equations(@jakarta.annotation.Nonnull List<? extends DirectionResultPair> results, boolean stable) {
    this.results = results;
    this.stable = stable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Equations that = (Equations) o;
    return stable == that.stable && results.equals(that.results);
  }

  @Override
  public int hashCode() {
    return 31 * results.hashCode() + (stable ? 1 : 0);
  }

  @jakarta.annotation.Nonnull
  Equations update(@SuppressWarnings("SameParameterValue") Direction direction, Effects newResult) {
    List<DirectionResultPair> newPairs = StreamEx.of(this.results)
        .map(drp -> drp.updateForDirection(direction, newResult))
        .nonNull()
        .toList();
    return new Equations(newPairs, this.stable);
  }

  Optional<Result> find(Direction direction) {
    int key = direction.asInt();
    return StreamEx.of(results).findFirst(pair -> pair.directionKey == key).map(pair -> pair.result);
  }
}

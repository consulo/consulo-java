package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class DirectionResultPair {
  final int directionKey;
  @Nonnull
  final Result result;

  DirectionResultPair(int directionKey, @Nonnull Result result) {
    this.directionKey = directionKey;
    this.result = result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DirectionResultPair that = (DirectionResultPair) o;
    return directionKey == that.directionKey && result.equals(that.result);
  }

  @Override
  public int hashCode() {
    return 31 * directionKey + result.hashCode();
  }

  @Override
  public String toString() {
    return Direction.fromInt(directionKey) + "->" + result;
  }

  @Nullable
  DirectionResultPair updateForDirection(Direction direction, Result newResult) {
    if (this.directionKey == direction.asInt()) {
      return newResult == null ? null : new DirectionResultPair(direction.asInt(), newResult);
    } else {
      return this;
    }
  }
}
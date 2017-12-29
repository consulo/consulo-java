/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.bytecodeAnalysis;

// data for data analysis
abstract class DataValue implements org.jetbrains.org.objectweb.asm.tree.analysis.Value {
  private final int myHash;

  DataValue(int hash) {
    myHash = hash;
  }

  @Override
  public final int hashCode() {
    return myHash;
  }

  static final DataValue ThisDataValue = new DataValue(-1) {
    @Override
    public int getSize() {
      return 1;
    }
  };
  static final DataValue LocalDataValue = new DataValue(-2) {
    @Override
    public int getSize() {
      return 1;
    }
  };
  static class ParameterDataValue extends DataValue {
    final int n;

    ParameterDataValue(int n) {
      super(n);
      this.n = n;
    }

    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ParameterDataValue that = (ParameterDataValue)o;
      if (n != that.n) return false;
      return true;
    }

  }
  static final DataValue OwnedDataValue = new DataValue(-3) {
    @Override
    public int getSize() {
      return 1;
    }
  };
  static final DataValue UnknownDataValue1 = new DataValue(-4) {
    @Override
    public int getSize() {
      return 1;
    }
  };
  static final DataValue UnknownDataValue2 = new DataValue(-5) {
    @Override
    public int getSize() {
      return 2;
    }
  };
}

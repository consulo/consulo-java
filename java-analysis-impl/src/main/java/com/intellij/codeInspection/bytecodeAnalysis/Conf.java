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

import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;

final class Conf {
  final int insnIndex;
  final Frame<BasicValue> frame;
  final int fastHashCode;

  Conf(int insnIndex, Frame<BasicValue> frame) {
    this.insnIndex = insnIndex;
    this.frame = frame;

    int hash = 0;
    for (int i = 0; i < frame.getLocals(); i++) {
      hash = hash * 31 + frame.getLocal(i).getClass().hashCode();
    }
    for (int i = 0; i < frame.getStackSize(); i++) {
      hash = hash * 31 + frame.getStack(i).getClass().hashCode();
    }
    fastHashCode = hash;
  }
}

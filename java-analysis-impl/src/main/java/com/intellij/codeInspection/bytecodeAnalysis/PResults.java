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

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.tree.analysis.AnalyzerException;

abstract class PResults {
  // SoP = sum of products
  static Set<Set<Key>> join(Set<Set<Key>> sop1, Set<Set<Key>> sop2) {
    Set<Set<Key>> sop = new HashSet<Set<Key>>();
    sop.addAll(sop1);
    sop.addAll(sop2);
    return sop;
  }

  static Set<Set<Key>> meet(Set<Set<Key>> sop1, Set<Set<Key>> sop2) {
    Set<Set<Key>> sop = new HashSet<Set<Key>>();
    for (Set<Key> prod1 : sop1) {
      for (Set<Key> prod2 : sop2) {
        Set<Key> prod = new HashSet<Key>();
        prod.addAll(prod1);
        prod.addAll(prod2);
        sop.add(prod);
      }
    }
    return sop;
  }

  /**
   * 'P' stands for 'Partial'
   */
  interface PResult {}
  static final PResult Identity = new PResult() {
    @Override
    public String toString() {
      return "Identity";
    }
  };
  // similar to top, maximal element
  static final PResult Return = new PResult() {
    @Override
    public String toString() {
      return "Return";
    }
  };
  // minimal element
  static final PResult NPE = new PResult() {
    @Override
    public String toString() {
      return "NPE";
    }
  };
  static final class ConditionalNPE implements PResult {
    final Set<Set<Key>> sop;
    public ConditionalNPE(Set<Set<Key>> sop) throws AnalyzerException
	{
      this.sop = sop;
      checkLimit(sop);
    }

    public ConditionalNPE(Key key) {
      sop = new HashSet<Set<Key>>();
      Set<Key> prod = new HashSet<Key>();
      prod.add(key);
      sop.add(prod);
    }

    static void checkLimit(Set<Set<Key>> sop) throws AnalyzerException {
      int size = 0;
      for (Set<Key> keys : sop) {
        size += keys.size();
      }
      if (size > Analysis.EQUATION_SIZE_LIMIT) {
        throw new AnalyzerException(null, "Equation size is too big");
      }
    }
  }

  static PResult combineNullable(PResult r1, PResult r2) throws AnalyzerException {
    if (Identity == r1) return r2;
    if (Identity == r2) return r1;
    if (Return == r1) return r2;
    if (Return == r2) return r1;
    if (NPE == r1) return NPE;
    if (NPE == r2) return NPE;
    ConditionalNPE cnpe1 = (ConditionalNPE) r1;
    ConditionalNPE cnpe2 = (ConditionalNPE) r2;
    return new ConditionalNPE(join(cnpe1.sop, cnpe2.sop));
  }

  static PResult join(PResult r1, PResult r2) throws AnalyzerException {
    if (Identity == r1) return r2;
    if (Identity == r2) return r1;
    if (Return == r1) return Return;
    if (Return == r2) return Return;
    if (NPE == r1) return r2;
    if (NPE == r2) return r1;
    ConditionalNPE cnpe1 = (ConditionalNPE) r1;
    ConditionalNPE cnpe2 = (ConditionalNPE) r2;
    return new ConditionalNPE(join(cnpe1.sop, cnpe2.sop));
  }

  static PResult meet(PResult r1, PResult r2) throws AnalyzerException {
    if (Identity == r1) return r2;
    if (Return == r1) return r2;
    if (Return == r2) return r1;
    if (NPE == r1) return NPE;
    if (NPE == r2) return NPE;
    if (Identity == r2) return Identity;
    ConditionalNPE cnpe1 = (ConditionalNPE) r1;
    ConditionalNPE cnpe2 = (ConditionalNPE) r2;
    return new ConditionalNPE(meet(cnpe1.sop, cnpe2.sop));
  }

}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeCook.deductive.resolver;

import com.intellij.java.impl.refactoring.typeCook.Settings;
import com.intellij.java.impl.refactoring.typeCook.Util;
import com.intellij.java.impl.refactoring.typeCook.deductive.PsiExtendedTypeVisitor;
import com.intellij.java.impl.refactoring.typeCook.deductive.builder.Constraint;
import com.intellij.java.impl.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.java.impl.refactoring.typeCook.deductive.builder.Subtype;
import com.intellij.java.language.psi.Bottom;
import com.intellij.java.language.psi.PsiTypeVariable;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.component.util.graph.DFSTBuilder;
import consulo.component.util.graph.Graph;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.Pair;

import java.util.*;
import java.util.function.IntConsumer;

/**
 * @author db
 */
public class ResolverTree {
  private static final Logger LOG = Logger.getInstance(ResolverTree.class);

  private ResolverTree[] mySons = new ResolverTree[0];
  private final BindingFactory myBindingFactory;
  private Binding myCurrentBinding;
  private final SolutionHolder mySolutions;
  private final Project myProject;
  private final ObjectIntMap<PsiTypeVariable> myBindingDegree; //How many times this type variable is bound in the system
  private final Settings mySettings;
  private boolean mySolutionFound = false;

  private HashSet<Constraint> myConstraints;

  public ResolverTree(ReductionSystem system) {
    myBindingFactory = new BindingFactory(system);
    mySolutions = new SolutionHolder();
    myCurrentBinding = myBindingFactory.create();
    myConstraints = system.getConstraints();
    myProject = system.getProject();
    myBindingDegree = calculateDegree();
    mySettings = system.getSettings();

    reduceCyclicVariables();
  }

  private ResolverTree(ResolverTree parent, HashSet<Constraint> constraints, Binding binding) {
    myBindingFactory = parent.myBindingFactory;
    myCurrentBinding = binding;
    mySolutions = parent.mySolutions;
    myConstraints = constraints;
    myProject = parent.myProject;
    myBindingDegree = calculateDegree();
    mySettings = parent.mySettings;
  }

  private static class PsiTypeVarCollector extends PsiExtendedTypeVisitor {
    final HashSet<PsiTypeVariable> mySet = new HashSet<PsiTypeVariable>();

    public Object visitTypeVariable(PsiTypeVariable var) {
      mySet.add(var);

      return null;
    }

    public HashSet<PsiTypeVariable> getSet(PsiType type) {
      type.accept(this);
      return mySet;
    }
  }

  private boolean isBoundElseWhere(PsiTypeVariable var) {
    return myBindingDegree.getInt(var) != 1;
  }

  private boolean canBePruned(Binding b) {
    if (mySettings.exhaustive()) {
      return false;
    }
    for (PsiTypeVariable var : b.getBoundVariables()) {
      PsiType type = b.apply(var);

      if (!(type instanceof PsiTypeVariable) && isBoundElseWhere(var)) {
        return false;
      }
    }

    return true;
  }

  private ObjectIntMap<PsiTypeVariable> calculateDegree() {
    ObjectIntMap<PsiTypeVariable> result = ObjectMaps.newObjectIntHashMap();

    for (Constraint constr : myConstraints) {
      PsiTypeVarCollector collector = new PsiTypeVarCollector();

      setDegree(collector.getSet(constr.getRight()), result);
    }

    return result;
  }

  private void setDegree(HashSet<PsiTypeVariable> set, ObjectIntMap<PsiTypeVariable> result) {
    for (PsiTypeVariable var : set) {
      if (result.containsKey(var)) {
        int value = result.getInt(var);
        result.putInt(var, value + 1);
      }
    }
  }

  private HashSet<Constraint> apply(Binding b) {
    HashSet<Constraint> result = new HashSet<Constraint>();

    for (Constraint constr : myConstraints) {
      result.add(constr.apply(b));
    }

    return result;
  }

  private HashSet<Constraint> apply(Binding b, HashSet<Constraint> additional) {
    HashSet<Constraint> result = new HashSet<Constraint>();

    for (Constraint constr : myConstraints) {
      result.add(constr.apply(b));
    }

    for (Constraint constr : additional) {
      result.add(constr.apply(b));
    }

    return result;
  }

  private ResolverTree applyRule(Binding b) {
    Binding newBinding = b != null ? myCurrentBinding.compose(b) : null;

    return newBinding == null ? null : new ResolverTree(this, apply(b), newBinding);
  }

  private ResolverTree applyRule(Binding b, HashSet<Constraint> additional) {
    Binding newBinding = b != null ? myCurrentBinding.compose(b) : null;

    return newBinding == null ? null : new ResolverTree(this, apply(b, additional), newBinding);
  }

  private void reduceCyclicVariables() {
    final HashSet<PsiTypeVariable> nodes = new HashSet<PsiTypeVariable>();
    HashSet<Constraint> candidates = new HashSet<Constraint>();

    final HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>> ins = new HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>>();
    final HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>> outs = new HashMap<PsiTypeVariable, HashSet<PsiTypeVariable>>();

    for (Constraint constraint : myConstraints) {
      PsiType left = constraint.getLeft();
      PsiType right = constraint.getRight();

      if (left instanceof PsiTypeVariable && right instanceof PsiTypeVariable) {
        PsiTypeVariable leftVar = (PsiTypeVariable) left;
        PsiTypeVariable rightVar = (PsiTypeVariable) right;

        candidates.add(constraint);

        nodes.add(leftVar);
        nodes.add(rightVar);

        HashSet<PsiTypeVariable> in = ins.get(leftVar);
        HashSet<PsiTypeVariable> out = outs.get(rightVar);

        if (in == null) {
          HashSet<PsiTypeVariable> newIn = new HashSet<PsiTypeVariable>();

          newIn.add(rightVar);

          ins.put(leftVar, newIn);
        } else {
          in.add(rightVar);
        }

        if (out == null) {
          HashSet<PsiTypeVariable> newOut = new HashSet<PsiTypeVariable>();

          newOut.add(leftVar);

          outs.put(rightVar, newOut);
        } else {
          out.add(leftVar);
        }
      }
    }

    final DFSTBuilder<PsiTypeVariable> dfstBuilder = new DFSTBuilder<PsiTypeVariable>(new Graph<PsiTypeVariable>() {
      public Collection<PsiTypeVariable> getNodes() {
        return nodes;
      }

      public Iterator<PsiTypeVariable> getIn(PsiTypeVariable n) {
        HashSet<PsiTypeVariable> in = ins.get(n);

        if (in == null) {
          return new HashSet<PsiTypeVariable>().iterator();
        }

        return in.iterator();
      }

      public Iterator<PsiTypeVariable> getOut(PsiTypeVariable n) {
        HashSet<PsiTypeVariable> out = outs.get(n);

        if (out == null) {
          return new HashSet<PsiTypeVariable>().iterator();
        }

        return out.iterator();
      }

    });

    IntList sccs = dfstBuilder.getSCCs();
    final HashMap<PsiTypeVariable, Integer> index = new HashMap<PsiTypeVariable, Integer>();

    sccs.forEach(new IntConsumer() {
      int myTNumber = 0;

      public void accept(int size) {
        for (int j = 0; j < size; j++) {
          index.put(dfstBuilder.getNodeByTNumber(myTNumber + j), myTNumber);
        }
        myTNumber += size;
      }
    });

    for (Constraint constraint : candidates) {
      if (index.get(constraint.getLeft()).equals(index.get(constraint.getRight()))) {
        myConstraints.remove(constraint);
      }
    }

    Binding binding = myBindingFactory.create();

    for (PsiTypeVariable fromVar : index.keySet()) {
      PsiTypeVariable toVar = dfstBuilder.getNodeByNNumber(index.get(fromVar).intValue());

      if (!fromVar.equals(toVar)) {
        binding = binding.compose(myBindingFactory.create(fromVar, toVar));

        if (binding == null) {
          break;
        }
      }
    }

    if (binding != null && binding.nonEmpty()) {
      myCurrentBinding = myCurrentBinding.compose(binding);
      myConstraints = apply(binding);
    }
  }

  private void reduceTypeType(Constraint constr) {
    PsiType left = constr.getLeft();
    PsiType right = constr.getRight();
    HashSet<Constraint> addendumRise = new HashSet<Constraint>();
    HashSet<Constraint> addendumSink = new HashSet<Constraint>();
    HashSet<Constraint> addendumWcrd = new HashSet<Constraint>();

    int numSons = 0;
    Binding riseBinding = myBindingFactory.rise(left, right, addendumRise);
    if (riseBinding != null) {
      numSons++;
    }
    Binding sinkBinding = myBindingFactory.sink(left, right, addendumSink);
    if (sinkBinding != null) {
      numSons++;
    }
    Binding wcrdBinding = mySettings.cookToWildcards() ? myBindingFactory.riseWithWildcard(left, right, addendumWcrd) : null;
    if (wcrdBinding != null) {
      numSons++;
    }
    Binding omitBinding = null;

    if (mySettings.exhaustive()) {
      PsiClassType.ClassResolveResult rightResult = Util.resolveType(right);
      PsiClassType.ClassResolveResult leftResult = Util.resolveType(left);

      PsiClass rightClass = rightResult.getElement();
      PsiClass leftClass = leftResult.getElement();

      if (rightClass != null && leftClass != null && rightClass.getManager().areElementsEquivalent(rightClass, leftClass)) {
        if (PsiUtil.typeParametersIterator(rightClass).hasNext()) {
          omitBinding = myBindingFactory.create();
          numSons++;
          for (PsiType type : rightResult.getSubstitutor().getSubstitutionMap().values()) {
            if (!(type instanceof Bottom)) {
              numSons--;
              omitBinding = null;
              break;
            }
          }
        }
      }
    }

    if (numSons == 0) {
      return;
    }

    if ((riseBinding != null && sinkBinding != null && riseBinding.equals(sinkBinding)) || canBePruned(riseBinding)) {
      numSons--;
      sinkBinding = null;
    }

    if (riseBinding != null && wcrdBinding != null && riseBinding.equals(wcrdBinding)) {
      numSons--;
      wcrdBinding = null;
    }

    myConstraints.remove(constr);

    mySons = new ResolverTree[numSons];

    int n = 0;

    if (riseBinding != null) {
      mySons[n++] = applyRule(riseBinding, addendumRise);
    }

    if (wcrdBinding != null) {
      mySons[n++] = applyRule(wcrdBinding, addendumWcrd);
    }

    if (omitBinding != null) {
      mySons[n++] = applyRule(omitBinding, addendumWcrd);
    }

    if (sinkBinding != null) {
      mySons[n++] = applyRule(sinkBinding, addendumSink);
    }
  }

  private void fillTypeRange(PsiType lowerBound,
                             PsiType upperBound,
                             HashSet<PsiType> holder) {
    if (lowerBound instanceof PsiClassType && upperBound instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resultLower = ((PsiClassType) lowerBound).resolveGenerics();
      PsiClassType.ClassResolveResult resultUpper = ((PsiClassType) upperBound).resolveGenerics();

      PsiClass lowerClass = resultLower.getElement();
      PsiClass upperClass = resultUpper.getElement();

      if (lowerClass != null && upperClass != null && !lowerClass.equals(upperClass)) {
        PsiSubstitutor upperSubst = resultUpper.getSubstitutor();
        PsiClass[] parents = upperClass.getSupers();
        PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

        for (PsiClass parent : parents) {
          PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(parent, upperClass, upperSubst);
          if (superSubstitutor != null) {
            PsiClassType type = factory.createType(parent, superSubstitutor);
            holder.add(type);
            fillTypeRange(lowerBound, type, holder);
          }
        }
      }
    } else if (lowerBound instanceof PsiArrayType && upperBound instanceof PsiArrayType) {
      fillTypeRange(((PsiArrayType) lowerBound).getComponentType(), ((PsiArrayType) upperBound).getComponentType(), holder);
    }
  }

  private PsiType[] getTypeRange(PsiType lowerBound, PsiType upperBound) {
    HashSet<PsiType> range = new HashSet<PsiType>();

    range.add(lowerBound);
    range.add(upperBound);

    fillTypeRange(lowerBound, upperBound, range);

    return range.toArray(new PsiType[]{});
  }

  private void reduceInterval(Constraint left, Constraint right) {
    PsiType leftType = left.getLeft();
    PsiType rightType = right.getRight();
    PsiTypeVariable var = (PsiTypeVariable) left.getRight();

    if (leftType.equals(rightType)) {
      Binding binding = myBindingFactory.create(var, leftType);

      myConstraints.remove(left);
      myConstraints.remove(right);

      mySons = new ResolverTree[]{applyRule(binding)};

      return;
    }

    Binding riseBinding = myBindingFactory.rise(leftType, rightType, null);
    Binding sinkBinding = myBindingFactory.sink(leftType, rightType, null);

    int indicator = (riseBinding == null ? 0 : 1) + (sinkBinding == null ? 0 : 1);

    if (indicator == 0) {
      return;
    } else if ((indicator == 2 && riseBinding.equals(sinkBinding)) || canBePruned(riseBinding)) {
      indicator = 1;
      sinkBinding = null;
    }

    PsiType[] riseRange = PsiType.EMPTY_ARRAY;
    PsiType[] sinkRange = PsiType.EMPTY_ARRAY;

    if (riseBinding != null) {
      riseRange = getTypeRange(riseBinding.apply(rightType), riseBinding.apply(leftType));
    }

    if (sinkBinding != null) {
      sinkRange = getTypeRange(sinkBinding.apply(rightType), sinkBinding.apply(leftType));
    }

    if (riseRange.length + sinkRange.length > 0) {
      myConstraints.remove(left);
      myConstraints.remove(right);
    }

    mySons = new ResolverTree[riseRange.length + sinkRange.length];

    for (int i = 0; i < riseRange.length; i++) {
      PsiType type = riseRange[i];

      mySons[i] = applyRule(riseBinding.compose(myBindingFactory.create(var, type)));
    }

    for (int i = 0; i < sinkRange.length; i++) {
      PsiType type = sinkRange[i];

      mySons[i + riseRange.length] = applyRule(sinkBinding.compose(myBindingFactory.create(var, type)));
    }
  }

  private void reduce() {
    if (myConstraints.size() == 0) {
      return;
    }

    if (myCurrentBinding.isCyclic()) {
      reduceCyclicVariables();
    }

    HashMap<PsiTypeVariable, Constraint> myTypeVarConstraints = new HashMap<PsiTypeVariable, Constraint>();
    HashMap<PsiTypeVariable, Constraint> myVarTypeConstraints = new HashMap<PsiTypeVariable, Constraint>();

    for (Constraint constr : myConstraints) {
      PsiType left = constr.getLeft();
      PsiType right = constr.getRight();

      switch ((left instanceof PsiTypeVariable ? 0 : 1) + (right instanceof PsiTypeVariable ? 0 : 2)) {
        case 0:
          continue;

        case 1: {
          Constraint c = myTypeVarConstraints.get(right);

          if (c == null) {
            Constraint d = myVarTypeConstraints.get(right);

            if (d != null) {
              reduceInterval(constr, d);
              return;
            }

            myTypeVarConstraints.put((PsiTypeVariable) right, constr);
          } else {
            reduceTypeVar(constr, c);
            return;
          }
        }
        break;

        case 2: {
          Constraint c = myVarTypeConstraints.get(left);

          if (c == null) {
            Constraint d = myTypeVarConstraints.get(left);

            if (d != null) {
              reduceInterval(d, constr);
              return;
            }

            myVarTypeConstraints.put((PsiTypeVariable) left, constr);
          } else {
            reduceVarType(constr, c);
            return;
          }
          break;
        }

        case 3:
          reduceTypeType(constr);
          return;
      }
    }

    //T1 < a < b ... < T2
    {
      for (Constraint constr : myConstraints) {
        PsiType left = constr.getLeft();
        PsiType right = constr.getRight();

        if (!(left instanceof PsiTypeVariable) && right instanceof PsiTypeVariable) {
          HashSet<PsiTypeVariable> bound = new PsiTypeVarCollector().getSet(left);

          if (bound.contains(right)) {
            myConstraints.remove(constr);
            mySons = new ResolverTree[]{applyRule(myBindingFactory.create(((PsiTypeVariable) right), Bottom.BOTTOM))};

            return;
          }

          PsiManager manager = PsiManager.getInstance(myProject);
          PsiType leftType = left instanceof PsiWildcardType ? ((PsiWildcardType) left).getBound() : left;
          PsiType[] types = getTypeRange(PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(myProject)), leftType);

          mySons = new ResolverTree[types.length];

          if (types.length > 0) {
            myConstraints.remove(constr);
          }

          for (int i = 0; i < types.length; i++) {
            PsiType type = types[i];
            mySons[i] = applyRule(myBindingFactory.create(((PsiTypeVariable) right), type));
          }

          return;
        }
      }
    }

    //T1 < a < b < ...
    {
      HashSet<PsiTypeVariable> haveLeftBound = new HashSet<PsiTypeVariable>();

      Constraint target = null;
      HashSet<PsiTypeVariable> boundVariables = new HashSet<PsiTypeVariable>();

      for (Constraint constr : myConstraints) {
        PsiType leftType = constr.getLeft();
        PsiType rightType = constr.getRight();

        if (leftType instanceof PsiTypeVariable) {
          boundVariables.add((PsiTypeVariable) leftType);

          if (rightType instanceof PsiTypeVariable) {
            boundVariables.add((PsiTypeVariable) rightType);
            haveLeftBound.add(((PsiTypeVariable) rightType));
          } else if (!Util.bindsTypeVariables(rightType)) {
            target = constr;
          }
        }
      }

      if (target == null) {
        if (mySettings.exhaustive()) {
          for (Constraint constr : myConstraints) {
            PsiType left = constr.getLeft();
            PsiType right = constr.getRight();

            PsiType[] range = null;
            PsiTypeVariable var = null;

            if (left instanceof PsiTypeVariable && !(right instanceof PsiTypeVariable)) {
              range = getTypeRange(PsiType.getJavaLangObject(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject)),
                  right);
              var = (PsiTypeVariable) left;
            }

            if (range == null && right instanceof PsiTypeVariable && !(left instanceof PsiTypeVariable)) {
              range = new PsiType[]{right};
              var = (PsiTypeVariable) right;
            }

            if (range != null) {
              mySons = new ResolverTree[range.length];

              for (int i = 0; i < range.length; i++) {
                mySons[i] = applyRule(myBindingFactory.create(var, range[i]));
              }

              return;
            }
          }
        }

        Binding binding = myBindingFactory.create();

        for (PsiTypeVariable var : myBindingFactory.getBoundVariables()) {
          if (!myCurrentBinding.binds(var) && !boundVariables.contains(var)) {
            binding = binding.compose(myBindingFactory.create(var, Bottom.BOTTOM));
          }
        }

        if (!binding.nonEmpty()) {
          myConstraints.clear();
        }

        mySons = new ResolverTree[]{applyRule(binding)};
      } else {
        PsiType type = target.getRight();
        PsiTypeVariable var = (PsiTypeVariable) target.getLeft();

        Binding binding =
            (haveLeftBound.contains(var) || type instanceof PsiWildcardType) || !mySettings.cookToWildcards()
                ? myBindingFactory.create(var, type)
                : myBindingFactory.create(var, PsiWildcardType.createExtends(PsiManager.getInstance(myProject), type));

        myConstraints.remove(target);

        mySons = new ResolverTree[]{applyRule(binding)};
      }
    }
  }

  private void logSolution() {
    LOG.debug("Reduced system:");

    for (Constraint constr : myConstraints) {
      LOG.debug(constr.toString());
    }

    LOG.debug("End of Reduced system.");
    LOG.debug("Reduced binding:");
    LOG.debug(myCurrentBinding.toString());
    LOG.debug("End of Reduced binding.");
  }

  private interface Reducer {
    LinkedList<Pair<PsiType, Binding>> unify(PsiType x, PsiType y);

    Constraint create(PsiTypeVariable var, PsiType type);

    PsiType getType(Constraint c);

    PsiTypeVariable getVar(Constraint c);
  }

  private void reduceTypeVar(Constraint x, Constraint y) {
    reduceSideVar(x, y, new Reducer() {
      public LinkedList<Pair<PsiType, Binding>> unify(PsiType x, PsiType y) {
        return myBindingFactory.intersect(x, y);
      }

      public Constraint create(PsiTypeVariable var, PsiType type) {
        return new Subtype(type, var);
      }

      public PsiType getType(Constraint c) {
        return c.getLeft();
      }

      public PsiTypeVariable getVar(Constraint c) {
        return (PsiTypeVariable) c.getRight();
      }
    });
  }

  private void reduceVarType(Constraint x, Constraint y) {
    reduceSideVar(x, y, new Reducer() {
      public LinkedList<Pair<PsiType, Binding>> unify(PsiType x, PsiType y) {
        return myBindingFactory.union(x, y);
      }

      public Constraint create(PsiTypeVariable var, PsiType type) {
        return new Subtype(var, type);
      }

      public PsiType getType(Constraint c) {
        return c.getRight();
      }

      public PsiTypeVariable getVar(Constraint c) {
        return (PsiTypeVariable) c.getLeft();
      }
    });
  }

  private void reduceSideVar(Constraint x, Constraint y, Reducer reducer) {
    PsiTypeVariable var = reducer.getVar(x);

    PsiType xType = reducer.getType(x);
    PsiType yType = reducer.getType(y);

    LinkedList<Pair<PsiType, Binding>> union = reducer.unify(xType, yType);

    if (union.size() == 0) {
      return;
    }

    myConstraints.remove(x);
    myConstraints.remove(y);

    mySons = new ResolverTree[union.size()];
    int i = 0;

    Constraint prev = null;

    for (Pair<PsiType, Binding> pair : union) {
      if (prev != null) {
        myConstraints.remove(prev);
      }

      prev = reducer.create(var, pair.getFirst());
      myConstraints.add(prev);

      mySons[i++] = applyRule(pair.getSecond());
    }
  }

  public void resolve() {
    reduce();

    if (mySons.length > 0) {
      for (int i = 0; i < mySons.length; i++) {

        if (mySons[i] != null) {
          mySons[i].resolve();
          if (!mySettings.exhaustive() && mySettings.cookToWildcards() && mySons[i].mySolutionFound) {
            break;
          }
          mySons[i] = null;
        }
      }
    } else {
      if (myConstraints.size() == 0) {
        logSolution();

        mySolutions.putSolution(myCurrentBinding);
        mySolutionFound = true;
      }
    }
  }

  public Binding getBestSolution() {
    return mySolutions.getBestSolution();
  }
}

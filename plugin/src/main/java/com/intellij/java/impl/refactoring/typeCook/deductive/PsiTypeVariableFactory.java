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
package com.intellij.java.impl.refactoring.typeCook.deductive;

import com.intellij.java.language.psi.PsiTypeVariable;
import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * @author db
 */
public class PsiTypeVariableFactory {
  private int myCurrent = 0;
  private final LinkedList<HashSet<PsiTypeVariable>> myClusters = new LinkedList<HashSet<PsiTypeVariable>>();
  private final HashMap<Integer, HashSet<PsiTypeVariable>> myVarCluster = new HashMap<Integer, HashSet<PsiTypeVariable>>();

  public final int getNumber() {
    return myCurrent;
  }

  public final void registerCluster(HashSet<PsiTypeVariable> cluster) {
    myClusters.add(cluster);

    for (PsiTypeVariable aCluster : cluster) {
      myVarCluster.put(new Integer(aCluster.getIndex()), cluster);
    }
  }

  public final LinkedList<HashSet<PsiTypeVariable>> getClusters() {
    return myClusters;
  }

  public final HashSet<PsiTypeVariable> getClusterOf(int var) {
    return myVarCluster.get(new Integer(var));
  }

  public final PsiTypeVariable create() {
    return create(null);
  }

  public final PsiTypeVariable create(final PsiElement context) {
    return new PsiTypeVariable() {
      private final int myIndex = myCurrent++;
      private final PsiElement myContext = context;

      public boolean isValidInContext(PsiType type) {
        if (myContext == null) {
          return true;
        }

        if (type == null) {
          return true;
        }

        return type.accept(new PsiTypeVisitor<Boolean>() {
          public Boolean visitType(PsiType type) {
            return Boolean.TRUE;
          }

          public Boolean visitArrayType(PsiArrayType arrayType) {
            return arrayType.getDeepComponentType().accept(this);
          }

          public Boolean visitWildcardType(PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();

            if (bound != null) {
              bound.accept(this);
            }

            return Boolean.TRUE;
          }

          public Boolean visitClassType(PsiClassType classType) {
            PsiClassType.ClassResolveResult result = classType.resolveGenerics();
            PsiClass aClass = result.getElement();
            PsiSubstitutor aSubst = result.getSubstitutor();

            if (aClass != null) {
              PsiManager manager = aClass.getManager();
              JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());

              if (aClass instanceof PsiTypeParameter) {
                PsiTypeParameterListOwner owner = PsiTreeUtil.getParentOfType(myContext, PsiTypeParameterListOwner.class);

                if (owner != null) {
                  boolean found = false;

                  for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(owner)) {
                    found = manager.areElementsEquivalent(typeParameter, aClass);
                    if (found) break;
                  }

                  if (!found) {
                    return Boolean.FALSE;
                  }
                }
                else {
                  return Boolean.FALSE;
                }
              }
              else if (!facade.getResolveHelper().isAccessible(aClass, myContext, null)) {
                return Boolean.FALSE;
              }

              for (PsiTypeParameter parm : PsiUtil.typeParametersIterable(aClass)) {
                PsiType type = aSubst.substitute(parm);

                if (type != null) {
                  Boolean b = type.accept(this);

                  if (!b.booleanValue()) {
                    return Boolean.FALSE;
                  }
                }
              }

              return Boolean.TRUE;
            }
            else {
              return Boolean.FALSE;
            }
          }
        }).booleanValue();
      }

      public String getPresentableText() {
        return "$" + myIndex;
      }

      public String getCanonicalText() {
        return getPresentableText();
      }

      public String getInternalCanonicalText() {
        return getCanonicalText();
      }

      public boolean isValid() {
        return true;
      }

      public boolean equalsToText(String text) {
        return text.equals(getPresentableText());
      }

      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @Nonnull
      public PsiType[] getSuperTypes() {
        return new PsiType[0];
      }

      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PsiTypeVariable)) return false;

        PsiTypeVariable psiTypeVariable = (PsiTypeVariable)o;

        if (myIndex != psiTypeVariable.getIndex()) return false;

        return true;
      }

      public int hashCode() {
        return myIndex;
      }

      public int getIndex() {
        return myIndex;
      }
    };
  }
}

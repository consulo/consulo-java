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
package com.intellij.java.impl.refactoring.typeCook.deductive.builder;

import com.intellij.java.language.psi.Bottom;
import com.intellij.java.language.psi.PsiTypeVariable;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.refactoring.typeCook.Settings;
import com.intellij.java.impl.refactoring.typeCook.Util;
import com.intellij.java.impl.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.java.impl.refactoring.typeCook.deductive.resolver.Binding;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author db
 */
public class ReductionSystem {
  final HashSet<Constraint> myConstraints = new HashSet<Constraint>();
  final HashSet<PsiElement> myElements;
  final HashMap<PsiTypeCastExpression, PsiType> myCastToOperandType;
  final HashMap<PsiElement, PsiType> myTypes;
  final PsiTypeVariableFactory myTypeVariableFactory;
  final Project myProject;
  final Settings mySettings;

  HashSet<PsiTypeVariable> myBoundVariables;

  public ReductionSystem(Project project,
                         HashSet<PsiElement> elements,
                         HashMap<PsiElement, PsiType> types,
                         PsiTypeVariableFactory factory,
                         Settings settings) {
    myProject = project;
    myElements = elements;
    myTypes = types;
    myTypeVariableFactory = factory;
    myBoundVariables = null;
    mySettings = settings;
    myCastToOperandType = new HashMap<PsiTypeCastExpression, PsiType>();
  }

  public Project getProject() {
    return myProject;
  }

  public HashSet<Constraint> getConstraints() {
    return myConstraints;
  }

  public void addCast(PsiTypeCastExpression cast, PsiType operandType){
    myCastToOperandType.put(cast, operandType);
  }

  public void addSubtypeConstraint(PsiType left, PsiType right) {
    if (left instanceof PsiPrimitiveType) left = ((PsiPrimitiveType)left).getBoxedType(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
    if (right instanceof PsiPrimitiveType) right = ((PsiPrimitiveType)right).getBoxedType(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
    if (left == null || right == null) {
      return;
    }

    if ((Util.bindsTypeVariables(left) || Util.bindsTypeVariables(right))
    ) {
      Subtype c = new Subtype(left, right);
      if (!myConstraints.contains(c)) {
        myConstraints.add(c);
      }
    }
  }

  private static String memberString(PsiMember member) {
    return member.getContainingClass().getQualifiedName() + "." + member.getName();
  }

  private static String variableString(PsiLocalVariable var) {
    PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);

    return memberString(method) + "#" + var.getName();
  }

  @SuppressWarnings({"StringConcatenationInsideStringBufferAppend"})
  public String toString() {
    @NonNls StringBuffer buffer = new StringBuffer();

    buffer.append("Victims:\n");

    for (PsiElement element : myElements) {
      PsiType type = myTypes.get(element);

      if (type == null) {
        continue;
      }

      if (element instanceof PsiParameter) {
        PsiParameter parm = (PsiParameter)element;
        PsiElement declarationScope = parm.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)declarationScope;

          buffer.append("   parameter " + method.getParameterList().getParameterIndex(parm) + " of " + memberString(method));
        }
        else {
          buffer.append("   parameter of foreach");
        }
      }
      else if (element instanceof PsiField) {
        buffer.append("   field " + memberString(((PsiField)element)));
      }
      else if (element instanceof PsiLocalVariable) {
        buffer.append("   local " + variableString(((PsiLocalVariable)element)));
      }
      else if (element instanceof PsiMethod) {
        buffer.append("   return of " + memberString(((PsiMethod)element)));
      }
      else if (element instanceof PsiNewExpression) {
        buffer.append("   " + element.getText());
      }
      else if (element instanceof PsiTypeCastExpression) {
        buffer.append("   " + element.getText());
      }
      else {
        buffer.append("   unknown: " + (element == null ? "null" : element.getClass().getName()));
      }

      buffer.append(" " + type.getCanonicalText() + "\n");
    }

    buffer.append("Variables: " + myTypeVariableFactory.getNumber() + "\n");
    buffer.append("Bound variables: ");

    if (myBoundVariables == null) {
      buffer.append(" not specified\n");
    }
    else {
      for (PsiTypeVariable boundVariable : myBoundVariables) {
        buffer.append(boundVariable.getIndex() + ", ");
      }
    }

    buffer.append("Constraints: " + myConstraints.size() + "\n");

    for (Constraint constraint : myConstraints) {
      buffer.append("   " + constraint + "\n");
    }

    return buffer.toString();
  }

  public ReductionSystem[] isolate() {
    class Node {
      int myComponent = -1;
      Constraint myConstraint;
      HashSet<Node> myNeighbours = new HashSet<Node>();

      public Node() {
        myConstraint = null;
      }

      public Node(Constraint c) {
        myConstraint = c;
      }

      public Constraint getConstraint() {
        return myConstraint;
      }

      public void addEdge(Node n) {
        if (!myNeighbours.contains(n)) {
          myNeighbours.add(n);
          n.addEdge(this);
        }
      }
    }

    Node[] typeVariableNodes = new Node[myTypeVariableFactory.getNumber()];
    Node[] constraintNodes = new Node[myConstraints.size()];
    HashMap<Constraint, HashSet<PsiTypeVariable>> boundVariables = new HashMap<Constraint, HashSet<PsiTypeVariable>>();

    for (int i = 0; i < typeVariableNodes.length; i++) {
      typeVariableNodes[i] = new Node();
    }

    {
      int j = 0;

      for (Constraint constraint : myConstraints) {
        constraintNodes[j++] = new Node(constraint);
      }
    }

    {
      int l = 0;

      for (Constraint constraint : myConstraints) {
        final HashSet<PsiTypeVariable> boundVars = new HashSet<PsiTypeVariable>();
        Node constraintNode = constraintNodes[l++];

        new Object() {
          void visit(Constraint c) {
            visit(c.getLeft());
            visit(c.getRight());
          }

          private void visit(PsiType t) {
            if (t instanceof PsiTypeVariable) {
              boundVars.add((PsiTypeVariable)t);
            }
            else if (t instanceof PsiArrayType) {
              visit(t.getDeepComponentType());
            }
            else if (t instanceof PsiClassType) {
              PsiSubstitutor subst = Util.resolveType(t).getSubstitutor();

              for (PsiType type : subst.getSubstitutionMap().values()) {
                visit(type);
              }
            }
            else if (t instanceof PsiIntersectionType) {
              PsiType[] conjuncts = ((PsiIntersectionType)t).getConjuncts();
              for (PsiType conjunct : conjuncts) {
                visit(conjunct);

              }
            }
            else if (t instanceof PsiWildcardType) {
              PsiType bound = ((PsiWildcardType)t).getBound();

              if (bound != null) {
                visit(bound);
              }
            }
          }
        }.visit(constraint);

        PsiTypeVariable[] bound = boundVars.toArray(new PsiTypeVariable[]{});

        for (int j = 0; j < bound.length; j++) {
          int x = bound[j].getIndex();
          Node typeVariableNode = typeVariableNodes[x];

          typeVariableNode.addEdge(constraintNode);

          for (int k = j + 1; k < bound.length; k++) {
            int y = bound[k].getIndex();

            typeVariableNode.addEdge(typeVariableNodes[y]);
          }
        }

        boundVariables.put(constraint, boundVars);
      }
    }

    LinkedList<HashSet<PsiTypeVariable>> clusters = myTypeVariableFactory.getClusters();

    for (HashSet<PsiTypeVariable> cluster : clusters) {
      Node prev = null;

      for (PsiTypeVariable variable : cluster) {
        Node curr = typeVariableNodes[variable.getIndex()];

        if (prev != null) {
          prev.addEdge(curr);
        }

        prev = curr;
      }
    }

    int currComponent = 0;

    for (Node node : typeVariableNodes) {
      if (node.myComponent == -1) {
        final int component = currComponent;
        new Object() {
          void selectComponent(Node n) {
            LinkedList<Node> frontier = new LinkedList<Node>();

            frontier.addFirst(n);

            while (frontier.size() > 0) {
              Node curr = frontier.removeFirst();

              curr.myComponent = component;

              for (Node p : curr.myNeighbours) {
                if (p.myComponent == -1) {
                  frontier.addFirst(p);
                }
              }
            }
          }
        }.selectComponent(node);

        currComponent++;
      }
    }

    ReductionSystem[] systems = new ReductionSystem[currComponent];

    for (Node node : constraintNodes) {
      Constraint constraint = node.getConstraint();
      int index = node.myComponent;

      if (systems[index] == null) {
        systems[index] = new ReductionSystem(myProject, myElements, myTypes, myTypeVariableFactory, mySettings);
      }

      systems[index].addConstraint(constraint, boundVariables.get(constraint));
    }

    return systems;
  }

  private void addConstraint(Constraint constraint, HashSet<PsiTypeVariable> vars) {
    if (myBoundVariables == null) {
      myBoundVariables = vars;
    }
    else {
      myBoundVariables.addAll(vars);
    }

    myConstraints.add(constraint);
  }

  public PsiTypeVariableFactory getVariableFactory() {
    return myTypeVariableFactory;
  }

  public HashSet<PsiTypeVariable> getBoundVariables() {
    return myBoundVariables;
  }

  public @NonNls String dumpString() {
    @NonNls String[] data = new String[myElements.size()];

    int i = 0;

    for (PsiElement element : myElements) {
      data[i++] = Util.getType(element).getCanonicalText() + "\\n" + elementString(element);
    }

    Arrays.sort(data,
                new Comparator<String>() {
                  public int compare(String x, String y) {
                    return x.compareTo(y);
                  }
                });


    StringBuffer repr = new StringBuffer();

    for (String aData : data) {
      repr.append(aData);
      repr.append("\n");
    }

    return repr.toString();
  }

  @NonNls private static
  String elementString(PsiElement element) {
    if (element instanceof PsiNewExpression) {
      return "new";
    }

    if (element instanceof PsiParameter) {
      PsiElement scope = ((PsiParameter)element).getDeclarationScope();

      if (scope instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)scope;
        return "parameter " + (method.getParameterList().getParameterIndex(((PsiParameter)element))) + " of " + method.getName();
      }
    }

    if (element instanceof PsiMethod) {
      return "return of " + ((PsiMethod)element).getName();
    }

    return element.toString();
  }

  public String dumpResult(final Binding bestBinding) {
    @NonNls String[] data = new String[myElements.size()];

    class Substitutor {
      PsiType substitute(PsiType t) {
        if (t instanceof PsiWildcardType) {
          PsiWildcardType wcType = (PsiWildcardType)t;
          PsiType bound = wcType.getBound();

          if (bound == null) {
            return t;
          }

          PsiManager manager = PsiManager.getInstance(myProject);
          PsiType subst = substitute(bound);
          return subst == null || subst instanceof PsiWildcardType ? subst : wcType.isExtends()
                                                                             ? PsiWildcardType.createExtends(manager, subst)
                                                                             : PsiWildcardType.createSuper(manager, subst);
        }
        else if (t instanceof PsiTypeVariable) {
          if (bestBinding != null) {
            PsiType b = bestBinding.apply(t);

            if (b instanceof Bottom || b instanceof PsiTypeVariable) {
              return null;
            }

            return substitute(b);
          }

          return null;
        }
        else if (t instanceof Bottom) {
          return null;
        }
        else if (t instanceof PsiArrayType) {
          return substitute(((PsiArrayType)t).getComponentType()).createArrayType();
        }
        else if (t instanceof PsiClassType) {
          PsiClassType.ClassResolveResult result = ((PsiClassType)t).resolveGenerics();

          PsiClass aClass = result.getElement();
          PsiSubstitutor aSubst = result.getSubstitutor();

          if (aClass == null) {
            return t;
          }

          PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

          for (PsiTypeParameter parm : aSubst.getSubstitutionMap().keySet()) {
            PsiType type = aSubst.substitute(parm);

            theSubst = theSubst.put(parm, substitute(type));
          }

          return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass, theSubst);
        }
        else {
          return t;
        }
      }
    }

    Substitutor binding = new Substitutor();
    int i = 0;

    for (PsiElement element : myElements) {
      PsiType t = myTypes.get(element);
      if (t != null) {
        data[i++] = binding.substitute(t).getCanonicalText() + "\\n" + elementString(element);
      }
      else {
        data[i++] = "\\n" + elementString(element);
      }
    }

    Arrays.sort(data,
                new Comparator<String>() {
                  public int compare(String x, String y) {
                    return x.compareTo(y);
                  }
                });


    StringBuffer repr = new StringBuffer();

    for (String aData : data) {
      repr.append(aData);
      repr.append("\n");
    }

    return repr.toString();
  }

  public Settings getSettings() {
    return mySettings;
  }
}

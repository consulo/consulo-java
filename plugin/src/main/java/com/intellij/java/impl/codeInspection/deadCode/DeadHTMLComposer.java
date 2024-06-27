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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2001
 * Time: 4:58:38 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.codeInspection.deadCode;

import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.analysis.impl.codeInspection.reference.RefClassImpl;
import com.intellij.java.analysis.impl.codeInspection.reference.RefMethodImpl;
import com.intellij.java.impl.codeInspection.HTMLJavaHTMLComposer;
import consulo.ide.impl.idea.codeInspection.ex.DescriptorComposer;
import consulo.ide.impl.idea.codeInspection.ui.InspectionToolPresentation;
import consulo.ide.impl.idea.codeInspection.ui.InspectionTreeNode;
import consulo.ide.impl.idea.codeInspection.ui.RefElementNode;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.inspection.HTMLComposerBase;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

public class DeadHTMLComposer extends HTMLComposerBase {
  private final InspectionToolPresentation myToolPresentation;
  private final HTMLJavaHTMLComposer myComposer;

  public DeadHTMLComposer(@Nonnull InspectionToolPresentation presentation) {
    myToolPresentation = presentation;
    myComposer = getExtension(HTMLJavaHTMLComposer.COMPOSER);
  }

  @Override
  public void compose(final StringBuffer buf, RefEntity refEntity) {
    genPageHeader(buf, refEntity);

    if (refEntity instanceof RefElementImpl refElement) {
      if (refElement.isSuspicious() && !refElement.isEntry()) {
        appendHeading(buf, InspectionLocalize.inspectionProblemSynopsis().get());
        //noinspection HardCodedStringLiteral
        buf.append("<br>");
        appendAfterHeaderIndention(buf);
        appendProblemSynopsis(refElement, buf);

        //noinspection HardCodedStringLiteral
        buf.append("<br><br>");
        appendResolution(buf, refElement, DescriptorComposer.quickFixTexts(refElement, myToolPresentation));
        refElement.accept(new RefJavaVisitor() {
          @Override public void visitClass(@Nonnull RefClass aClass) {
            appendClassInstantiations(buf, aClass);
            myComposer.appendDerivedClasses(buf, aClass);
            myComposer.appendClassExtendsImplements(buf, aClass);
            myComposer.appendLibraryMethods(buf, aClass);
            myComposer.appendTypeReferences(buf, aClass);
          }

          @Override public void visitMethod(@Nonnull RefMethod method) {
            appendElementInReferences(buf, method);
            appendElementOutReferences(buf, method);
            myComposer.appendDerivedMethods(buf, method);
            myComposer.appendSuperMethods(buf, method);
          }

          @Override public void visitField(@Nonnull RefField field) {
            appendElementInReferences(buf, field);
            appendElementOutReferences(buf, field);
          }
        });
      } else {
        appendNoProblems(buf);
      }
      appendCallesList(refElement, buf, new HashSet<>(), true);
    }
  }

  public static void appendProblemSynopsis(final RefElement refElement, final StringBuffer buf) {
    refElement.accept(new RefJavaVisitor() {
      @Override public void visitField(@Nonnull RefField field) {
        if (field.isUsedForReading() && !field.isUsedForWriting()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis().get());
          return;
        }

        if (!field.isUsedForReading() && field.isUsedForWriting()) {
          if (field.isOnlyAssignedInInitializer()) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis1());
            return;
          }

          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis2());
          return;
        }

        int nUsages = field.getInReferences().size();
        if (nUsages == 0) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis1());
        } else if (nUsages == 1) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis3());
        } else {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis4(nUsages));
        }
      }

      @Override public void visitClass(@Nonnull RefClass refClass) {
        if (refClass.isAnonymous()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis10());
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          String classOrInterface = HTMLJavaHTMLComposer.getClassOrInterface(refClass, true);
          //noinspection HardCodedStringLiteral
          buf.append("&nbsp;");

          int nDerived = getImplementationsCount(refClass);

          if (nDerived == 0) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis23(classOrInterface));
          } else if (nDerived == 1) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis24(classOrInterface));
          } else {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis25(classOrInterface, nDerived));
          }
        } else if (refClass.isUtilityClass()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis11());
        } else {
          int nInstantiationsCount = getInstantiationsCount(refClass);

          if (nInstantiationsCount == 0) {
            int nImplementations = getImplementationsCount(refClass);
            if (nImplementations != 0) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis19(nImplementations));
            } else {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis13());
            }
          } else if (nInstantiationsCount == 1) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis12());
          } else {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis20(nInstantiationsCount));
          }
        }
      }

      @Override public void visitMethod(@Nonnull RefMethod method) {
        RefClass refClass = method.getOwnerClass();
        if (method.isExternalOverride()) {
          String classOrInterface = HTMLJavaHTMLComposer.getClassOrInterface(refClass, false);
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis22(classOrInterface));
        } else if (method.isStatic() || method.isConstructor()) {
          int nRefs = method.getInReferences().size();
          if (method.isConstructor()) {
            if (nRefs == 0) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis26Constructor());
            } else if (method.isConstructor() && ((RefMethodImpl)method).isSuspiciousRecursive()) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis27Constructor());
            } else if (nRefs == 1) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis28Constructor());
            } else {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis29Constructor(nRefs));
            }
          } else {
            if (nRefs == 0) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis26Method());
            } else if (method.isConstructor() && ((RefMethodImpl)method).isSuspiciousRecursive()) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis27Method());
            } else if (nRefs == 1) {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis28Method());
            } else {
              buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis29Method(nRefs));
            }
          }
        } else if (((RefClassImpl)refClass).isSuspicious()) {
          if (method.isAbstract()) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis14());
          } else {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis15());
          }
        } else {
          int nOwnRefs = method.getInReferences().size();
          int nSuperRefs = getSuperRefsCount(method);
          int nDerivedRefs = getDerivedRefsCount(method);

          if (nOwnRefs == 0 && nSuperRefs == 0 && nDerivedRefs == 0) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis16());
          } else if (nDerivedRefs > 0 && nSuperRefs == 0 && nOwnRefs == 0) {
            String classOrInterface = HTMLJavaHTMLComposer.getClassOrInterface(refClass, false);
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis21(classOrInterface));
          } else if (((RefMethodImpl)method).isSuspiciousRecursive()) {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis17());
          } else {
            buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis18());
          }
        }
      }
    });
  }

  @Override
  protected void appendAdditionalListItemInfo(StringBuffer buf, RefElement refElement) {
    if (refElement instanceof RefImplicitConstructor implicitConstructor) {
      refElement = implicitConstructor.getOwnerClass();
    }

    //noinspection HardCodedStringLiteral
    buf.append("<br>");
    if (refElement instanceof RefClass) {
      RefClassImpl refClass = (RefClassImpl)refElement;
      if (refClass.isSuspicious()) {
        if (refClass.isUtilityClass()) {
          // Append nothing.
        } else if (refClass.isAnonymous()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis9Suspicious(getInstantiationsCount(refClass)));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis8Suspicious(getInstantiationsCount(refClass)));
        } else {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis7Suspicious(getInstantiationsCount(refClass)));
        }
      } else {
        if (refClass.isUtilityClass()) {
          // Append nothing.
        } else if (refClass.isAnonymous()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis9(getInstantiationsCount(refClass)));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis8(getInstantiationsCount(refClass)));
        } else {
          buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis7(getInstantiationsCount(refClass)));
        }
      }
    } else {
      int nUsageCount = refElement.getInReferences().size();
      if (refElement instanceof RefMethod method) {
        nUsageCount += getDerivedRefsCount(method);
      }
      if (((RefElementImpl)refElement).isSuspicious()) {
        buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis6Suspicious(nUsageCount));
      } else {
        buf.append(InspectionLocalize.inspectionDeadCodeProblemSynopsis6(nUsageCount));
      }
    }
  }

  private static int getDerivedRefsCount(RefMethod refMethod) {
    int count = 0;

    for (RefMethod refDerived : refMethod.getDerivedMethods()) {
      count += refDerived.getInReferences().size() + getDerivedRefsCount(refDerived);
    }

    return count;
  }

  private static int getSuperRefsCount(RefMethod refMethod) {
    int count = 0;

    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      count += refSuper.getInReferences().size() + getSuperRefsCount(refSuper);
    }

    return count;
  }

  private static int getInstantiationsCount(RefClass aClass) {
    if (!aClass.isAnonymous()) {
      int count = 0;

      for (RefMethod refConstructor : aClass.getConstructors()) {
        count += refConstructor.getInReferences().size();
      }

      for (RefClass subClass : aClass.getSubClasses()) {
        count += getInstantiationsCount(subClass);
        count -= subClass.getConstructors().size();
      }

      return count;
    }

    return 1;
  }

  private static int getImplementationsCount(RefClass refClass) {
    int count = 0;
    for (RefClass subClass : refClass.getSubClasses()) {
      if (!subClass.isInterface() && !subClass.isAbstract()) {
        count++;
      }
      count += getImplementationsCount(subClass);
    }

    return count;
  }

  private void appendClassInstantiations(StringBuffer buf, RefClass refClass) {
    if (!refClass.isInterface() && !refClass.isAbstract() && !refClass.isUtilityClass()) {
      boolean found = false;

      appendHeading(buf, InspectionLocalize.inspectionDeadCodeExportResultsInstantiatedFromHeading().get());

      startList(buf);
      for (RefMethod refMethod : refClass.getConstructors()) {
        for (RefElement refCaller : refMethod.getInReferences()) {
          appendListItem(buf, refCaller);
          found = true;
        }
      }

      if (!found) {
        startListItem(buf);
        buf.append(InspectionLocalize.inspectionDeadCodeExportResultsNoInstantiationsFound());
        doneListItem(buf);
      }

      doneList(buf);
    }
  }

  private void appendCallesList(RefElement element, StringBuffer buf, Set<RefElement> mentionedElements, boolean appendCallees){
    final Set<RefElement> possibleChildren = getPossibleChildren(new RefElementNode(element, myToolPresentation), element);
    if (!possibleChildren.isEmpty()) {
      if (appendCallees){
        appendHeading(buf, InspectionLocalize.inspectionExportResultsCallees().get());
      }
      @NonNls final String ul = "<ul>";
      buf.append(ul);
      for (RefElement refElement : possibleChildren) {
        if (!mentionedElements.contains(refElement)) {
          mentionedElements.add(refElement);
          @NonNls final String li = "<li>";
          buf.append(li);
          appendElementReference(buf, refElement, true);
          @NonNls final String closeLi = "</li>";
          buf.append(closeLi);
          appendCallesList(refElement, buf, mentionedElements, false);
        }
      }
      @NonNls final String closeUl = "</ul>";
      buf.append(closeUl);
    }
  }

  public static Set<RefElement> getPossibleChildren(final RefElementNode refElementNode, RefElement refElement) {
    final TreeNode[] pathToRoot = refElementNode.getPath();

    final HashSet<RefElement> newChildren = new HashSet<>();

    if (!refElement.isValid()) return newChildren;

    for (RefElement refCallee : refElement.getOutReferences()) {
      if (((RefElementImpl)refCallee).isSuspicious()) {
        if (notInPath(pathToRoot, refCallee)) newChildren.add(refCallee);
      }
    }

    if (refElement instanceof RefMethod refMethod) {
      if (!refMethod.isStatic() && !refMethod.isConstructor() && !refMethod.getOwnerClass().isAnonymous()) {
        for (RefMethod refDerived : refMethod.getDerivedMethods()) {
          if (((RefMethodImpl)refDerived).isSuspicious()) {
            if (notInPath(pathToRoot, refDerived)) newChildren.add(refDerived);
          }
        }
      }
    } else if (refElement instanceof RefClass refClass) {
      for (RefClass subClass : refClass.getSubClasses()) {
        if ((subClass.isInterface() || subClass.isAbstract()) && ((RefClassImpl)subClass).isSuspicious()) {
          if (notInPath(pathToRoot, subClass)) newChildren.add(subClass);
        }
      }

      if (refClass.getDefaultConstructor() instanceof RefImplicitConstructor) {
        Set<RefElement> fromConstructor = getPossibleChildren(refElementNode, refClass.getDefaultConstructor());
        newChildren.addAll(fromConstructor);
      }
    }

    return newChildren;
  }

  private static boolean notInPath(TreeNode[] pathToRoot, RefElement refChild) {
    for (TreeNode aPathToRoot : pathToRoot) {
      InspectionTreeNode node = (InspectionTreeNode)aPathToRoot;
      if (node instanceof RefElementNode refElementNode && refElementNode.getElement() == refChild) return false;
    }

    return true;
  }
}

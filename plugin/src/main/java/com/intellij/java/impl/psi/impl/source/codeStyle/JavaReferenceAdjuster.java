/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.source.codeStyle;

import com.intellij.java.impl.ig.psiutils.ImportUtils;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.impl.psi.codeStyle.ReferenceAdjuster;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.java.language.impl.psi.impl.source.SourceJavaCodeReference;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class JavaReferenceAdjuster implements ReferenceAdjuster {
  @Override
  public ASTNode process(@Nonnull ASTNode element, boolean addImports, boolean incompleteCode, boolean useFqInJavadoc, boolean useFqInCode) {
    IElementType elementType = element.getElementType();
    if ((elementType == JavaElementType.JAVA_CODE_REFERENCE || elementType == JavaElementType.REFERENCE_EXPRESSION) && !isAnnotated(element)) {
      IElementType parentType = element.getTreeParent().getElementType();
      if (elementType == JavaElementType.REFERENCE_EXPRESSION) {
        PsiReferenceExpression ref = (PsiReferenceExpression) element.getPsi();
        if (ImportUtils.isAlreadyStaticallyImported(ref)) {
          deQualifyImpl((PsiQualifiedReferenceElement) element);
          return element;
        }
      }
      if (elementType == JavaElementType.JAVA_CODE_REFERENCE || incompleteCode || parentType == JavaElementType.REFERENCE_EXPRESSION || parentType == JavaElementType.METHOD_REF_EXPRESSION) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) element.getPsi();

        PsiReferenceParameterList parameterList = ref.getParameterList();
        if (parameterList != null) {
          PsiTypeElement[] typeParameters = parameterList.getTypeParameterElements();
          for (PsiTypeElement typeParameter : typeParameters) {
            process(typeParameter.getNode(), addImports, incompleteCode, useFqInJavadoc, useFqInCode);
          }
        }

        boolean rightKind = true;
        if (elementType == JavaElementType.JAVA_CODE_REFERENCE) {
          PsiJavaCodeReferenceElementImpl impl = (PsiJavaCodeReferenceElementImpl) element;
          PsiJavaCodeReferenceElementImpl.Kind kind = impl.getKindEnum(impl.getContainingFile());
          rightKind = kind == PsiJavaCodeReferenceElementImpl.Kind.CLASS_NAME_KIND || kind == PsiJavaCodeReferenceElementImpl.Kind.CLASS_OR_PACKAGE_NAME_KIND;
        }

        if (rightKind) {
          // annotations may jump out of reference (see PsiJavaCodeReferenceImpl#setAnnotations()) so they should be processed first
          List<PsiAnnotation> annotations = PsiTreeUtil.getChildrenOfTypeAsList(ref, PsiAnnotation.class);
          for (PsiAnnotation annotation : annotations) {
            process(annotation.getNode(), addImports, incompleteCode, useFqInJavadoc, useFqInCode);
          }

          boolean isInsideDocComment = TreeUtil.findParent(element, JavaDocElementType.DOC_COMMENT) != null;
          boolean isShort = !ref.isQualified();
          if (isInsideDocComment ? !useFqInJavadoc : !useFqInCode) {
            if (isShort) {
              return element; // short name already, no need to change
            }
          }

          PsiElement refElement;
          if (!incompleteCode) {
            refElement = ref.resolve();
          } else {
            PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getManager().getProject()).getResolveHelper();
            final SourceJavaCodeReference reference = (SourceJavaCodeReference) element;
            refElement = helper.resolveReferencedClass(reference.getClassNameText(), ref);
          }

          if (refElement instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) refElement;
            if (isInsideDocComment ? useFqInJavadoc : useFqInCode) {
              String qName = psiClass.getQualifiedName();
              if (qName == null) {
                return element;
              }

              PsiFile file = ref.getContainingFile();
              if (file instanceof PsiJavaFile) {
                if (ImportHelper.isImplicitlyImported(qName, (PsiJavaFile) file)) {
                  if (isShort) {
                    return element;
                  }
                  return makeShortReference((CompositeElement) element, psiClass, addImports);
                }

                String thisPackageName = ((PsiJavaFile) file).getPackageName();
                if (ImportHelper.hasPackage(qName, thisPackageName)) {
                  if (!isShort) {
                    return makeShortReference((CompositeElement) element, psiClass, addImports);
                  }
                }
              }

              return replaceReferenceWithFQ(element, psiClass);
            } else {
              int oldLength = element.getTextLength();
              ASTNode treeElement = makeShortReference((CompositeElement) element, psiClass, addImports);
              if (treeElement.getTextLength() == oldLength && psiClass.getContainingClass() != null) {
                PsiElement qualifier = ref.getQualifier();
                if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement) qualifier).resolve() instanceof PsiClass) {
                  process(qualifier.getNode(), addImports, incompleteCode, useFqInJavadoc, useFqInCode);
                }
              }
              return treeElement;
            }
          }
        }
      }
    }

    for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      //noinspection AssignmentToForLoopParameter
      child = process(child, addImports, incompleteCode, useFqInJavadoc, useFqInCode);
    }

    return element;
  }

  @Override
  public ASTNode process(@Nonnull ASTNode element, boolean addImports, boolean incompleteCode, Project project) {
    final CodeStyleSettings settings = CodeStyle.getSettings(element.getPsi().getContainingFile());
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    return process(element, addImports, incompleteCode, javaSettings.useFqNamesInJavadocAlways(), javaSettings.USE_FQ_CLASS_NAMES);
  }

  private static boolean isAnnotated(ASTNode element) {
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) element.getPsi();

    PsiElement qualifier = ref.getQualifier();
    if (qualifier instanceof PsiJavaCodeReferenceElement) {
      if (((PsiJavaCodeReferenceElement) qualifier).resolve() instanceof PsiPackage) {
        return false;
      }
      if (PsiTreeUtil.getChildOfType(qualifier, PsiAnnotation.class) != null) {
        return true;
      }
    }

    PsiModifierList modifierList = PsiImplUtil.findNeighbourModifierList(ref);
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        if (AnnotationTargetUtil.findAnnotationTarget(annotation, PsiAnnotation.TargetType.TYPE_USE) != null) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void processRange(@Nonnull ASTNode element, int startOffset, int endOffset, boolean useFqInJavadoc, boolean useFqInCode) {
    List<ASTNode> array = new ArrayList<>();
    addReferencesInRange(array, element, startOffset, endOffset);
    for (ASTNode ref : array) {
      if (ref.getPsi().isValid()) {
        process(ref, true, true, useFqInJavadoc, useFqInCode);
      }
    }
  }

  @Override
  public void processRange(@Nonnull ASTNode element, int startOffset, int endOffset, Project project) {
    final CodeStyleSettings settings = CodeStyle.getSettings(element.getPsi().getContainingFile());
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    processRange(element, startOffset, endOffset, javaSettings.useFqNamesInJavadocAlways(), javaSettings.USE_FQ_CLASS_NAMES);
  }

  private static void addReferencesInRange(List<ASTNode> array, ASTNode parent, int startOffset, int endOffset) {
    if (parent.getElementType() == JavaElementType.JAVA_CODE_REFERENCE || parent.getElementType() == JavaElementType.REFERENCE_EXPRESSION) {
      array.add(parent);
      return;
    }

    if (parent.getPsi() instanceof PsiFile) {
      /*JspFile jspFile = JspPsiUtil.getJspFile(parent.getPsi());
			if(jspFile != null)
			{
				JspClass jspClass = (JspClass) jspFile.getJavaClass();
				if(jspClass != null)
				{
					addReferencesInRange(array, jspClass.getNode(), startOffset, endOffset);
				}
				return;
			} */
    }

    addReferencesInRangeForComposite(array, parent, startOffset, endOffset);
  }

  private static void addReferencesInRangeForComposite(List<ASTNode> array, ASTNode parent, int startOffset, int endOffset) {
    int offset = 0;
    for (ASTNode child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      int length = child.getTextLength();
      if (startOffset <= offset + length && offset <= endOffset) {
        IElementType type = child.getElementType();
        if (type == JavaElementType.JAVA_CODE_REFERENCE || type == JavaElementType.REFERENCE_EXPRESSION) {
          array.add(child);
        } else {
          addReferencesInRangeForComposite(array, child, startOffset - offset, endOffset - offset);
        }
      }
      offset += length;
    }
  }

  @Nonnull
  private static ASTNode makeShortReference(@Nonnull CompositeElement reference, @Nonnull PsiClass refClass, boolean addImports) {
    @Nonnull final PsiJavaCodeReferenceElement psiReference = (PsiJavaCodeReferenceElement) reference.getPsi();
    final PsiQualifiedReferenceElement reference1 = getClassReferenceToShorten(refClass, addImports, psiReference);
    if (reference1 != null) {
      replaceReferenceWithShort(reference1);
    }
    return reference;
  }

  @Nullable
  public static PsiQualifiedReferenceElement getClassReferenceToShorten(@Nonnull final PsiClass refClass,
                                                                        final boolean addImports,
                                                                        @Nonnull final PsiQualifiedReferenceElement reference) {
    PsiClass parentClass = refClass.getContainingClass();
    if (parentClass != null) {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(parentClass.getProject());
      final PsiResolveHelper resolveHelper = facade.getResolveHelper();
      if (resolveHelper.isAccessible(refClass, reference, null) && isSafeToShortenReference(reference.getReferenceName(), reference, refClass)) {
        return reference;
      }

      if (!JavaCodeStyleSettings.getInstance(reference.getContainingFile()).isInsertInnerClassImportsFor(refClass.getName())) {
        final PsiElement qualifier = reference.getQualifier();
        if (qualifier instanceof PsiQualifiedReferenceElement) {
          return getClassReferenceToShorten(parentClass, addImports, (PsiQualifiedReferenceElement) qualifier);
        }
        return null;
      }
    }

    if (addImports && !((PsiImportHolder) reference.getContainingFile()).importClass(refClass)) {
      return null;
    }
    if (!isSafeToShortenReference(reference, refClass)) {
      return null;
    }
    return reference;
  }

  private static boolean isSafeToShortenReference(@Nonnull PsiElement psiReference, @Nonnull PsiClass refClass) {
    return isSafeToShortenReference(refClass.getName(), psiReference, refClass);
  }

  private static boolean isSafeToShortenReference(final String referenceText, final PsiElement psiReference, final PsiClass refClass) {
    final PsiManager manager = refClass.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiResolveHelper helper = facade.getResolveHelper();
    if (manager.areElementsEquivalent(refClass, helper.resolveReferencedClass(referenceText, psiReference))) {
      if (psiReference instanceof PsiJavaCodeReferenceElement) {
        PsiElement parent = psiReference.getParent();
        if (parent instanceof PsiNewExpression || parent.getParent() instanceof PsiNewExpression) {
          return true;
        }

        if (parent instanceof PsiTypeElement &&
            parent.getParent() instanceof PsiInstanceOfExpression) {
          final PsiClass containingClass = refClass.getContainingClass();
          if (containingClass != null && containingClass.hasTypeParameters()) {
            return false;
          }
        }
      }
      return helper.resolveReferencedVariable(referenceText, psiReference) == null;
    }
    return false;
  }

  @Nonnull
  private static ASTNode replaceReferenceWithShort(PsiQualifiedReferenceElement reference) {
    ASTNode node = reference.getNode();
    assert node != null;
    deQualifyImpl(reference);
    return node;
  }

  private static void deQualifyImpl(PsiQualifiedReferenceElement reference) {
    PsiElement qualifier = reference.getQualifier();
    if (qualifier != null) {
      ASTNode qNode = qualifier.getNode();
      if (qNode == null) {
        return;
      }
      ASTNode firstChildNode = qNode.getFirstChildNode();
      boolean markToReformatBefore = firstChildNode instanceof TreeElement && CodeEditUtil.isMarkedToReformatBefore((TreeElement) firstChildNode);
      new CommentTracker().deleteAndRestoreComments(qualifier);
      if (markToReformatBefore) {
        firstChildNode = reference.getNode().getFirstChildNode();
        if (firstChildNode != null) {
          CodeEditUtil.markToReformatBefore(firstChildNode, true);
        }
      }
    }
  }

  private static ASTNode replaceReferenceWithFQ(ASTNode reference, PsiClass refClass) {
    ((SourceJavaCodeReference) reference).fullyQualify(refClass);
    return reference;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}

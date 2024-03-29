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
package com.intellij.java.impl.psi.impl.source.tree;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.psi.impl.source.JavaDummyElement;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TreeGenerator;
import consulo.language.impl.ast.*;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.impl.psi.DummyHolderFactory;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.CharTable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
@ExtensionImpl
public class JavaTreeGenerator implements TreeGenerator {
  private static final Logger LOG = Logger.getInstance(JavaTreeGenerator.class);

  private static final JavaParserUtil.ParserWrapper MOD_LIST =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parseModifierList(builder);

  @Override
  @Nullable
  public TreeElement generateTreeFor(@Nonnull PsiElement original, @Nonnull final CharTable table, @Nonnull final PsiManager manager) {
    if (original instanceof PsiKeyword || original instanceof PsiIdentifier) {
      final String text = original.getText();
      return createLeafFromText(text, table, manager, original, ((PsiJavaToken)original).getTokenType());
    }

    if (original instanceof PsiModifierList) {
      final String text = original.getText();
      assert text != null : "Text is null for " + original + "; " + original.getClass();
      final LanguageLevel level = PsiUtil.getLanguageLevel(original);
      final DummyHolder holder = DummyHolderFactory.createHolder(original.getManager(), new JavaDummyElement(text, MOD_LIST, level), null);
      final TreeElement modifierListElement = holder.getTreeElement().getFirstChildNode();
      if (modifierListElement == null) {
        throw new AssertionError("No modifier list for \"" + text + '\"');
      }
      return markGeneratedIfNeeded(original, modifierListElement);
    }

    if (original instanceof PsiReferenceExpression) {
      TreeElement element = createReferenceExpression(original.getProject(), original.getText(), original);
      PsiElement refElement = ((PsiJavaCodeReferenceElement)original).resolve();
      if (refElement instanceof PsiClass) {
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
      }
      return element;
    }

    if (original instanceof PsiJavaCodeReferenceElement) {
      PsiElement refElement = ((PsiJavaCodeReferenceElement)original).resolve();
      final boolean generated = refElement != null && CodeEditUtil.isNodeGenerated(refElement.getNode());
      if (refElement instanceof PsiClass) {
        if (refElement instanceof PsiAnonymousClass) {
          PsiJavaCodeReferenceElement ref = ((PsiAnonymousClass)refElement).getBaseClassReference();
          original = ref;
          refElement = ref.resolve();
        }

        boolean isFQ = false;
        if (original instanceof PsiJavaCodeReferenceElementImpl) {
          PsiJavaCodeReferenceElementImpl.Kind kind = ((PsiJavaCodeReferenceElementImpl)original).getKindEnum(original.getContainingFile());
          switch (kind) {
            case CLASS_OR_PACKAGE_NAME_KIND:
            case CLASS_NAME_KIND:
            case CLASS_IN_QUALIFIED_NEW_KIND:
              isFQ = false;
              break;

            case CLASS_FQ_NAME_KIND:
            case CLASS_FQ_OR_PACKAGE_NAME_KIND:
              isFQ = true;
              break;

            default:
              LOG.assertTrue(false);
          }
        }

        final String text = isFQ ? ((PsiClass)refElement).getQualifiedName() : original.getText();
        final TreeElement element = createReference(original.getProject(), text, generated);
        element.putCopyableUserData(REFERENCED_CLASS_KEY, (PsiClass)refElement);
        return element;
      }
      return createReference(original.getProject(), original.getText(), generated);
    }

    if (original instanceof PsiCompiledElement) {
      PsiElement sourceVersion = original.getNavigationElement();
      if (sourceVersion != original) {
        return ChangeUtil.generateTreeElement(sourceVersion, table, manager);
      }
      PsiElement mirror = ((PsiCompiledElement)original).getMirror();
      return ChangeUtil.generateTreeElement(mirror, table, manager);
    }

    if (original instanceof PsiTypeElement) {
      PsiTypeElement typeElement = (PsiTypeElement)original;
      PsiType type = typeElement.getType();

      if (type instanceof PsiIntersectionType) {
        type = ((PsiIntersectionType)type).getRepresentative();
      }
      else if (type instanceof PsiMethodReferenceType || type instanceof PsiLambdaExpressionType) {
        type = PsiType.getJavaLangObject(manager, GlobalSearchScope.projectScope(manager.getProject()));
      }

      String text = type.getCanonicalText(true);
      PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(original.getProject()).getParserFacade();
      PsiTypeElement element = parserFacade.createTypeElementFromText(text, original);

      TreeElement result = (TreeElement)element.getNode();
      markGeneratedIfNeeded(original, result);
      encodeInfoInTypeElement(result, type);
      return result;
    }

    return null;
  }

  private static LeafElement createLeafFromText(String text, CharTable table, PsiManager manager, PsiElement original, IElementType type) {
    return Factory.createSingleLeafElement(type, text, 0, text.length(), table, manager, CodeEditUtil.isNodeGenerated(original.getNode()));
  }

  private static TreeElement markGeneratedIfNeeded(@Nonnull PsiElement original, @Nonnull TreeElement copy) {
    if (CodeEditUtil.isNodeGenerated(original.getNode())) {
      CodeEditUtil.markGenerated(copy);
    }
    return copy;
  }

  private static TreeElement createReference(final Project project, final String text, boolean mark) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
    final TreeElement element = (TreeElement)parserFacade.createReferenceFromText(text, null).getNode();
    if (mark) {
      CodeEditUtil.markGenerated(element);
    }
    return element;
  }

  private static TreeElement createReferenceExpression(final Project project, final String text, final PsiElement context) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
    final PsiExpression expression = parserFacade.createExpressionFromText(text, context);
    return (TreeElement)expression.getNode();
  }

  private static void encodeInfoInTypeElement(ASTNode typeElement, PsiType type) {
    if (type instanceof PsiPrimitiveType) {
      return;
    }
    LOG.assertTrue(typeElement.getElementType() == JavaElementType.TYPE);
    if (type instanceof PsiArrayType) {
      final ASTNode firstChild = typeElement.getFirstChildNode();
      LOG.assertTrue(firstChild.getElementType() == JavaElementType.TYPE);
      encodeInfoInTypeElement(firstChild, ((PsiArrayType)type).getComponentType());
    }
    else if (type instanceof PsiWildcardType) {
      final PsiType bound = ((PsiWildcardType)type).getBound();
      if (bound == null) {
        return;
      }
      final ASTNode lastChild = typeElement.getLastChildNode();
      if (lastChild.getElementType() != JavaElementType.TYPE) {
        return;
      }
      encodeInfoInTypeElement(lastChild, bound);
    }
    else if (type instanceof PsiCapturedWildcardType) {
      final PsiType bound = ((PsiCapturedWildcardType)type).getWildcard().getBound();
      if (bound == null) {
        return;
      }
      final ASTNode lastChild = typeElement.getLastChildNode();
      if (lastChild.getElementType() != JavaElementType.TYPE) {
        return;
      }
      encodeInfoInTypeElement(lastChild, bound);
    }
    else if (type instanceof PsiIntersectionType) {
      encodeInfoInTypeElement(typeElement, ((PsiIntersectionType)type).getRepresentative());
    }
    else if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass referencedClass = resolveResult.getElement();
      if (referencedClass == null) {
        return;
      }
      if (referencedClass instanceof PsiAnonymousClass) {
        encodeInfoInTypeElement(typeElement, ((PsiAnonymousClass)referencedClass).getBaseClassType());
      }
      else {
        final ASTNode reference = typeElement.findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
        // can be not the case for "? name"
        if (reference instanceof CompositeElement) {
          encodeClassTypeInfoInReference((CompositeElement)reference, resolveResult.getElement(), resolveResult.getSubstitutor());
        }
      }
    }
  }

  private static void encodeClassTypeInfoInReference(@Nonnull CompositeElement reference,
                                                     PsiClass referencedClass,
                                                     PsiSubstitutor substitutor) {
    reference.putCopyableUserData(REFERENCED_CLASS_KEY, referencedClass);

    final PsiTypeParameter[] typeParameters = referencedClass.getTypeParameters();
    if (typeParameters.length == 0) {
      return;
    }

    final ASTNode referenceParameterList = reference.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    int index = 0;
    for (ASTNode child = referenceParameterList.getFirstChildNode(); child != null && index < typeParameters.length;
         child = child.getTreeNext()) {
      if (child.getElementType() == JavaElementType.TYPE) {
        final PsiType substitutedType = substitutor.substitute(typeParameters[index]);
        if (substitutedType != null) {
          encodeInfoInTypeElement(child, substitutedType);
        }
        index++;
      }
    }

    final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier != null) {
      if (referencedClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass outerClass = referencedClass.getContainingClass();
      if (outerClass != null) {
        encodeClassTypeInfoInReference((CompositeElement)qualifier, outerClass, substitutor);
      }
    }
  }

  static final Key<PsiClass> REFERENCED_CLASS_KEY = Key.create("REFERENCED_CLASS_KEY");
  static final Key<PsiMember> REFERENCED_MEMBER_KEY = Key.create("REFERENCED_MEMBER_KEY");
}

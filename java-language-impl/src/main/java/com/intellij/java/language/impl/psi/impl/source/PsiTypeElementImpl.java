/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.language.impl.psi.PsiDiamondTypeImpl;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.attachment.AttachmentFactory;
import consulo.logging.attachment.RuntimeExceptionWithAttachments;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.collection.SmartList;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  @SuppressWarnings("UnusedDeclaration")
  public PsiTypeElementImpl() {
    this(JavaElementType.TYPE);
  }

  PsiTypeElementImpl(@Nonnull IElementType type) {
    super(type);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitTypeElement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  @Nonnull
  public PsiType getType() {
    return LanguageCachedValueUtil.getCachedValue(this, () -> CachedValueProvider.Result.create(calculateType(), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nonnull
  private PsiType calculateType() {
    /*PsiType inferredType = PsiAugmentProvider.getInferredType(this);
    if(inferredType != null)
		{
			return inferredType;
		} */

    PsiType type = null;
    List<PsiAnnotation> annotations = new SmartList<>();

    PsiElement parent = getParent();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
        continue;
      }

      if (child instanceof PsiAnnotation) {
        annotations.add((PsiAnnotation) child);
      } else if (child instanceof PsiTypeElement) {
        assert type == null : this;
        if (child instanceof PsiDiamondTypeElementImpl) {
          type = new PsiDiamondTypeImpl(getManager(), this);
          break;
        } else {
          type = ((PsiTypeElement) child).getType();
        }
      } else if (PsiUtil.isJavaToken(child, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
        assert type == null : this;
        String text = child.getText();
        type = annotations.isEmpty() ? PsiJavaParserFacadeImpl.getPrimitiveType(text) : new PsiPrimitiveType(text, createProvider(annotations));
      } else if (PsiUtil.isJavaToken(child, JavaTokenType.VAR_KEYWORD)) {
        assert type == null : this;
        type = inferVarType(parent);
      } else if (child instanceof PsiJavaCodeReferenceElement) {
        assert type == null : this;
        type = new PsiClassReferenceType(getReferenceComputable((PsiJavaCodeReferenceElement) child), null, createProvider(annotations));
      } else if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        assert type != null : this;
        type = new PsiArrayType(type, createProvider(annotations));
      } else if (PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        assert type != null : this;
        type = new PsiEllipsisType(type, createProvider(annotations));
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST) || child instanceof ASTNode && ((ASTNode) child).getElementType() == JavaElementType.DUMMY_ELEMENT && "any".equals(child.getText())) {
        assert type == null : this;
        PsiElement boundKind = PsiTreeUtil.skipSiblingsForward(child, PsiComment.class, PsiWhiteSpace.class);
        PsiElement boundType = PsiTreeUtil.skipSiblingsForward(boundKind, PsiComment.class, PsiWhiteSpace.class);
        if (PsiUtil.isJavaToken(boundKind, JavaTokenType.EXTENDS_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createExtends(getManager(), ((PsiTypeElement) boundType).getType());
        } else if (PsiUtil.isJavaToken(boundKind, JavaTokenType.SUPER_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createSuper(getManager(), ((PsiTypeElement) boundType).getType());
        } else {
          type = PsiWildcardType.createUnbounded(getManager());
        }
        type = type.annotate(createProvider(annotations));
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.AND)) {
        List<PsiType> types = collectTypes();
        assert !types.isEmpty() : this;
        type = PsiIntersectionType.createIntersection(false, types.toArray(PsiType.createArray(types.size())));
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.OR)) {
        List<PsiType> types = collectTypes();
        assert !types.isEmpty() : this;
        type = PsiDisjunctionType.createDisjunction(types, getManager());
        break;
      }
    }

    if (type == null) {
      return PsiType.NULL;
    }

    if (parent instanceof PsiModifierListOwner) {
      type = JavaSharedImplUtil.applyAnnotations(type, ((PsiModifierListOwner) parent).getModifierList());
    }

    return type;
  }

  private PsiType inferVarType(PsiElement parent) {
    if (parent instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter) parent).getDeclarationScope();
      if (declarationScope instanceof PsiForeachStatement) {
        PsiExpression iteratedValue = ((PsiForeachStatement) declarationScope).getIteratedValue();
        if (iteratedValue != null) {
          return JavaGenericsUtil.getCollectionItemType(iteratedValue);
        }
        return null;
      }

      if (declarationScope instanceof PsiLambdaExpression) {
        return ((PsiParameter) parent).getType();
      }
    } else {
      for (PsiElement e = this; e != null; e = e.getNextSibling()) {
        if (e instanceof PsiExpression) {
          if (!(e instanceof PsiArrayInitializerExpression)) {
            PsiExpression expression = (PsiExpression) e;
            RecursionGuard.StackStamp stamp = RecursionManager.markStack();
            PsiType type = RecursionManager.doPreventingRecursion(expression, true, () -> expression.getType());
            if (stamp.mayCacheNow()) {
              return type == null ? null : JavaVarTypeUtil.getUpwardProjection(type);
            }
            return null;
          }
          return null;
        }
      }
    }
    return null;
  }

  private static boolean isSelfReferenced(@Nonnull PsiExpression initializer, PsiElement parent) {
    class SelfReferenceVisitor extends JavaRecursiveElementVisitor {
      private boolean referenced;

      @Override
      public void visitElement(PsiElement element) {
        if (referenced) {
          return;
        }
        super.visitElement(element);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getParent() instanceof PsiMethodCallExpression) {
          return;
        }
        if (expression.resolve() == parent) {
          referenced = true;
        }
      }
    }

    SelfReferenceVisitor visitor = new SelfReferenceVisitor();
    initializer.accept(visitor);
    return visitor.referenced;
  }

  @Override
  public boolean isInferredType() {
    PsiElement firstChild = getFirstChild();
    return PsiUtil.isJavaToken(firstChild, JavaTokenType.VAR_KEYWORD) || PsiAugmentProvider.isInferredType(this);
  }

  @Nonnull
  private static ClassReferencePointer getReferenceComputable(@Nonnull PsiJavaCodeReferenceElement ref) {
    PsiTypeElement rootType = getRootTypeElement(ref);
    if (rootType != null) {
      PsiElement parent = rootType.getParent();
      if (parent instanceof PsiMethod || parent instanceof PsiVariable) {
        int index = allReferencesInside(rootType).indexOf(ref::equals);
        if (index < 0) {
          throw new AssertionError(rootType.getClass());
        }
        return computeFromTypeOwner(parent, index, new WeakReference<>(ref));
      }
    }
    return ClassReferencePointer.constant(ref);
  }

  @Nullable
  private static PsiTypeElement getRootTypeElement(@Nonnull PsiJavaCodeReferenceElement ref) {
    PsiElement root = SyntaxTraverser.psiApi()
        .parents(ref.getParent())
        .takeWhile(it -> it instanceof PsiTypeElement || it instanceof PsiReferenceParameterList || it instanceof PsiJavaCodeReferenceElement)
        .last();
    return ObjectUtil.tryCast(root, PsiTypeElement.class);
  }

  @Nonnull
  private static ClassReferencePointer computeFromTypeOwner(PsiElement parent, int index,
                                                            @Nonnull WeakReference<PsiJavaCodeReferenceElement> ref) {
    return new ClassReferencePointer() {
      volatile WeakReference<PsiJavaCodeReferenceElement> myCache = ref;

      @Override
      public
      @Nullable
      PsiJavaCodeReferenceElement retrieveReference() {
        PsiJavaCodeReferenceElement result = myCache.get();
        if (result == null) {
          PsiType type = calcTypeByParent();
          if (type instanceof PsiClassReferenceType) {
            result = findReferenceByIndex((PsiClassReferenceType) type);
          }
          myCache = new WeakReference<>(result);
        }
        return result;
      }

      @Nullable
      private PsiJavaCodeReferenceElement findReferenceByIndex(PsiClassReferenceType type) {
        PsiTypeElement root = getRootTypeElement(type.getReference());
        return root == null ? null : allReferencesInside(root).get(index);
      }

      @Nullable
      private PsiType calcTypeByParent() {
        if (!parent.isValid()) {
          return null;
        }

        PsiType type = parent instanceof PsiMethod ? ((PsiMethod) parent).getReturnType() : ((PsiVariable) parent).getType();
        if (type instanceof PsiArrayType) { //also, for c-style array, e.g. String args[]
          return type.getDeepComponentType();
        }
        return type;
      }

      @Override
      public
      @Nonnull
      PsiJavaCodeReferenceElement retrieveNonNullReference() {
        PsiJavaCodeReferenceElement result = retrieveReference();
        if (result == null) {
          PsiType type = calcTypeByParent();
          if (!(type instanceof PsiClassReferenceType)) {
            PsiUtilCore.ensureValid(parent);
            throw new IllegalStateException("No reference type for " + parent.getClass() + "; type: " + (type != null ? type.getClass() : "null"));
          }
          result = findReferenceByIndex((PsiClassReferenceType) type);
          if (result == null) {
            PsiUtilCore.ensureValid(parent);
            throw new RuntimeExceptionWithAttachments("Can't retrieve reference by index " + index + " for " + parent.getClass() + "; type: " + type.getClass(),
                AttachmentFactory.get().create("memberType.txt", type.getCanonicalText()));
          }
        }
        return result;
      }

      @Override
      public String toString() {
        String msg = "Type element reference of " + parent.getClass() + " #" + parent.getClass().getSimpleName() + ", index=" + index;
        return parent.isValid() ? msg + " #" + parent.getLanguage() : msg + ", invalid";
      }
    };
  }

  @Nonnull
  private static JBIterable<PsiJavaCodeReferenceElement> allReferencesInside(@Nonnull PsiTypeElement rootType) {
    return SyntaxTraverser.psiTraverser(rootType).filter(PsiJavaCodeReferenceElement.class);
  }

  @Nonnull
  private static TypeAnnotationProvider createProvider(@Nonnull List<PsiAnnotation> annotations) {
    return TypeAnnotationProvider.Static.create(ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true));
  }

  @Nonnull
  private List<PsiType> collectTypes() {
    List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiTypeElement.class);
    return ContainerUtil.map(typeElements, typeElement -> typeElement.getType());
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) {
      return null;
    }
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
    }
    return getReferenceElement();
  }

  @Nullable
  private PsiJavaCodeReferenceElement getReferenceElement() {
    ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
    if (ref == null) {
      return null;
    }
    return (PsiJavaCodeReferenceElement) SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(this, PsiAnnotation.class);
    return annotations != null ? annotations : PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getType().getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @Nonnull
  public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException();//todo
  }

  @Override
  public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException {
    // neighbouring type annotations are logical part of this type element and should be dropped
    //if replacement is `var`, annotations should be left as they are not inferred from the right side of the assignment
    if (!(newElement instanceof PsiTypeElement) || !((PsiTypeElement) newElement).isInferredType()) {
      PsiImplUtil.markTypeAnnotations(this);
    }
    PsiElement result = super.replace(newElement);
    if (result instanceof PsiTypeElement) {
      PsiImplUtil.deleteTypeAnnotations((PsiTypeElement) result);
    }
    return result;
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.psi;

import com.intellij.java.impl.psi.impl.source.codeStyle.JavaReferenceAdjuster;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.language.impl.psi.CheckUtil;
import consulo.language.psi.*;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.resolve.BaseScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class AbstractQualifiedReference<T extends AbstractQualifiedReference<T>> extends ASTWrapperPsiElement
    implements PsiPolyVariantReference, PsiQualifiedReferenceElement {
  private static final ResolveCache.PolyVariantResolver<AbstractQualifiedReference> MY_RESOLVER = new ResolveCache.PolyVariantResolver<AbstractQualifiedReference>() {
    @Nonnull
    @Override
    public ResolveResult[] resolve(@Nonnull AbstractQualifiedReference expression, boolean incompleteCode) {
      return expression.resolveInner();
    }
  };

  protected AbstractQualifiedReference(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public final PsiReference getReference() {
    return this;
  }

  @Override
  public final PsiElement getElement() {
    return this;
  }

  protected abstract ResolveResult[] resolveInner();

  @Override
  @Nonnull
  public final ResolveResult[] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, MY_RESOLVER, true, false, file);
  }

  @Override
  @Nullable
  public final PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  protected boolean processVariantsInner(PsiScopeProcessor processor) {
    T qualifier = getQualifier();
    if (qualifier == null) {
      return processUnqualifiedVariants(processor);
    }

    PsiElement psiElement = qualifier.resolve();
    return psiElement == null || psiElement.processDeclarations(processor, ResolveState.initial(), null, this);
  }

  protected boolean processUnqualifiedVariants(PsiScopeProcessor processor) {
    return PsiScopesUtil.treeWalkUp(processor, this, null);
  }


  @Override
  @Nonnull
  public String getCanonicalText() {
    return getText();
  }

  @Override
  @SuppressWarnings({"unchecked"})
  @Nullable
  public T getQualifier() {
    return (T) findChildByClass(getClass());
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    PsiElement firstChildNode = ObjectUtil.assertNotNull(getFirstChild());
    PsiElement firstInIdentifier = getClass().isInstance(firstChildNode) ? ObjectUtil.assertNotNull(firstChildNode.getNextSibling()).getNextSibling() : firstChildNode;
    getNode().removeRange(firstInIdentifier.getNode(), null);
    PsiElement referenceName = ObjectUtil.assertNotNull(parseReference(newElementName).getReferenceNameElement());
    getNode().addChild(referenceName.getNode());
    return this;
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (isReferenceTo(element)) return this;

    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      String methodName = method.getName();
      if (isDirectlyVisible(method)) return replaceReference(methodName);

      AbstractQualifiedReference result = replaceReference(method.getContainingClass().getQualifiedName() + "." + methodName);
      AbstractQualifiedReference qualifier = result.getQualifier();
      assert qualifier != null;
      qualifier.shortenReferences();
      return result;
    }
    if (element instanceof PsiClass) {
      return replaceReference(((PsiClass) element).getQualifiedName()).shortenReferences();
    }
    if (element instanceof PsiPackage) {
      return replaceReference(((PsiPackage) element).getQualifiedName());
    }
    if (element instanceof PsiMetaOwner) {
      PsiMetaData metaData = ((PsiMetaOwner) element).getMetaData();
      if (metaData != null) {
        String name = metaData.getName(this);
        if (name != null) {
          return replaceReference(name);
        }
      }
    }
    return this;
  }

  private boolean isDirectlyVisible(final PsiMethod method) {
    AbstractQualifiedReferenceResolvingProcessor processor = new AbstractQualifiedReferenceResolvingProcessor() {
      @Override
      protected void process(PsiElement element) {
        if (getManager().areElementsEquivalent(element, method) && isAccessible(element)) {
          setFound();
        }
      }
    };
    processUnqualifiedVariants(processor);
    return processor.isFound();
  }

  protected final AbstractQualifiedReference replaceReference(String newText) {
    ASTNode newNode = parseReference(newText).getNode();
    getNode().getTreeParent().replaceChild(getNode(), newNode);
    return (AbstractQualifiedReference) newNode.getPsi();
  }

  @Nonnull
  protected abstract T parseReference(String newText);

  protected boolean isAccessible(PsiElement element) {
    if (element instanceof PsiMember) {
      PsiMember member = (PsiMember) element;
      return JavaResolveUtil.isAccessible(member, member.getContainingClass(), member.getModifierList(), this, null, null);
    }
    return true;
  }

  @Nonnull
  protected AbstractQualifiedReference shortenReferences() {
    PsiElement refElement = resolve();
    if (refElement instanceof PsiClass) {
      PsiQualifiedReference reference = JavaReferenceAdjuster.getClassReferenceToShorten((PsiClass) refElement, false, this);
      if (reference instanceof AbstractQualifiedReference) {
        ((AbstractQualifiedReference) reference).dequalify();
      }
    }
    return this;
  }

  private void dequalify() {
    AbstractQualifiedReference qualifier = getQualifier();
    if (qualifier != null) {
      getNode().removeChild(qualifier.getNode());
      PsiElement separator = getSeparator();
      if (separator != null) {
        ASTNode separatorNode = separator.getNode();
        if (separatorNode != null) {
          getNode().removeChild(separatorNode);
        }
      }
    }
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    PsiManager manager = getManager();
    for (ResolveResult result : multiResolve(false)) {
      if (manager.areElementsEquivalent(result.getElement(), element)) return true;
    }
    return false;
  }

  @Nullable
  protected abstract PsiElement getSeparator();

  @Nullable
  protected abstract PsiElement getReferenceNameElement();

  @Override
  public TextRange getRangeInElement() {
    PsiElement element = getSeparator();
    int length = getTextLength();
    return element == null ? TextRange.from(0, length) : new TextRange(element.getStartOffsetInParent() + element.getTextLength(), length);
  }

  @Override
  @Nullable
  @NonNls
  public String getReferenceName() {
    PsiElement element = getReferenceNameElement();
    return element == null ? null : element.getText().trim();
  }

  @Override
  public final boolean isSoft() {
    return false;
  }

  protected abstract static class AbstractQualifiedReferenceResolvingProcessor extends BaseScopeProcessor {
    private boolean myFound = false;
    private final Set<ResolveResult> myResults = new LinkedHashSet<ResolveResult>();

    @Override
    public boolean execute(@Nonnull PsiElement element, ResolveState state) {
      if (isFound()) return false;
      process(element);
      return true;
    }

    protected final void addResult(ResolveResult resolveResult) {
      myResults.add(resolveResult);
    }

    private boolean isFound() {
      return myFound;
    }

    @Override
    public void handleEvent(Event event, Object associated) {
      if ((event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT || event == Event.SET_DECLARATION_HOLDER) && !myResults.isEmpty()) {
        setFound();
      }
      super.handleEvent(event, associated);
    }

    protected final void setFound() {
      myFound = true;
    }

    protected abstract void process(PsiElement element);

    public Set<ResolveResult> getResults() {
      return myResults;
    }
  }

}

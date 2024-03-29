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
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassFilter;
import com.intellij.java.language.impl.psi.scope.processor.FilterScopeProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.CharTable;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class PsiDocMethodOrFieldRef extends CompositePsiElement implements PsiDocTagValue, Constants {
  public PsiDocMethodOrFieldRef() {
    super(DOC_METHOD_OR_FIELD_REF);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiReference getReference() {
    final PsiClass scope = getScope();
    final PsiElement element = getNameElement();
    if (scope == null || element == null) return new MyReference(null);

    PsiReference psiReference = getReferenceInScope(scope, element);
    if (psiReference != null) return psiReference;

    PsiClass classScope;
    PsiClass containingClass = scope.getContainingClass();
    while (containingClass != null) {
      classScope = containingClass;
      psiReference = getReferenceInScope(classScope, element);
      if (psiReference != null) return psiReference;
      containingClass = classScope.getContainingClass();
    }
    return new MyReference(null);
  }

  @Nullable
  private PsiReference getReferenceInScope(PsiClass scope, PsiElement element) {
    final String name = element.getText();
    final String[] signature = getSignature();

    if (signature == null) {
      final PsiVariable[] vars = getAllVariables(scope, this);
      for (PsiVariable var : vars) {
        if (!var.getName().equals(name)) continue;
        return new MyReference(var);
      }
    }

    final MethodSignature methodSignature;
    if (signature != null) {
      final List<PsiType> types = ContainerUtil.newArrayListWithCapacity(signature.length);
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
      for (String s : signature) {
        try {
          types.add(elementFactory.createTypeFromText(s, element));
        }
        catch (IncorrectOperationException e) {
          types.add(PsiType.NULL);
        }
      }
      methodSignature = MethodSignatureUtil.createMethodSignature(name, types.toArray(new PsiType[types.size()]),
                                                                  PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY,
                                                                  name.equals(scope.getName()));
    }
    else {
      methodSignature = null;
    }

    final PsiMethod[] methods = getAllMethods(scope, this);
    for (PsiMethod method : methods) {
      if (!method.getName().equals(name) ||
          (methodSignature != null && !MethodSignatureUtil.areSignaturesErasureEqual(methodSignature, method.getSignature(PsiSubstitutor.EMPTY)))) continue;
      return new MyReference(method) {
        @Override
        @Nonnull
        public PsiElement[] getVariants() {
          final List<PsiMethod> lst = new ArrayList<PsiMethod>();
          for (PsiMethod method : methods) {
            if (name.equals(method.getName())) {
              lst.add(method);
            }
          }
          return lst.toArray(new PsiMethod[lst.size()]);
        }
      };
    }

    return null;
  }

  public static PsiVariable[] getAllVariables(PsiElement scope, PsiElement place) {
    final SmartList<PsiVariable> result = new SmartList<PsiVariable>();
    scope.processDeclarations(new FilterScopeProcessor<PsiVariable>(ElementClassFilter.VARIABLE, result), ResolveState.initial(), null, place);
    return result.toArray(new PsiVariable[result.size()]);
  }

  public static PsiMethod[] getAllMethods(PsiElement scope, PsiElement place) {
    final SmartList<PsiMethod> result = new SmartList<PsiMethod>();
    scope.processDeclarations(new FilterScopeProcessor<PsiMethod>(ElementClassFilter.METHOD, result), ResolveState.initial(), null, place);
    return result.toArray(new PsiMethod[result.size()]);
  }

  @Override
  public int getTextOffset() {
    final PsiElement element = getNameElement();
    return element != null ? element.getTextRange().getStartOffset() : getTextRange().getEndOffset();
  }

  @Nullable
  public PsiElement getNameElement() {
    final ASTNode sharp = findChildByType(JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN);
    return sharp != null ? SourceTreeToPsiMap.treeToPsiNotNull(sharp).getNextSibling() : null;
  }

  @Nullable
  public String[] getSignature() {
    PsiElement element = getNameElement();
    if (element == null) return null;

    element = element.getNextSibling();
    while (element != null && !(element instanceof PsiDocTagValue)) {
      element = element.getNextSibling();
    }
    if (element == null) return null;

    List<String> types = new ArrayList<String>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNode().getElementType() == DOC_TYPE_HOLDER) {
        final String[] typeStrings = child.getText().split("[, ]");  //avoid param types list parsing hmm method(paramType1, paramType2, ...) -> typeElement1, identifier2, ...
        if (typeStrings != null) {
          for (String type : typeStrings) {
            if (type.length() > 0) {
              types.add(type);
            }
          }
        }
      }
    }

    return ArrayUtil.toStringArray(types);
  }

  @Nullable
  private PsiClass getScope(){
    if (getFirstChildNode().getElementType() == ElementType.DOC_REFERENCE_HOLDER) {
      final PsiElement firstChildPsi = SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode().getFirstChildNode());
      if (firstChildPsi instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)firstChildPsi;
        final PsiElement referencedElement = referenceElement.resolve();
        if (referencedElement instanceof PsiClass) return (PsiClass)referencedElement;
        return null;
      }
      else if (firstChildPsi instanceof PsiKeyword) {
        final PsiKeyword keyword = (PsiKeyword)firstChildPsi;

        if (keyword.getTokenType().equals(JavaTokenType.THIS_KEYWORD)) {
          return JavaResolveUtil.getContextClass(this);
        } else if (keyword.getTokenType().equals(JavaTokenType.SUPER_KEYWORD)) {
          final PsiClass contextClass = JavaResolveUtil.getContextClass(this);
          if (contextClass != null) return contextClass.getSuperClass();
          return null;
        }
      }
    }
    return JavaResolveUtil.getContextClass(this);
  }

  public class MyReference implements PsiJavaReference {
    private final PsiElement myReferredElement;

    public MyReference(PsiElement referredElement) {
      myReferredElement = referredElement;
    }

    @Override
    public PsiElement resolve() {
      return myReferredElement;
    }

    @Override
    public void processVariants(PsiScopeProcessor processor) {
      for (final PsiElement element : getVariants()) {
        if (!processor.execute(element, ResolveState.initial())) {
          return;
        }
      }
    }

    @Override
    @Nonnull
    public JavaResolveResult advancedResolve(boolean incompleteCode) {
      return myReferredElement == null ? JavaResolveResult.EMPTY
                                  : new CandidateInfo(myReferredElement, PsiSubstitutor.EMPTY);
    }

    @Override
    @Nonnull
    public JavaResolveResult[] multiResolve(boolean incompleteCode) {
      return myReferredElement == null ? JavaResolveResult.EMPTY_ARRAY
                                  : new JavaResolveResult[]{new CandidateInfo(myReferredElement, PsiSubstitutor.EMPTY)};
    }

    @Override
    @Nonnull
    public PsiElement[] getVariants(){
      final List<PsiModifierListOwner> vars = new ArrayList<PsiModifierListOwner>();
      PsiClass scope = getScope();
      while (scope != null) {
        ContainerUtil.addAll(vars, getAllMethods(scope, PsiDocMethodOrFieldRef.this));
        ContainerUtil.addAll(vars, getAllVariables(scope, PsiDocMethodOrFieldRef.this));
        scope = scope.getContainingClass();
      }
      return vars.toArray(new PsiModifierListOwner[vars.size()]);
    }

    @Override
    public boolean isSoft(){
      return false;
    }

    @Override
    @Nonnull
    public String getCanonicalText() {
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      return nameElement.getText();
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      final ASTNode treeElement = SourceTreeToPsiMap.psiToTreeNotNull(nameElement);
      final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
      final LeafElement newToken = Factory.createSingleLeafElement(JavaDocTokenType.DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, getManager());
      ((CompositeElement)treeElement.getTreeParent()).replaceChildInternal(SourceTreeToPsiMap.psiToTreeNotNull(nameElement), newToken);
      return SourceTreeToPsiMap.treeToPsiNotNull(newToken);
    }

    @Override
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
      if (isReferenceTo(element)) return PsiDocMethodOrFieldRef.this;
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      final String name = nameElement.getText();
      final String newName;

      final PsiMethod method;
      final PsiField field;
      final boolean hasSignature;
      final PsiClass containingClass;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        hasSignature = getSignature() != null;
        containingClass = method.getContainingClass();
        newName = method.getName();
      } else if (element instanceof PsiField) {
        field = (PsiField) element;
        hasSignature = false;
        containingClass = field.getContainingClass();
        method = null;
        newName = field.getName();
      } else {
        throw new IncorrectOperationException();
      }

      final PsiElement child = getFirstChild();
      if (containingClass != null && child != null && child.getNode().getElementType() == ElementType.DOC_REFERENCE_HOLDER) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) child.getFirstChild();
        assert referenceElement != null;
        referenceElement.bindToElement(containingClass);
      }
      else {
        if (containingClass != null && !PsiTreeUtil.isAncestor(containingClass, PsiDocMethodOrFieldRef.this, true)) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(containingClass.getProject()).getElementFactory();
          final PsiReferenceExpression ref = elementFactory.createReferenceExpression(containingClass);
          addAfter(ref, null);
        }
      }

      if (hasSignature || !name.equals(newName)) {
        String text = getText();

        @NonNls StringBuffer newText = new StringBuffer();
        newText.append("/** @see ");
        if (name.equals(newName)) { // hasSignature is true here, so we can search for '('
          newText.append(text.substring(0, text.indexOf('(')));
        }
        else {
          final int sharpIndex = text.indexOf('#');
          if (sharpIndex >= 0) {
            newText.append(text.substring(0, sharpIndex + 1));
          }
          newText.append(newName);
        }
        if (hasSignature) {
          newText.append('(');
          PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (i > 0) newText.append(",");
            newText.append(parameter.getType().getCanonicalText());
          }
          newText.append(')');
        }
        newText.append("*/");

        return bindToText(containingClass, newText);
      }

      return PsiDocMethodOrFieldRef.this;
    }

    public PsiElement bindToText(PsiClass containingClass, StringBuffer newText) {
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(containingClass.getProject()).getElementFactory();
      PsiComment comment = elementFactory.createCommentFromText(newText.toString(), null);
      PsiElement tag = PsiTreeUtil.getChildOfType(comment, PsiDocTag.class);
      PsiElement ref = PsiTreeUtil.getChildOfType(tag, PsiDocMethodOrFieldRef.class);
      assert ref != null : newText;
      return replace(ref);
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    public TextRange getRangeInElement() {
      final ASTNode sharp = findChildByType(JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN);
      if (sharp == null) return new TextRange(0, getTextLength());
      final PsiElement nextSibling = SourceTreeToPsiMap.treeToPsiNotNull(sharp).getNextSibling();
      if (nextSibling != null) {
        final int startOffset = nextSibling.getTextRange().getStartOffset() - getTextRange().getStartOffset();
        int endOffset = nextSibling.getTextRange().getEndOffset() - getTextRange().getStartOffset();
        return new TextRange(startOffset, endOffset);
      }
      return new TextRange(getTextLength(), getTextLength());
    }

    @Override
    public PsiElement getElement() {
      return PsiDocMethodOrFieldRef.this;
    }
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.java.language.impl.psi.impl.source.SourceJavaCodeReference;
import com.intellij.java.language.impl.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.VariableResolverProcessor;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.impl.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.java.language.impl.psi.scope.processor.MethodResolverProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.*;
import consulo.language.impl.psi.CheckUtil;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.*;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.CharTable;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class PsiReferenceExpressionImpl extends ExpressionPsiElement implements PsiReferenceExpression, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance(PsiReferenceExpressionImpl.class);
  private static final ThreadLocal<Map<PsiReferenceExpression, ResolveResult[]>> ourQualifierCache = ThreadLocal.withInitial(() -> new HashMap<>());

  private volatile String myCachedQName;
  private volatile String myCachedNormalizedText;

  public PsiReferenceExpressionImpl() {
    super(JavaElementType.REFERENCE_EXPRESSION);
  }

  @Override
  public PsiExpression getQualifierExpression() {
    return (PsiExpression) findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public PsiElement bindToElementViaStaticImport(@Nonnull PsiClass qualifierClass) throws IncorrectOperationException {
    String qualifiedName = qualifierClass.getQualifiedName();
    if (qualifiedName == null) {
      throw new IncorrectOperationException();
    }

    if (getQualifierExpression() != null) {
      throw new IncorrectOperationException("Reference is qualified: " + getText());
    }
    if (!isPhysical()) {
      // don't qualify reference: the isReferenceTo() check fails anyway, whether we have a static import for this member or not
      return this;
    }
    String staticName = getReferenceName();
    PsiFile containingFile = getContainingFile();
    PsiImportList importList = null;
    boolean doImportStatic;
    if (containingFile instanceof PsiJavaFile) {
      importList = ((PsiJavaFile) containingFile).getImportList();
      assert importList != null : containingFile;
      PsiImportStatementBase singleImportStatement = importList.findSingleImportStatement(staticName);
      doImportStatic = singleImportStatement == null;
      if (singleImportStatement instanceof PsiImportStaticStatement) {
        String qName = qualifierClass.getQualifiedName() + "." + staticName;
        if (qName.equals(singleImportStatement.getImportReference().getQualifiedName())) {
          return this;
        }
      }
    } else {
      doImportStatic = false;
    }
    if (doImportStatic) {
      bindToElementViaStaticImport(qualifierClass, staticName, importList);
    } else {
      PsiManager manager = getManager();
      PsiReferenceExpression classRef = JavaPsiFacade.getElementFactory(manager.getProject()).createReferenceExpression(
          qualifierClass);
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, manager);
      addInternal(dot, dot, SourceTreeToPsiMap.psiElementToTree(getParameterList()), Boolean.TRUE);
      addBefore(classRef, SourceTreeToPsiMap.treeElementToPsi(dot));
    }
    return this;
  }

  public static void bindToElementViaStaticImport(PsiClass qualifierClass, String staticName, PsiImportList importList) throws IncorrectOperationException {
    assert importList != null;
    final String qualifiedName = qualifierClass.getQualifiedName();
    final List<PsiJavaCodeReferenceElement> refs = getImportsFromClass(importList, qualifiedName);
    JavaCodeStyleSettingsFacade javaCodeStyleSettingsFacade = JavaCodeStyleSettingsFacade.getInstance(qualifierClass.getProject());
    if (!javaCodeStyleSettingsFacade.isToImportInDemand(qualifiedName) && refs.size() + 1 < javaCodeStyleSettingsFacade.getNamesCountToUseImportOnDemand() ||
        JavaCodeStyleManager.getInstance(qualifierClass.getProject()).hasConflictingOnDemandImport((PsiJavaFile) importList.getContainingFile(), qualifierClass, staticName)) {
      importList.add(JavaPsiFacade.getElementFactory(qualifierClass.getProject()).createImportStaticStatement(qualifierClass, staticName));
    } else {
      for (PsiJavaCodeReferenceElement ref : refs) {
        final PsiImportStaticStatement importStatement = PsiTreeUtil.getParentOfType(ref, PsiImportStaticStatement.class);
        if (importStatement != null) {
          importStatement.delete();
        }
      }
      importList.add(JavaPsiFacade.getElementFactory(qualifierClass.getProject()).createImportStaticStatement(qualifierClass,
          "*"));
    }
  }

  private static List<PsiJavaCodeReferenceElement> getImportsFromClass(@Nonnull PsiImportList importList, String className) {
    final List<PsiJavaCodeReferenceElement> array = new ArrayList<>();
    for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
      final PsiClass psiClass = staticStatement.resolveTargetClass();
      if (psiClass != null && Comparing.strEqual(psiClass.getQualifiedName(), className)) {
        array.add(staticStatement.getImportReference());
      }
    }
    return array;
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    final PsiExpression oldQualifier = getQualifierExpression();
    if (newQualifier == null) {
      if (oldQualifier != null) {
        deleteChildInternal(oldQualifier.getNode());
      }
    } else {
      if (oldQualifier == null) {
        final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        TreeElement dot = (TreeElement) findChildByRole(ChildRole.DOT);
        if (dot == null) {
          dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, treeCharTab, getManager());
          dot = addInternal(dot, dot, getFirstChildNode(), Boolean.TRUE);
        }
        addBefore(newQualifier, dot.getPsi());
      }
      getQualifierExpression().replace(newQualifier);
    }
  }

  @Override
  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  @Override
  public String getReferenceName() {
    PsiElement element = getReferenceNameElement();
    return element != null ? element.getText() : null;
  }

  @Override
  public void clearCaches() {
    myCachedQName = null;
    myCachedNormalizedText = null;
    super.clearCaches();
  }

  public static final class OurGenericsResolver implements ResolveCache.PolyVariantContextResolver<PsiJavaReference> {
    public static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    @Nonnull
    @Override
    public ResolveResult[] resolve(@Nonnull PsiJavaReference ref, @Nonnull PsiFile containingFile, boolean incompleteCode) {
      PsiReferenceExpressionImpl expression = (PsiReferenceExpressionImpl) ref;
      CompositeElement treeParent = expression.getTreeParent();
      IElementType parentType = treeParent == null ? null : treeParent.getElementType();
      if (!incompleteCode) {
        //optimization:
        //for the expression foo().bar().baz(), first foo() is resolved during resolveAllQualifiers traversal
        //then next qualifier is picked: foo().bar(); to resolve bar(), foo() should be resolved again
        //if the global cache worked, then the result is already in ResolveCache
        //if top level resolve was started in the context where caching is prohibited,
        //foo() is already in the local cache ourQualifiersCache
        ResolveResult[] result = ourQualifierCache.get().get(ref);
        if (result != null) {
          return result;
        }
      }

      boolean empty = ourQualifierCache.get().isEmpty();
      try {
        resolveAllQualifiers(expression, containingFile);
        JavaResolveResult[] result = expression.resolve(parentType, containingFile);

        if (result.length == 0 && incompleteCode && parentType != JavaElementType.REFERENCE_EXPRESSION) {
          result = expression.resolve(JavaElementType.REFERENCE_EXPRESSION, containingFile);
        }

        JavaResolveUtil.substituteResults(expression, result);
        return result;
      } finally {
        //clear cache for the top level expression
        if (empty) {
          ourQualifierCache.remove();
        }
      }
    }

    private static void resolveAllQualifiers(@Nonnull PsiReferenceExpressionImpl expression, @Nonnull PsiFile containingFile) {
      // to avoid SOE, resolve all qualifiers starting from the innermost
      PsiElement qualifier = expression.getQualifier();
      if (qualifier == null) {
        return;
      }

      final ResolveCache resolveCache = ResolveCache.getInstance(containingFile.getProject());
      boolean physical = containingFile.isPhysical();
      qualifier.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (!(expression instanceof PsiReferenceExpressionImpl)) {
            return;
          }
          ResolveResult[] cachedResults = resolveCache.getCachedResults(expression, physical, false, true);
          if (cachedResults != null) {
            return;
          }
          visitElement(expression);
        }

        @Override
        protected void elementFinished(@Nonnull PsiElement element) {
          if (!(element instanceof PsiReferenceExpressionImpl)) {
            return;
          }
          PsiReferenceExpressionImpl chainedQualifier = (PsiReferenceExpressionImpl) element;
          RecursionGuard.StackStamp stamp = RecursionManager.markStack();
          ResolveResult[] res = resolveCache.resolveWithCaching(chainedQualifier, INSTANCE, false, false, containingFile);
          if (stamp.mayCacheNow()) {
            ourQualifierCache.get().put(chainedQualifier, res);
          }
        }

        // walk only qualifiers, not their argument and other associated stuff

        @Override
        public void visitExpressionList(PsiExpressionList list) {
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
        }

        @Override
        public void visitClass(PsiClass aClass) {
        }
      });
    }
  }

  @Nonnull
  private JavaResolveResult[] resolve(IElementType parentType, @Nonnull PsiFile containingFile) {
    if (parentType == JavaElementType.REFERENCE_EXPRESSION) {
      JavaResolveResult[] variable = null;
      JavaResolveResult[] result = resolveToVariable(containingFile);
      if (result.length == 1) {
        if (result[0].isAccessible()) {
          return result;
        }
        variable = result;
      }

      PsiElement classNameElement = getReferenceNameElement();
      if (!(classNameElement instanceof PsiIdentifier)) {
        return JavaResolveResult.EMPTY_ARRAY;
      }

      result = resolveToClass(classNameElement, containingFile);
      if (result.length == 1 && !result[0].isAccessible()) {
        JavaResolveResult[] packageResult = resolveToPackage(containingFile);
        if (packageResult.length != 0) {
          result = packageResult;
        }
      } else if (result.length == 0) {
        result = resolveToPackage(containingFile);
      }

      if (result.length == 0 && variable == null) {
        result = PsiJavaCodeReferenceElementImpl.tryClassResult(getCachedNormalizedText(), this);
      }

      return result.length == 0 && variable != null ? variable : result;
    }

    if (parentType == JavaElementType.METHOD_CALL_EXPRESSION) {
      return resolveToMethod(containingFile);
    }

    if (parentType == JavaElementType.METHOD_REF_EXPRESSION) {
      if (((PsiMethodReferenceExpression) getParent()).isConstructor()) {
        PsiElement classNameElement = getReferenceNameElement();
        if (classNameElement == null) {
          return JavaResolveResult.EMPTY_ARRAY;
        }
        return resolveToClass(classNameElement, containingFile);
      }
      return resolve(JavaElementType.REFERENCE_EXPRESSION, containingFile);
    }

    return resolveToVariable(containingFile);
  }

  @Nonnull
  private JavaResolveResult[] resolveToMethod(@Nonnull PsiFile containingFile) {
    final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) getParent();
    final MethodResolverProcessor processor = new MethodResolverProcessor(methodCall, containingFile);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, methodCall, false);
    } catch (MethodProcessorSetupFailedException e) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    return processor.getResult();
  }

  @Nonnull
  private JavaResolveResult[] resolveToPackage(@Nonnull PsiFile containingFile) {
    final String packageName = getCachedNormalizedText();
    Project project = containingFile.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiPackage aPackage = psiFacade.findPackage(packageName);
    if (aPackage == null) {
      return psiFacade.isPartOfPackagePrefix(packageName)
          ? CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE
          : JavaResolveResult.EMPTY_ARRAY;
    }
    // check that all qualifiers must resolve to package parts, to prevent local vars shadowing corresponding package case
    PsiExpression qualifier = getQualifierExpression();
    if (qualifier instanceof PsiReferenceExpression && !(((PsiReferenceExpression) qualifier).resolve() instanceof PsiPackage)) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY, this, false)};
  }

  @Nonnull
  private JavaResolveResult[] resolveToClass(@Nonnull PsiElement classNameElement, @Nonnull PsiFile containingFile) {
    final String className = classNameElement.getText();

    final ClassResolverProcessor processor = new ClassResolverProcessor(className, this, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  @Nonnull
  private JavaResolveResult[] resolveToVariable(@Nonnull PsiFile containingFile) {
    final VariableResolverProcessor processor = new VariableResolverProcessor(this, containingFile);
    PsiScopesUtil.resolveAndWalk(processor, this, null);
    return processor.getResult();
  }

  @Override
  @Nonnull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    return PsiImplUtil.multiResolveImpl(this, incompleteCode, OurGenericsResolver.INSTANCE);
  }

  @Override
  @Nonnull
  public String getCanonicalText() {
    final PsiElement element = resolve();
    if (element instanceof PsiClass) {
      final String fqn = ((PsiClass) element).getQualifiedName();
      if (fqn != null) {
        return fqn;
      }
    }
    return getCachedNormalizedText();
  }

  private static final Function<PsiReferenceExpressionImpl, PsiType> TYPE_EVALUATOR = new TypeEvaluator();

  private static class TypeEvaluator implements Function<PsiReferenceExpressionImpl, PsiType> {
    @Override
    public PsiType apply(final PsiReferenceExpressionImpl expr) {
      PsiFile file = expr.getContainingFile();
      Project project = file.getProject();
      ResolveResult[] results = ResolveCache.getInstance(project).resolveWithCaching(expr, OurGenericsResolver.INSTANCE, true, false, file);
      JavaResolveResult result = results.length == 1 ? (JavaResolveResult) results[0] : null;

      PsiElement resolve = result == null ? null : result.getElement();
      if (resolve == null) {
        ASTNode refName = expr.findChildByRole(ChildRole.REFERENCE_NAME);
        if (refName != null && "length".equals(refName.getText())) {
          ASTNode qualifier = expr.findChildByRole(ChildRole.QUALIFIER);
          if (qualifier != null && ElementType.EXPRESSION_BIT_SET.contains(qualifier.getElementType())) {
            PsiType type = SourceTreeToPsiMap.<PsiExpression>treeToPsiNotNull(qualifier).getType();
            if (type instanceof PsiArrayType) {
              return PsiType.INT;
            }
          }
        }
        return null;
      }

      PsiTypeParameterListOwner owner = null;
      PsiType ret = null;
      if (resolve instanceof PsiVariable) {
        PsiType type = ((PsiVariable) resolve).getType();
        ret = type instanceof PsiEllipsisType ? ((PsiEllipsisType) type).toArrayType() : type;
        if (ret != null && !ret.isValid()) {
          PsiUtil.ensureValidType(ret, "invalid type of " + resolve + " of class " + resolve.getClass() + ", valid=" + resolve.isValid());
        }
        if (resolve instanceof PsiField && !((PsiField) resolve).hasModifierProperty(PsiModifier.STATIC)) {
          owner = ((PsiField) resolve).getContainingClass();
        }
      } else if (resolve instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) resolve;
        ret = method.getReturnType();
        if (ret != null) {
          PsiUtil.ensureValidType(ret);
        }
        owner = method;
      }
      if (ret == null) {
        return null;
      }

      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);
      if (ret instanceof PsiClassType) {
        ret = ((PsiClassType) ret).setLanguageLevel(languageLevel);
      }

      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
        final PsiSubstitutor substitutor = result.getSubstitutor();
        if (owner == null || !PsiUtil.isRawSubstitutor(owner, substitutor)) {
          PsiType substitutedType = substitutor.substitute(ret);
          PsiUtil.ensureValidType(substitutedType);
          PsiType normalized = PsiImplUtil.normalizeWildcardTypeByPosition(substitutedType, expr);
          PsiUtil.ensureValidType(normalized);
          return PsiClassImplUtil.correctType(normalized, expr.getResolveScope());
        }
      }

      return PsiClassImplUtil.correctType(TypeConversionUtil.erasure(ret), expr.getResolveScope());
    }
  }

  @Override
  public PsiType getType() {
    return JavaResolveCache.getInstance(getProject()).getType(this, TYPE_EVALUATOR);
  }

  @Override
  public boolean isReferenceTo(@Nonnull PsiElement element) {
    IElementType i = getLastChildNode().getElementType();
    boolean resolvingToMethod = element instanceof PsiMethod;
    if (i == JavaTokenType.IDENTIFIER) {
      if (!(element instanceof PsiPackage)) {
        if (!(element instanceof PsiNamedElement)) {
          return false;
        }
        String name = ((PsiNamedElement) element).getName();
        if (name == null) {
          return false;
        }
        if (!name.equals(getLastChildNode().getText())) {
          return false;
        }
      }
    } else if (i == JavaTokenType.SUPER_KEYWORD || i == JavaTokenType.THIS_KEYWORD) {
      if (!resolvingToMethod) {
        return false;
      }
      if (!((PsiMethod) element).isConstructor()) {
        return false;
      }
    }

    PsiElement parent = getParent();
    boolean parentIsMethodCall = parent instanceof PsiMethodCallExpression;
    // optimization: methodCallExpression should resolve to a method
    if (parentIsMethodCall != resolvingToMethod) {
      return false;
    }

    return element.getManager().areElementsEquivalent(element, advancedResolve(true).getElement());
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void processVariants(@Nonnull PsiScopeProcessor processor) {
    DelegatingScopeProcessor filterProcessor = new DelegatingScopeProcessor(processor) {
      private PsiElement myResolveContext;
      private final Set<String> myVarNames = new HashSet<>();

      @Override
      public boolean execute(@Nonnull final PsiElement element, @Nonnull final ResolveState state) {
        return !shouldProcess(element) || super.execute(element, state);
      }

      private boolean shouldProcess(@Nonnull PsiElement element) {
        if (element instanceof PsiVariable) {
          return ensureNonShadowedVariable((PsiVariable) element);
        }
        if (element instanceof PsiClass) {
          return !seemsScrambled((PsiClass) element);
        }
        if (element instanceof PsiPackage) {
          return isQualified();
        }
        if (element instanceof PsiMethod) {
          return shouldProcessMethod((PsiMethod) element);
        }
        return false;
      }

      private boolean ensureNonShadowedVariable(@Nonnull PsiVariable element) {
        if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
          myVarNames.add(element.getName());
        }
        return !(element instanceof PsiField) || !myVarNames.contains(element.getName());
      }

      private boolean shouldProcessMethod(@Nonnull PsiMethod method) {
        PsiReferenceExpressionImpl ref = PsiReferenceExpressionImpl.this;
        return !method.isConstructor() && hasValidQualifier(method, ref, myResolveContext);
      }

      @Override
      public void handleEvent(@Nonnull Event event, Object associated) {
        if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
          myResolveContext = (PsiElement) associated;
        }
        super.handleEvent(event, associated);
      }

    };
    PsiScopesUtil.resolveAndWalk(filterProcessor, this, null, true);
  }

  @Nonnull
  @Override
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  /* see also HighlightMethodUtil.checkStaticInterfaceMethodCallQualifier() */
  private static boolean hasValidQualifier(PsiMethod method, PsiReferenceExpression ref, PsiElement scope) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass != null && containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      if (!PsiUtil.getLanguageLevel(ref).isAtLeast(LanguageLevel.JDK_1_8)) {
        return false;
      }

      PsiExpression qualifierExpression = ref.getQualifierExpression();
      if (qualifierExpression == null && (scope instanceof PsiImportStaticStatement || PsiTreeUtil.isAncestor(containingClass, ref, true))) {
        return true;
      }

      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression) qualifierExpression).resolve();
        if (containingClass.getManager().areElementsEquivalent(resolve, containingClass)) {
          return true;
        }

        if (resolve instanceof PsiTypeParameter) {
          Set<PsiClass> classes = new HashSet<>();
          for (PsiClassType type : ((PsiTypeParameter) resolve).getExtendsListTypes()) {
            final PsiClass aClass = type.resolve();
            if (aClass != null) {
              classes.add(aClass);
            }
          }

          if (classes.size() == 1 && classes.contains(containingClass)) {
            return true;
          }
        }
      }

      return false;
    }

    return true;
  }

  public static boolean seemsScrambled(@Nullable PsiClass aClass) {
    return aClass instanceof PsiCompiledElement && seemsScrambledByStructure(aClass);
  }

  public static boolean seemsScrambledByStructure(@Nonnull PsiClass aClass) {
    PsiClass containingClass = aClass.getContainingClass();
    if (containingClass != null && !seemsScrambledByStructure(containingClass)) {
      return false;
    }

    if (seemsScrambled(aClass.getName())) {
      List<PsiMethod> methods = ContainerUtil.filter(aClass.getMethods(), method -> !method.hasModifierProperty(PsiModifier.PRIVATE));

      return !methods.isEmpty() && ContainerUtil.and(methods, method -> seemsScrambled(method.getName()));
    }

    return false;
  }

  private static boolean seemsScrambled(String name) {
    return name != null && !name.isEmpty() && name.length() <= 2;
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, PsiReferenceParameterList.class);
  }

  @Override
  public int getTypeParameterCount() {
    PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArgumentCount() : 0;
  }

  @Nonnull
  @Override
  public PsiType[] getTypeParameters() {
    PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArguments() : PsiType.EMPTY_ARRAY;
  }

  @Override
  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    return refName == null ? super.getTextOffset() : refName.getStartOffset();
  }

  @Override
  public PsiElement handleElementRename(@Nonnull String newElementName) throws IncorrectOperationException {
    if (getQualifierExpression() != null) {
      return renameDirectly(newElementName);
    }
    final JavaResolveResult resolveResult = advancedResolve(false);
    if (resolveResult.getElement() == null) {
      return renameDirectly(newElementName);
    }
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    if (!(currentFileResolveScope instanceof PsiImportStaticStatement) ||
        ((PsiImportStaticStatement) currentFileResolveScope).isOnDemand()) {
      return renameDirectly(newElementName);
    }
    final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement) currentFileResolveScope;
    final String referenceName = importStaticStatement.getReferenceName();
    LOG.assertTrue(referenceName != null);
    final PsiElement element = importStaticStatement.getImportReference().resolve();
    if (getManager().areElementsEquivalent(element, resolveResult.getElement())) {
      return renameDirectly(newElementName);
    }
    final PsiClass psiClass = importStaticStatement.resolveTargetClass();
    if (psiClass == null) {
      return renameDirectly(newElementName);
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    final PsiReferenceExpression expression = (PsiReferenceExpression) factory.createExpressionFromText("X." + newElementName, this);
    final PsiReferenceExpression result = (PsiReferenceExpression) replace(expression);
    ((PsiReferenceExpression) result.getQualifierExpression()).bindToElement(psiClass);
    return result;
  }

  private PsiElement renameDirectly(String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final String oldRefName = oldIdentifier.getText();
    if (PsiKeyword.THIS.equals(oldRefName) || PsiKeyword.SUPER.equals(oldRefName) || Comparing.strEqual(oldRefName, newElementName)) {
      return this;
    }
    PsiIdentifier identifier = JavaPsiFacade.getElementFactory(getProject()).createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  @Override
  public PsiElement bindToElement(@Nonnull final PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element)) {
      return this;
    }

    final PsiManager manager = getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiJavaParserFacade parserFacade = facade.getParserFacade();
    if (element instanceof PsiClass) {
      final boolean preserveQualification = JavaCodeStyleSettingsFacade.getInstance(getProject()).useFQClassNames() && isFullyQualified(this);
      String qName = ((PsiClass) element).getQualifiedName();
      if (qName == null) {
        qName = ((PsiClass) element).getName();
      } else if (JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, getResolveScope()) == null && !preserveQualification) {
        return this;
      } else if (facade.getResolveHelper().resolveReferencedClass(qName, this) == null &&
          facade.getResolveHelper().resolveReferencedClass(StringUtil.getPackageName(qName), this) != null) {
        qName = ((PsiClass) element).getName();
        assert qName != null : element;
      }
      PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement) ref.getNode());
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(manager.getProject());
      if (!preserveQualification) {
        ref = (PsiExpression) codeStyleManager.shortenClassReferences(ref, JavaCodeStyleManager.INCOMPLETE_CODE);
      }
      return ref;
    } else if (element instanceof PsiPackage) {
      final String qName = ((PsiPackage) element).getQualifiedName();
      if (qName.isEmpty()) {
        throw new IncorrectOperationException();
      }
      final PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement) ref.getNode());
      return ref;
    } else if ((element instanceof PsiField || element instanceof PsiMethod) && ((PsiMember) element).hasModifierProperty(PsiModifier.STATIC)) {
      if (!isPhysical()) {
        // don't qualify reference: the isReferenceTo() check fails anyway, whether we have a static import for this member or not
        return this;
      }
      final PsiMember member = (PsiMember) element;
      final PsiClass psiClass = member.getContainingClass();
      if (psiClass == null) {
        throw new IncorrectOperationException();
      }
      final String qName = psiClass.getQualifiedName() + "." + member.getName();
      final PsiExpression ref = parserFacade.createExpressionFromText(qName, this);
      getTreeParent().replaceChildInternal(this, (TreeElement) ref.getNode());
      return ref;
    } else {
      throw new IncorrectOperationException(element.toString());
    }
  }

  private static boolean isFullyQualified(CompositeElement classRef) {
    ASTNode qualifier = classRef.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier == null) {
      return false;
    }
    if (qualifier.getElementType() != JavaElementType.REFERENCE_EXPRESSION) {
      return false;
    }
    PsiElement refElement = ((PsiReference) qualifier).resolve();
    return refElement instanceof PsiPackage || isFullyQualified((CompositeElement) qualifier);
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      ASTNode dot = findChildByType(JavaTokenType.DOT, child);
      assert dot != null : this;
      deleteChildRange(child.getPsi(), dot.getPsi());

      ASTNode first = getFirstChildNode();
      if (getChildRole(first) == ChildRole.REFERENCE_PARAMETER_LIST && first.getFirstChildNode() == null) {
        ASTNode start = first.getTreeNext();
        if (PsiImplUtil.isWhitespaceOrComment(start)) {
          ASTNode next = PsiImplUtil.skipWhitespaceAndComments(start);
          assert next != null : this;
          CodeEditUtil.removeChildren(this, start, next.getTreePrev());
        }
      }
    } else if (child.getElementType() == JavaElementType.REFERENCE_PARAMETER_LIST) {
      replaceChildInternal(child, createEmptyRefParameterList(getProject()));
    } else {
      super.deleteChildInternal(child);
    }
  }

  public static TreeElement createEmptyRefParameterList(Project project) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return (TreeElement) Objects.requireNonNull(factory.createReferenceFromText("foo", null).getParameterList()).getNode();
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      case ChildRole.REFERENCE_NAME:
        TreeElement lastChild = getLastChildNode();
        return getChildRole(lastChild) == role ? lastChild : findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.QUALIFIER:
        TreeElement firstChild = getFirstChildNode();
        return getChildRole(firstChild) == ChildRole.QUALIFIER ? firstChild : null;

      case ChildRole.REFERENCE_PARAMETER_LIST:
        return findChildByType(JavaElementType.REFERENCE_PARAMETER_LIST);

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);
    }

    return null;
  }

  @Override
  public int getChildRole(@Nonnull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    if (i == JavaElementType.REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    if (i == JavaTokenType.IDENTIFIER || i == JavaTokenType.THIS_KEYWORD || i == JavaTokenType.SUPER_KEYWORD) {
      return ChildRole.REFERENCE_NAME;
    }
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      return ChildRole.QUALIFIER;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  public PsiReference getReference() {
    return getReferenceNameElement() != null ? this : null;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitReferenceExpression(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Nonnull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @Nonnull
  @Override
  public TextRange getRangeInElement() {
    return PsiJavaCodeReferenceElementImpl.calcRangeInElement(this);
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public String getClassNameText() {
    String cachedQName = myCachedQName;
    if (cachedQName == null) {
      myCachedQName = cachedQName = PsiNameHelper.getQualifiedClassName(getCachedNormalizedText(), false);
    }
    return cachedQName;
  }

  @Override
  public void fullyQualify(@Nonnull PsiClass targetClass) {
    JavaSourceUtil.fullyQualifyReference(this, targetClass);
  }

  @Override
  public boolean isQualified() {
    return getChildRole(getFirstChildNode()) == ChildRole.QUALIFIER;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }

  @Nonnull
  private String getCachedNormalizedText() {
    String whiteSpaceAndComments = myCachedNormalizedText;
    if (whiteSpaceAndComments == null) {
      myCachedNormalizedText = whiteSpaceAndComments = JavaSourceUtil.getReferenceText(this);
    }
    return whiteSpaceAndComments;
  }

  @Override
  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }
}
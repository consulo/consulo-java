package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.impl.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import consulo.codeInsight.TargetElementUtilEx;
import consulo.language.editor.TargetElementUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import junit.framework.Assert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * @author ven
 */
public abstract class ChangeSignaturePropagationTest extends LightRefactoringTestCase  {
  public void testParamSimple() throws Exception {
    parameterPropagationTest();
  }

  public void testParamWithOverriding() throws Exception {
    parameterPropagationTest();
  }

  public void testParamTypeSubst() throws Exception {
    PsiMethod method = getPrimaryMethod();
    HashSet<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiReference reference : ReferencesSearch.search(method)) {
      PsiMethod psiMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
      if (psiMethod != null) {
        methods.add(psiMethod);
      }
    }
    parameterPropagationTest(method, methods, JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("T"));
  }

  public void testExceptionSimple() throws Exception {
    exceptionPropagationTest();
  }

  public void testExceptionWithOverriding() throws Exception {
    exceptionPropagationTest();
  }

  public void testParamWithNoConstructor() throws Exception {
    PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectNonPhysicalMethodsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

   public void testExceptionWithNoConstructor() throws Exception {
    PsiMethod method = getPrimaryMethod();
     exceptionPropagationTest(method, collectNonPhysicalMethodsToPropagate(method));
  }

  private static HashSet<PsiMethod> collectNonPhysicalMethodsToPropagate(PsiMethod method) {
    HashSet<PsiMethod> methodsToPropagate = new HashSet<PsiMethod>();
    PsiReference[] references =
      MethodReferencesSearch.search(method, GlobalSearchScope.allScope(getProject()), true).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference reference : references) {
      PsiElement element = reference.getElement();
      Assert.assertTrue(element instanceof PsiClass);
      PsiClass containingClass = (PsiClass)element;
      methodsToPropagate.add(JavaPsiFacade.getElementFactory(getProject()).createMethodFromText(containingClass.getName() + "(){}", containingClass));
    }
    return methodsToPropagate;
  }

  public void testParamWithImplicitConstructor() throws Exception {
    PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  public void testParamWithImplicitConstructors() throws Exception {
    PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  public void testExceptionWithImplicitConstructor() throws Exception {
    PsiMethod method = getPrimaryMethod();
    exceptionPropagationTest(method, collectDefaultConstructorsToPropagate(method));
  }

  private static HashSet<PsiMethod> collectDefaultConstructorsToPropagate(PsiMethod method) {
    HashSet<PsiMethod> methodsToPropagate = new HashSet<PsiMethod>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(method.getContainingClass())) {
      methodsToPropagate.add(inheritor.getConstructors()[0]);
    }
    return methodsToPropagate;
  }

  private void parameterPropagationTest() throws Exception {
    parameterPropagationTest(JavaPsiFacade.getElementFactory(getProject())
                               .createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  private void parameterPropagationTest(PsiClassType paramType) throws Exception {
    PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, new HashSet<PsiMethod>(Arrays.asList(method.getContainingClass().getMethods())),
                             paramType);
  }

  private void parameterPropagationTest(PsiMethod method, HashSet<PsiMethod> psiMethods, PsiType paramType) throws Exception {
    ParameterInfoImpl[] newParameters = new ParameterInfoImpl[]{new ParameterInfoImpl(-1, "clazz", paramType, "null")};
    doTest(newParameters, new ThrownExceptionInfo[0], psiMethods, null, method);
  }

  private void exceptionPropagationTest() throws Exception {
    PsiMethod method = getPrimaryMethod();
    exceptionPropagationTest(method, new HashSet<PsiMethod>(Arrays.asList(method.getContainingClass().getMethods())));
  }

  private void exceptionPropagationTest(PsiMethod method, Set<PsiMethod> methodsToPropagateExceptions) throws Exception {
    PsiClassType newExceptionType = JavaPsiFacade.getElementFactory(getProject())
      .createTypeByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, GlobalSearchScope.allScope(getProject()));
    ThrownExceptionInfo[] newExceptions = new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, newExceptionType)};
    doTest(new ParameterInfoImpl[0], newExceptions, null, methodsToPropagateExceptions, method);
  }

  private void doTest(ParameterInfoImpl[] newParameters,
                      ThrownExceptionInfo[] newExceptions,
                      Set<PsiMethod> methodsToPropagateParameterChanges,
                      Set<PsiMethod> methodsToPropagateExceptionChanges,
                      PsiMethod primaryMethod) throws Exception {
    String filePath = getBasePath() + getTestName(false) + ".java";
    PsiType returnType = primaryMethod.getReturnType();
    CanonicalTypes.Type type = returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
    new ChangeSignatureProcessor(getProject(), primaryMethod, false, null,
                                 primaryMethod.getName(),
                                 type,
                                 generateParameterInfos(primaryMethod, newParameters),
                                 generateExceptionInfos(primaryMethod, newExceptions),
                                 methodsToPropagateParameterChanges,
                                 methodsToPropagateExceptionChanges).run();
    checkResultByFile(filePath + ".after");
  }

  private PsiMethod getPrimaryMethod() throws Exception {
    String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    PsiElement targetElement = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    return (PsiMethod) targetElement;
  }

  private static String getBasePath() {
    return "/refactoring/changeSignaturePropagation/";
  }

  private static ParameterInfoImpl[] generateParameterInfos (PsiMethod method, ParameterInfoImpl[] newParameters) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    ParameterInfoImpl[] result = new ParameterInfoImpl[parameters.length + newParameters.length];
    for (int i = 0; i < parameters.length; i++) {
      result[i] = new ParameterInfoImpl(i);
    }
    System.arraycopy(newParameters, 0, result, parameters.length, newParameters.length);
    return result;
  }

  private static ThrownExceptionInfo[] generateExceptionInfos (PsiMethod method, ThrownExceptionInfo[] newExceptions) {
    PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[exceptions.length + newExceptions.length];
    for (int i = 0; i < exceptions.length; i++) {
      result[i] = new JavaThrownExceptionInfo(i);
    }
    System.arraycopy(newExceptions, 0, result, exceptions.length, newExceptions.length);
    return result;
  }

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

}

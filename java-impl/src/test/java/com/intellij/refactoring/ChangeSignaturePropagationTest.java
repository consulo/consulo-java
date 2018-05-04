package com.intellij.refactoring;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Set;

import javax.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import consulo.codeInsight.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;
import junit.framework.Assert;

/**
 * @author ven
 */
public class ChangeSignaturePropagationTest extends LightRefactoringTestCase  {
  public void testParamSimple() throws Exception {
    parameterPropagationTest();
  }

  public void testParamWithOverriding() throws Exception {
    parameterPropagationTest();
  }

  public void testParamTypeSubst() throws Exception {
    final PsiMethod method = getPrimaryMethod();
    final HashSet<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiReference reference : ReferencesSearch.search(method)) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
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
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectNonPhysicalMethodsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

   public void testExceptionWithNoConstructor() throws Exception {
    final PsiMethod method = getPrimaryMethod();
     exceptionPropagationTest(method, collectNonPhysicalMethodsToPropagate(method));
  }

  private static HashSet<PsiMethod> collectNonPhysicalMethodsToPropagate(PsiMethod method) {
    final HashSet<PsiMethod> methodsToPropagate = new HashSet<PsiMethod>();
    final PsiReference[] references =
      MethodReferencesSearch.search(method, GlobalSearchScope.allScope(getProject()), true).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      Assert.assertTrue(element instanceof PsiClass);
      PsiClass containingClass = (PsiClass)element;
      methodsToPropagate.add(JavaPsiFacade.getElementFactory(getProject()).createMethodFromText(containingClass.getName() + "(){}", containingClass));
    }
    return methodsToPropagate;
  }

  public void testParamWithImplicitConstructor() throws Exception {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  public void testParamWithImplicitConstructors() throws Exception {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, collectDefaultConstructorsToPropagate(method), JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  public void testExceptionWithImplicitConstructor() throws Exception {
    final PsiMethod method = getPrimaryMethod();
    exceptionPropagationTest(method, collectDefaultConstructorsToPropagate(method));
  }

  private static HashSet<PsiMethod> collectDefaultConstructorsToPropagate(PsiMethod method) {
    final HashSet<PsiMethod> methodsToPropagate = new HashSet<PsiMethod>();
    for (PsiClass inheritor : ClassInheritorsSearch.search(method.getContainingClass())) {
      methodsToPropagate.add(inheritor.getConstructors()[0]);
    }
    return methodsToPropagate;
  }

  private void parameterPropagationTest() throws Exception {
    parameterPropagationTest(JavaPsiFacade.getElementFactory(getProject())
                               .createTypeByFQClassName("java.lang.Class", GlobalSearchScope.allScope(getProject())));
  }

  private void parameterPropagationTest(final PsiClassType paramType) throws Exception {
    final PsiMethod method = getPrimaryMethod();
    parameterPropagationTest(method, new HashSet<PsiMethod>(Arrays.asList(method.getContainingClass().getMethods())),
                             paramType);
  }

  private void parameterPropagationTest(final PsiMethod method, final HashSet<PsiMethod> psiMethods, final PsiType paramType) throws Exception {
    final ParameterInfoImpl[] newParameters = new ParameterInfoImpl[]{new ParameterInfoImpl(-1, "clazz", paramType, "null")};
    doTest(newParameters, new ThrownExceptionInfo[0], psiMethods, null, method);
  }

  private void exceptionPropagationTest() throws Exception {
    final PsiMethod method = getPrimaryMethod();
    exceptionPropagationTest(method, new HashSet<PsiMethod>(Arrays.asList(method.getContainingClass().getMethods())));
  }

  private void exceptionPropagationTest(final PsiMethod method, final Set<PsiMethod> methodsToPropagateExceptions) throws Exception {
    PsiClassType newExceptionType = JavaPsiFacade.getElementFactory(getProject()).createTypeByFQClassName("java.lang.Exception", GlobalSearchScope.allScope(getProject()));
    final ThrownExceptionInfo[] newExceptions = new ThrownExceptionInfo[]{new JavaThrownExceptionInfo(-1, newExceptionType)};
    doTest(new ParameterInfoImpl[0], newExceptions, null, methodsToPropagateExceptions, method);
  }

  private void doTest(ParameterInfoImpl[] newParameters,
                      final ThrownExceptionInfo[] newExceptions,
                      Set<PsiMethod> methodsToPropagateParameterChanges,
                      Set<PsiMethod> methodsToPropagateExceptionChanges,
                      PsiMethod primaryMethod) throws Exception {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    final PsiType returnType = primaryMethod.getReturnType();
    final CanonicalTypes.Type type = returnType == null ? null : CanonicalTypes.createTypeWrapper(returnType);
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
    final String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    return (PsiMethod) targetElement;
  }

  private static String getBasePath() {
    return "/refactoring/changeSignaturePropagation/";
  }

  private static ParameterInfoImpl[] generateParameterInfos (PsiMethod method, ParameterInfoImpl[] newParameters) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    ParameterInfoImpl[] result = new ParameterInfoImpl[parameters.length + newParameters.length];
    for (int i = 0; i < parameters.length; i++) {
      result[i] = new ParameterInfoImpl(i);
    }
    System.arraycopy(newParameters, 0, result, parameters.length, newParameters.length);
    return result;
  }

  private static ThrownExceptionInfo[] generateExceptionInfos (PsiMethod method, ThrownExceptionInfo[] newExceptions) {
    final PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
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
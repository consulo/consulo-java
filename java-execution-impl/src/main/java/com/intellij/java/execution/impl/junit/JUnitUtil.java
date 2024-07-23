// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.impl.junit;

import com.intellij.java.execution.JUnitRecognizer;
import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.impl.codeInsight.MetaAnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiClassUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import com.intellij.java.language.testIntegration.TestFramework;
import com.siyeh.ig.psiutils.TestUtils;
import consulo.annotation.DeprecationInfo;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.application.util.CachedValueProvider;
import consulo.execution.CantRunException;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.test.SourceScope;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
@Deprecated
@DeprecationInfo("Use JUnitUtil from junit plugin")
public class JUnitUtil {
  public static final String TEST_CASE_CLASS = "junit.framework.TestCase";
  private static final String TEST_INTERFACE = "junit.framework.Test";
  private static final String TEST_SUITE_CLASS = "junit.framework.TestSuite";
  public static final String TEST_ANNOTATION = "org.junit.Test";
  public static final String TEST5_PACKAGE_FQN = "org.junit.jupiter.api";
  public static final String TEST5_ANNOTATION = "org.junit.jupiter.api.Test";
  public static final String CUSTOM_TESTABLE_ANNOTATION = "org.junit.platform.commons.annotation.Testable";
  public static final String TEST5_FACTORY_ANNOTATION = "org.junit.jupiter.api.TestFactory";
  public static final String IGNORE_ANNOTATION = "org.junit.Ignore";
  public static final String RUN_WITH = "org.junit.runner.RunWith";
  public static final String DATA_POINT = "org.junit.experimental.theories.DataPoint";
  public static final String SUITE_METHOD_NAME = "suite";

  public static final String BEFORE_ANNOTATION_NAME = "org.junit.Before";
  public static final String AFTER_ANNOTATION_NAME = "org.junit.After";

  public static final String BEFORE_EACH_ANNOTATION_NAME = "org.junit.jupiter.api.BeforeEach";
  public static final String AFTER_EACH_ANNOTATION_NAME = "org.junit.jupiter.api.AfterEach";

  public static final String PARAMETRIZED_PARAMETERS_ANNOTATION_NAME = "org.junit.runners.Parameterized.Parameters";
  public static final String PARAMETRIZED_PARAMETER_ANNOTATION_NAME = "org.junit.runners.Parameterized.Parameter";

  public static final String AFTER_CLASS_ANNOTATION_NAME = "org.junit.AfterClass";
  public static final String BEFORE_CLASS_ANNOTATION_NAME = "org.junit.BeforeClass";
  public static final Collection<String> TEST5_CONFIG_METHODS = Collections.unmodifiableList(Arrays.asList(BEFORE_EACH_ANNOTATION_NAME, AFTER_EACH_ANNOTATION_NAME));

  public static final String BEFORE_ALL_ANNOTATION_NAME = "org.junit.jupiter.api.BeforeAll";
  public static final String AFTER_ALL_ANNOTATION_NAME = "org.junit.jupiter.api.AfterAll";
  public static final Collection<String> TEST5_STATIC_CONFIG_METHODS = Collections.unmodifiableList(Arrays.asList(BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME));

  public static final Collection<String> TEST5_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(TEST5_ANNOTATION, TEST5_FACTORY_ANNOTATION, CUSTOM_TESTABLE_ANNOTATION));
  public static final Collection<String> TEST5_JUPITER_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(TEST5_ANNOTATION, TEST5_FACTORY_ANNOTATION));

  private static final List<String> INSTANCE_CONFIGS = Arrays.asList(BEFORE_ANNOTATION_NAME, AFTER_ANNOTATION_NAME);
  private static final List<String> INSTANCE_5_CONFIGS = Arrays.asList(BEFORE_EACH_ANNOTATION_NAME, AFTER_EACH_ANNOTATION_NAME);

  private static final List<String> STATIC_CONFIGS = Arrays.asList(BEFORE_CLASS_ANNOTATION_NAME, AFTER_CLASS_ANNOTATION_NAME, PARAMETRIZED_PARAMETERS_ANNOTATION_NAME);
  private static final List<String> STATIC_5_CONFIGS = Arrays.asList(BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME);

  private static final Collection<String> CONFIGURATIONS_ANNOTATION_NAME = Collections.unmodifiableList(Arrays.asList(DATA_POINT, AFTER_ANNOTATION_NAME, BEFORE_ANNOTATION_NAME,
      AFTER_CLASS_ANNOTATION_NAME, BEFORE_CLASS_ANNOTATION_NAME, BEFORE_ALL_ANNOTATION_NAME, AFTER_ALL_ANNOTATION_NAME));

  public static final String PARAMETERIZED_CLASS_NAME = "org.junit.runners.Parameterized";
  public static final String SUITE_CLASS_NAME = "org.junit.runners.Suite";
  public static final String JUNIT5_NESTED = "org.junit.jupiter.api.Nested";

  private static final String[] RUNNERS_UNAWARE_OF_INNER_CLASSES = {
      "org.junit.runners.Parameterized",
      "org.junit.runners.BlockJUnit4ClassRunner",
      "org.junit.runners.JUnit4",
      "org.junit.internal.runners.JUnit38ClassRunner",
      "org.junit.internal.runners.JUnit4ClassRunner",
      "org.junit.runners.Suite"
  };

  private static final String[] RUNNERS_REQUIRE_ANNOTATION_ON_TEST_METHOD = {
      "org.junit.runners.Parameterized",
      "org.junit.runners.BlockJUnit4ClassRunner",
      "org.junit.runners.JUnit4",
      "org.junit.internal.runners.JUnit4ClassRunner"
  };

  public static boolean isSuiteMethod(@Nonnull PsiMethod psiMethod) {
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (psiMethod.isConstructor()) {
      return false;
    }
    if (psiMethod.getParameterList().getParametersCount() > 0) {
      return false;
    }
    final PsiType returnType = psiMethod.getReturnType();
    if (returnType == null || returnType instanceof PsiPrimitiveType) {
      return false;
    }
    return returnType.equalsToText(TEST_INTERFACE) || returnType.equalsToText(TEST_SUITE_CLASS)
      || InheritanceUtil.isInheritor(returnType, TEST_INTERFACE);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location) {
    return isTestMethod(location, true);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location, boolean checkAbstract) {
    return isTestMethod(location, checkAbstract, true);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location, boolean checkAbstract, boolean checkRunWith) {
    return isTestMethod(location, checkAbstract, checkRunWith, true);
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location, boolean checkAbstract, boolean checkRunWith, boolean checkClass) {
    final PsiMethod psiMethod = location.getPsiElement();
    final PsiClass aClass = location instanceof MethodLocation methodLocation
      ? methodLocation.getContainingClass() : psiMethod.getContainingClass();
    if (checkClass && (aClass == null || !isTestClass(aClass, checkAbstract, true))) {
      return false;
    }
    if (isTestAnnotated(psiMethod)) {
      return true;
    }
    if (psiMethod.isConstructor()) {
      return false;
    }
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    if (AnnotationUtil.isAnnotated(psiMethod, CONFIGURATIONS_ANNOTATION_NAME, 0)) {
      return false;
    }
    if (checkClass && checkRunWith) {
      PsiAnnotation annotation = getRunWithAnnotation(aClass);
      if (annotation != null) {
        return !isInheritorOrSelfRunner(annotation, RUNNERS_REQUIRE_ANNOTATION_ON_TEST_METHOD);
      }
    }
    if (psiMethod.getParameterList().getParametersCount() > 0) {
      return false;
    }
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (!psiMethod.getName().startsWith("test")) {
      return false;
    }
    if (checkClass) {
      PsiClass testCaseClass = getTestCaseClassOrNull(location);
      if (testCaseClass == null || !psiMethod.getContainingClass().isInheritor(testCaseClass, true)) {
        return false;
      }
    }
    return PsiType.VOID.equals(psiMethod.getReturnType());
  }

  public static boolean isTestCaseInheritor(final PsiClass aClass) {
    if (!aClass.isValid()) {
      return false;
    }
    Location<PsiClass> location = PsiLocation.fromPsiElement(aClass);
    PsiClass testCaseClass = getTestCaseClassOrNull(location);
    return testCaseClass != null && aClass.isInheritor(testCaseClass, true);
  }

  public static boolean isTestClass(final PsiClass psiClass) {
    return isTestClass(psiClass, true, true);
  }

  public static boolean isTestClass(@Nonnull PsiClass psiClass, boolean checkAbstract, boolean checkForTestCaseInheritance) {
    if (psiClass.getQualifiedName() == null) {
      return false;
    }
    if (isJUnit5(psiClass) && isJUnit5TestClass(psiClass, checkAbstract)) {
      return true;
    }
    final PsiClass topLevelClass = PsiTreeUtil.getTopmostParentOfType(psiClass, PsiClass.class);
    if (topLevelClass != null) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(topLevelClass, Collections.singleton(RUN_WITH));
      if (annotation != null) {
        final PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue("value");
        if (attributeValue instanceof PsiClassObjectAccessExpression classObjectAccessExpression) {
          final String runnerName = classObjectAccessExpression.getOperand().getType().getCanonicalText();
          if (!(PARAMETERIZED_CLASS_NAME.equals(runnerName) || SUITE_CLASS_NAME.equals(runnerName))) {
            return true;
          }
        }
      }
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, true, checkAbstract)) {
      return false;
    }

    if (AnnotationUtil.isAnnotated(psiClass, RUN_WITH, CHECK_HIERARCHY)) {
      return true;
    }

    if (checkForTestCaseInheritance && isTestCaseInheritor(psiClass)) {
      return true;
    }

    return LanguageCachedValueUtil.getCachedValue(
      psiClass,
      () -> CachedValueProvider.Result.create(hasTestOrSuiteMethods(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)
    );
  }

  private static boolean hasTestOrSuiteMethods(@Nonnull PsiClass psiClass) {
    for (final PsiMethod method : psiClass.getAllMethods()) {
      if (isSuiteMethod(method)) {
        return true;
      }
      if (isTestAnnotated(method)) {
        return true;
      }
    }

    if (isJUnit5(psiClass)) {
      for (PsiClass innerClass : psiClass.getInnerClasses()) {
        for (PsiMethod method : innerClass.getAllMethods()) {
          if (isTestAnnotated(method)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  public static boolean isJUnit3TestClass(final PsiClass clazz) {
    return isTestCaseInheritor(clazz);
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass) {
    return isJUnit4TestClass(psiClass, true);
  }

  public static boolean isJUnit4TestClass(final PsiClass psiClass, boolean checkAbstract) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) {
      return false;
    }
    final PsiClass topLevelClass = PsiTreeUtil.getTopmostParentOfType(modifierList, PsiClass.class);
    if (topLevelClass != null) {
      if (AnnotationUtil.isAnnotated(topLevelClass, RUN_WITH, CHECK_HIERARCHY)) {
        PsiAnnotation annotation = getRunWithAnnotation(topLevelClass);
        if (topLevelClass == psiClass) {
          return true;
        }

        //default runners do not implicitly run inner classes
        if (annotation != null && !isInheritorOrSelfRunner(annotation, RUNNERS_UNAWARE_OF_INNER_CLASSES)) {
          return true;
        }
      }
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, true, checkAbstract)) {
      return false;
    }

    for (final PsiMethod method : psiClass.getAllMethods()) {
      ProgressManager.checkCanceled();
      if (isTestAnnotated(method)) {
        return true;
      }
    }

    return false;
  }

  @RequiredReadAction
  public static boolean isJUnit5TestClass(@Nonnull final PsiClass psiClass, boolean checkAbstract) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) {
      return false;
    }

    if (psiClass.getContainingClass() != null && AnnotationUtil.isAnnotated(psiClass, JUNIT5_NESTED, 0)) {
      return true;
    }

    if (MetaAnnotationUtil.isMetaAnnotated(psiClass, Collections.singleton(CUSTOM_TESTABLE_ANNOTATION))) {
      return true;
    }

    if (!PsiClassUtil.isRunnableClass(psiClass, false, checkAbstract)) {
      return false;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module != null) {
      return LanguageCachedValueUtil.getCachedValue(psiClass, () ->
      {
        boolean hasAnnotation = false;
        for (final PsiMethod method : psiClass.getAllMethods()) {
          ProgressManager.checkCanceled();
          if (MetaAnnotationUtil.isMetaAnnotated(method, TEST5_ANNOTATIONS)) {
            hasAnnotation = true;
            break;
          }
        }

        if (!hasAnnotation) {
          for (PsiClass aClass : psiClass.getAllInnerClasses()) {
            if (MetaAnnotationUtil.isMetaAnnotated(aClass, Collections.singleton(JUNIT5_NESTED))) {
              hasAnnotation = true;
              break;
            }
          }
        }
        return CachedValueProvider.Result.create(hasAnnotation, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      });
    }

    return false;
  }

  public static boolean isJUnit5(@Nonnull PsiElement element) {
    return isJUnit5(element.getResolveScope(), element.getProject());
  }

  public static boolean isJUnit5(GlobalSearchScope scope, Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    Condition<String> foundCondition = aPackageName ->
    {
      PsiPackage aPackage = facade.findPackage(aPackageName);
      return aPackage != null && aPackage.getDirectories(scope).length > 0;
    };

    return ReadAction.compute(() -> foundCondition.value(TEST5_PACKAGE_FQN));
  }

  public static boolean isTestAnnotated(final PsiMethod method) {
    return AnnotationUtil.isAnnotated(method, TEST_ANNOTATION, 0)
      || JUnitRecognizer.willBeAnnotatedAfterCompilation(method)
      || MetaAnnotationUtil.isMetaAnnotated(method, TEST5_ANNOTATIONS);
  }

  @Nullable
  @RequiredReadAction
  private static PsiClass getTestCaseClassOrNull(final Location<?> location) {
    final Location<PsiClass> ancestorOrSelf = location.getAncestorOrSelf(PsiClass.class);
    if (ancestorOrSelf == null) {
      return null;
    }
    final PsiClass aClass = ancestorOrSelf.getPsiElement();
    Module module = JavaExecutionUtil.findModule(aClass);
    if (module == null) {
      return null;
    }
    GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, true);
    return getTestCaseClassOrNull(scope, module.getProject());
  }

  public static PsiClass getTestCaseClass(final Module module) throws NoJUnitException {
    if (module == null) {
      throw new NoJUnitException();
    }
    final GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, true);
    return getTestCaseClass(scope, module.getProject());
  }

  public static PsiClass getTestCaseClass(final SourceScope scope) throws NoJUnitException {
    if (scope == null) {
      throw new NoJUnitException();
    }
    return getTestCaseClass(scope.getLibrariesScope(), scope.getProject());
  }

  public static void checkTestCase(SourceScope scope, Project project) throws NoJUnitException {
    if (scope == null) {
      throw new NoJUnitException();
    }
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("junit.framework");
    if (aPackage == null || aPackage.getDirectories(scope.getLibrariesScope()).length == 0) {
      throw new NoJUnitException();
    }
  }

  private static PsiClass getTestCaseClass(final GlobalSearchScope scope, final Project project) throws NoJUnitException {
    PsiClass testCaseClass = getTestCaseClassOrNull(scope, project);
    if (testCaseClass == null) {
      throw new NoJUnitException(scope.getDisplayName());
    }
    return testCaseClass;
  }

  @Nullable
  private static PsiClass getTestCaseClassOrNull(final GlobalSearchScope scope, final Project project) {
    return JavaPsiFacade.getInstance(project).findClass(TEST_CASE_CLASS, scope);
  }

  public static boolean isTestMethodOrConfig(@Nonnull PsiMethod psiMethod) {
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    if (isTestMethod(PsiLocation.fromPsiElement(psiMethod), false)) {
      if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final boolean[] foundNonAbstractInheritor = new boolean[1];
        ClassInheritorsSearch.search(containingClass).forEach(psiClass ->
        {
          if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            foundNonAbstractInheritor[0] = true;
            return false;
          }
          return true;
        });
        if (foundNonAbstractInheritor[0]) {
          return true;
        }
      } else {
        return true;
      }
    }
    final String name = psiMethod.getName();
    final boolean isPublic = psiMethod.hasModifierProperty(PsiModifier.PUBLIC);
    if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (isPublic && (SUITE_METHOD_NAME.equals(name) || "setUp".equals(name) || "tearDown".equals(name))) {
        return true;
      }

      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_CONFIGS, 0)) {
          return isPublic;
        }
        if (AnnotationUtil.isAnnotated(psiMethod, STATIC_5_CONFIGS, 0)) {
          return true;
        }
      } else {
        if (AnnotationUtil.isAnnotated(psiMethod, INSTANCE_CONFIGS, 0)) {
          return isPublic;
        }
        if (AnnotationUtil.isAnnotated(psiMethod, INSTANCE_5_CONFIGS, 0)) {
          return true;
        }
        if (TestUtils.testInstancePerClass(containingClass) && AnnotationUtil.isAnnotated(psiMethod, STATIC_5_CONFIGS, 0)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static PsiMethod findFirstTestMethod(PsiClass clazz) {
    PsiMethod testMethod = null;
    for (PsiMethod method : clazz.getMethods()) {
      if (isTestMethod(MethodLocation.elementInClass(method, clazz)) || isSuiteMethod(method)) {
        testMethod = method;
        break;
      }
    }
    return testMethod;
  }

  @Nullable
  public static PsiMethod findSuiteMethod(PsiClass clazz) {
    final PsiMethod[] suiteMethods = clazz.findMethodsByName(SUITE_METHOD_NAME, false);
    for (PsiMethod method : suiteMethods) {
      if (isSuiteMethod(method)) {
        return method;
      }
    }
    return null;
  }

  public static PsiAnnotation getRunWithAnnotation(PsiClass aClass) {
    return AnnotationUtil.findAnnotationInHierarchy(aClass, Collections.singleton(RUN_WITH));
  }

  public static boolean isParameterized(PsiAnnotation annotation) {
    return isInheritorOrSelfRunner(annotation, "org.junit.runners.Parameterized");
  }

  public static boolean isInheritorOrSelfRunner(PsiAnnotation annotation, String... runners) {
    final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
    if (value instanceof PsiClassObjectAccessExpression classObjectAccessExpression) {
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();
      final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(operand.getType());
      return psiClass != null && Arrays.stream(runners).anyMatch(runner -> InheritanceUtil.isInheritor(psiClass, runner));
    }
    return false;
  }

  public static class TestMethodFilter implements Condition<PsiMethod> {
    private final PsiClass myClass;
    private final JavaTestFramework framework;

    public TestMethodFilter(final PsiClass aClass) {
      myClass = aClass;
      TestFramework framework = TestFrameworks.detectFramework(aClass);
      this.framework = framework instanceof JavaTestFramework javaTestFramework ? javaTestFramework : null;
    }

    public boolean value(final PsiMethod method) {
      return framework != null ? framework.isTestMethod(method, myClass) : isTestMethod(MethodLocation.elementInClass(method, myClass));
    }
  }

  public static PsiClass findPsiClass(final String qualifiedName, final Module module, final Project project) {
    final GlobalSearchScope scope = module == null ? GlobalSearchScope.projectScope(project) : GlobalSearchScope.moduleWithDependenciesScope(module);
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
  }

  public static PsiJavaPackage getContainingPackage(@Nonnull PsiClass psiClass) {
    PsiDirectory directory = psiClass.getContainingFile().getContainingDirectory();
    return directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
  }

  public static PsiClass getTestClass(final PsiElement element) {
    return getTestClass(PsiLocation.fromPsiElement(element));
  }

  public static PsiClass getTestClass(final Location<?> location) {
    for (Iterator<Location<PsiClass>> iterator = location.getAncestors(PsiClass.class, false); iterator.hasNext(); ) {
      final Location<PsiClass> classLocation = iterator.next();
      if (isTestClass(classLocation.getPsiElement(), false, true)) {
        return classLocation.getPsiElement();
      }
    }
    PsiElement element = location.getPsiElement();
    if (element instanceof PsiClassOwner classOwner) {
      PsiClass[] classes = classOwner.getClasses();
      if (classes.length == 1 && isTestClass(classes[0], false, true)) {
        return classes[0];
      }
    }
    return null;
  }

  public static PsiMethod getTestMethod(final PsiElement element) {
    return getTestMethod(element, true);
  }


  public static PsiMethod getTestMethod(final PsiElement element, boolean checkAbstract) {
    return getTestMethod(element, checkAbstract, true);
  }

  public static PsiMethod getTestMethod(final PsiElement element, boolean checkAbstract, boolean checkRunWith) {
    final PsiManager manager = element.getManager();
    final Location<PsiElement> location = PsiLocation.fromPsiElement(manager.getProject(), element);
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext(); ) {
      final Location<? extends PsiMethod> methodLocation = iterator.next();
      if (isTestMethod(methodLocation, checkAbstract, checkRunWith)) {
        return methodLocation.getPsiElement();
      }
    }
    return null;
  }

  public static class NoJUnitException extends CantRunException {
    public NoJUnitException() {
      super(ExecutionLocalize.noJunitErrorMessage().get());
    }

    public NoJUnitException(final String message) {
      super(ExecutionLocalize.noJunitInScopeErrorMessage(message).get());
    }
  }
}
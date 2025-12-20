/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.java.language.impl.psi.impl.JavaPsiFacadeEx;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.testFramework.PsiTestUtil;
import consulo.application.ApplicationManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import static org.junit.Assert.*;

/**
 *  @author dsl
 */
public abstract class TypesTest extends GenericsTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setupGenericSampleClasses();

    final String testPath = "/psi/types/" + getTestName(true);
    final VirtualFile[] testRoot = { null };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        testRoot[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(testPath);
      }
    });
    if (testRoot[0] != null) {
      PsiTestUtil.addSourceRoot(myModule, testRoot[0]);
    }
  }

  public void testSimpleStuff() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] methodStatements = method.getBody().getStatements();
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) methodStatements[0];
    PsiVariable varList = (PsiVariable) declarationStatement.getDeclaredElements()[0];
    PsiType typeListOfA = factory.createTypeFromText("test.List<java.lang.String>", null);
    assertEquals(varList.getType(), typeListOfA);
    PsiType typeListOfObject = factory.createTypeFromText("test.List<java.lang.Object>", null);
    assertFalse(varList.getType().equals(typeListOfObject));

    PsiReferenceExpression methodExpression
            = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[1]).getExpression()).getMethodExpression();
    JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof PsiMethod);
    PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
    PsiType typeOfFirstParameterOfAdd = methodFromList.getParameterList().getParameters()[0].getType();
    PsiType substitutedType = resolveResult.getSubstitutor().substitute(typeOfFirstParameterOfAdd);
    PsiClassType typeA = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING);
    assertEquals(typeA, substitutedType);
    assertTrue(typeA.equalsToText(CommonClassNames.JAVA_LANG_STRING));

    PsiType aListIteratorType = ((PsiExpressionStatement) methodStatements[2]).getExpression().getType();
    PsiType aIteratorType = factory.createTypeFromText("test.Iterator<java.lang.String>", null);
    assertEquals(aIteratorType, aListIteratorType);
    PsiType objectIteratorType = factory.createTypeFromText("test.Iterator<java.lang.Object>", null);
    assertFalse(objectIteratorType.equals(aListIteratorType));
  }

  public void testRawTypes() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] methodStatements = method.getBody().getStatements();
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) methodStatements[0];
    PsiVariable varList = (PsiVariable) declarationStatement.getDeclaredElements()[0];
    PsiType typeFromText = factory.createTypeFromText("test.List", null);
    assertEquals(varList.getType(), typeFromText);

    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[1]).getExpression()).getMethodExpression();
    JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof PsiMethod);
    PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
    PsiType typeOfFirstParameterOfAdd = methodFromList.getParameterList().getParameters()[0].getType();
    PsiType substitutedType = resolveResult.getSubstitutor().substitute(typeOfFirstParameterOfAdd);
    assertEquals(PsiType.getJavaLangObject(getPsiManager(), method.getResolveScope()), substitutedType);

    PsiType methodCallType = ((PsiExpressionStatement) methodStatements[2]).getExpression().getType();
    PsiType rawIteratorType = factory.createTypeFromText("test.Iterator", null);
    assertEquals(rawIteratorType, methodCallType);
  }

  public void testSubstWithInheritor() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] methodStatements = method.getBody().getStatements();
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) methodStatements[0];
    PsiVariable varList = (PsiVariable) declarationStatement.getDeclaredElements()[0];
    PsiType typeFromText = factory.createTypeFromText("test.IntList", null);
    assertEquals(varList.getType(), typeFromText);

    PsiReferenceExpression methodExpression
            = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[1]).getExpression()).getMethodExpression();
    JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof PsiMethod);
    PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
    PsiType typeOfFirstParameterOfAdd = methodFromList.getParameterList().getParameters()[0].getType();
    PsiType substitutedType = resolveResult.getSubstitutor().substitute(typeOfFirstParameterOfAdd);
    PsiType javaLangInteger = factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, null);
    assertEquals(javaLangInteger, substitutedType);

    PsiType intListIteratorReturnType = ((PsiExpressionStatement) methodStatements[2]).getExpression().getType();
    PsiType integerIteratorType = factory.createTypeFromText("test.Iterator<java.lang.Integer>", null);
    assertEquals(integerIteratorType, intListIteratorReturnType);
    PsiType objectIteratorType = factory.createTypeFromText("test.Iterator<java.lang.Object>", null);
    assertFalse(objectIteratorType.equals(integerIteratorType));
  }

  public void testSimpleRawTypeInMethodArg() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] methodStatements = method.getBody().getStatements();

    PsiVariable variable = (PsiVariable) ((PsiDeclarationStatement) methodStatements[0]).getDeclaredElements()[0];
    PsiClassType type = (PsiClassType) variable.getType();
    PsiClassType.ClassResolveResult resolveClassTypeResult = type.resolveGenerics();
    assertNotNull(resolveClassTypeResult.getElement());

    PsiReferenceExpression methodExpression
            = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[2]).getExpression()).getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    PsiClassType qualifierType = (PsiClassType) qualifierExpression.getType();
    assertFalse(qualifierType.hasParameters());
    PsiType typeFromText = factory.createTypeFromText("test.List", null);
    assertEquals(qualifierType, typeFromText);

    PsiElement psiElement = ((PsiReferenceExpression) qualifierExpression).resolve();
    assertTrue(psiElement instanceof PsiVariable);
    JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof PsiMethod);
    PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
    assertEquals("add", methodFromList.getName());
    assertEquals("test.List", methodFromList.getContainingClass().getQualifiedName());
  }



  public void testRawTypeInMethodArg() throws Exception {
    PsiClass classA = getJavaFacade().findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] methodStatements = method.getBody().getStatements();
    PsiReferenceExpression methodExpression
            = ((PsiMethodCallExpression) ((PsiExpressionStatement) methodStatements[2]).getExpression()).getMethodExpression();
    JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    assertTrue(resolveResult.getElement() instanceof PsiMethod);
    PsiMethod methodFromList = (PsiMethod) resolveResult.getElement();
    assertEquals("putAll", methodFromList.getName());
    assertEquals("test.List", methodFromList.getContainingClass().getQualifiedName());
  }

  public void testBoundedParams() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] statements = method.getBody().getStatements();

    PsiVariable var = (PsiVariable) ((PsiDeclarationStatement) statements[0]).getDeclaredElements()[0];
    PsiType varType = var.getType();
    PsiType typeRawIterator = factory.createTypeFromText("test.Iterator", null);
    assertEquals(varType, typeRawIterator);

    PsiType initializerType = var.getInitializer().getType();
    assertEquals(initializerType, typeRawIterator);
    assertTrue(varType.isAssignableFrom(initializerType));
  }

  public void testRawTypeExtension() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.getMethods()[0];
    PsiStatement[] statements = method.getBody().getStatements();

    PsiVariable var = (PsiVariable) ((PsiDeclarationStatement) statements[0]).getDeclaredElements()[0];
    PsiType varType = var.getType();
    PsiType typeRawIterator = factory.createTypeFromText("test.Iterator", null);
    assertEquals(varType, typeRawIterator);

    PsiType initializerType = var.getInitializer().getType();
    assertEquals(initializerType, typeRawIterator);
    assertTrue(varType.isAssignableFrom(initializerType));
  }

  public void testTypesInGenericClass() {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiTypeParameter parameterT = classA.getTypeParameters()[0];
    assertEquals("T", parameterT.getName());

    PsiMethod method = classA.findMethodsByName("method", false)[0];
    PsiType type = ((PsiExpressionStatement) method.getBody().getStatements()[0]).getExpression().getType();
    PsiClassType typeT = factory.createType(parameterT);
    assertEquals("T", typeT.getPresentableText());

    assertEquals(typeT, type);
  }

  public void testAssignableSubInheritor() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classCollection = psiManager.findClass("test.Collection");
    PsiClass classList = psiManager.findClass("test.List");
    PsiType collectionType = factory.createType(classCollection, PsiSubstitutor.EMPTY);
    PsiType listType = factory.createType(classList, PsiSubstitutor.EMPTY);
    assertEquals(collectionType.getCanonicalText(), "test.Collection<E>");
    assertEquals(listType.getCanonicalText(), "test.List<T>");

    PsiType typeListOfString = factory.createTypeFromText("test.List<java.lang.String>", null);
    PsiType typeCollectionOfString = factory.createTypeFromText("test.Collection<java.lang.String>", null);
    assertTrue(typeCollectionOfString.isAssignableFrom(typeListOfString));
  }

  public void testComplexInheritance() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.findMethodsByName("method", false)[0];
    PsiExpression expression = ((PsiExpressionStatement) method.getBody().getStatements()[1]).getExpression();
    assertEquals("l.get(0)", expression.getText());

    PsiType type = expression.getType();
    PsiType listOfInteger = factory.createTypeFromText("test.List<java.lang.Integer>", null);
    assertEquals(listOfInteger, type);
    PsiType collectionOfInteger = factory.createTypeFromText("test.Collection<java.lang.Integer>", null);
    assertTrue(collectionOfInteger.isAssignableFrom(type));
  }

  public void testListListInheritance() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.findMethodsByName("method", false)[0];

    PsiExpression expression1 = ((PsiExpressionStatement) method.getBody().getStatements()[1]).getExpression();
    assertEquals("l.get(0)", expression1.getText());
    PsiType type1 = expression1.getType();
    PsiType typeListOfInteger = factory.createTypeFromText("test.List<java.lang.Integer>", null);
    assertEquals(typeListOfInteger, type1);
    assertTrue(typeListOfInteger.isAssignableFrom(type1));

    PsiExpression expression2 = ((PsiExpressionStatement) method.getBody().getStatements()[3]).getExpression();
    assertEquals("b.get(0)", expression2.getText());
    PsiType type2 = expression2.getType();
    assertEquals(typeListOfInteger, type2);
  }

  public void testSpaceInTypeParameterList() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.findMethodsByName("method", false)[0];

    PsiVariable variable = (PsiVariable) ((PsiDeclarationStatement) method.getBody().getStatements()[0]).getDeclaredElements()[0];
    PsiType type = variable.getType();
    PsiType typeListOfListOfInteger = factory.createTypeFromText("test.List<test.List<java.lang.Integer>>", null);
    assertEquals(typeListOfListOfInteger, type);
  }

  public void testMethodTypeParameter() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.findMethodsByName("method", false)[0];
    PsiStatement[] statements = method.getBody().getStatements();

    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[1]).getExpression();
    isCollectionUtilSort(methodCallExpression, factory.createTypeFromText(CommonClassNames.JAVA_LANG_INTEGER, null));

    PsiMethodCallExpression methodCallExpression1 = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[3]).getExpression();
    isCollectionUtilSort(methodCallExpression1, null);
  }

  private static void isCollectionUtilSort(PsiMethodCallExpression methodCallExpression,
                                           PsiType typeParameterValue) {
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    JavaResolveResult methodResolve = methodExpression.advancedResolve(false);
    assertTrue(methodResolve.getElement() instanceof PsiMethod);
    PsiMethod methodSort = (PsiMethod) methodResolve.getElement();
    assertEquals("sort", methodSort.getName());
    assertEquals("test.CollectionUtil", methodSort.getContainingClass().getQualifiedName());
    PsiTypeParameter methodSortTypeParameter = methodSort.getTypeParameters()[0];
    PsiType sortParameterActualType = methodResolve.getSubstitutor().substitute(methodSortTypeParameter);
    assertTrue(Comparing.equal(sortParameterActualType, typeParameterValue));
    assertTrue(
            PsiUtil.isApplicable(methodSort, methodResolve.getSubstitutor(), methodCallExpression.getArgumentList()));
  }

  public void testRawArrayTypes() throws Exception {
    JavaPsiFacadeEx psiManager = getJavaFacade();
    PsiElementFactory factory = psiManager.getElementFactory();
    PsiClass classA = psiManager.findClass("A");
    assertNotNull(classA);

    PsiMethod method = classA.findMethodsByName("method", false)[0];
    PsiStatement[] statements = method.getBody().getStatements();

    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) statements[0];
    PsiClassType typeOfL = (PsiClassType) ((PsiVariable) declarationStatement.getDeclaredElements()[0]).getType();
    PsiType typeRawList = factory.createTypeFromText("test.List", null);
    assertTrue(Comparing.equal(typeOfL, typeRawList));
    PsiSubstitutor typeOfLSubstitutor = typeOfL.resolveGenerics().getSubstitutor();

    PsiMethodCallExpression exprGetArray = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[1]).getExpression();
    PsiType typeOfGetArrayCall = exprGetArray.getType();
    PsiType objectArrayType = factory.createTypeFromText("java.lang.Object[]", null);
    assertTrue(Comparing.equal(typeOfGetArrayCall, objectArrayType));
    PsiMethod methodGetArray = (PsiMethod) exprGetArray.getMethodExpression().resolve();
    PsiType subtitutedGetArrayReturnType = typeOfLSubstitutor.substitute(methodGetArray.getReturnType());
    assertTrue(Comparing.equal(subtitutedGetArrayReturnType, objectArrayType));


    PsiMethodCallExpression exprGetListOfArray = (PsiMethodCallExpression) ((PsiExpressionStatement) statements[2]).getExpression();
    PsiMethod methodGetListOfArray = (PsiMethod) exprGetListOfArray.getMethodExpression().resolve();
    PsiType returnType = methodGetListOfArray.getReturnType();
    PsiType substitutedReturnType = typeOfLSubstitutor.substitute(returnType);
    assertTrue(Comparing.equal(substitutedReturnType, typeRawList));

    PsiType typeOfGetListOfArrayCall = exprGetListOfArray.getType();
    assertTrue(Comparing.equal(typeOfGetListOfArrayCall, typeRawList));
  }

  public void testWildcardTypeParsing() throws Exception{
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule);
    PsiClassType javaLangObject = PsiType.getJavaLangObject(myPsiManager, scope);

    PsiElement element = ((PsiDeclarationStatement)myJavaFacade.getElementFactory().createStatementFromText("X<? extends Y, ? super Z<A,B>, ?> x;", null)).getDeclaredElements()[0];
    PsiJavaCodeReferenceElement referenceElement = ((PsiVariable) element).getTypeElement().getInnermostComponentReferenceElement();
    PsiType[] typeArguments = referenceElement.getTypeParameters();
    assertEquals(3, typeArguments.length);
    assertTrue(typeArguments[0] instanceof PsiWildcardType);
    assertTrue(typeArguments[1] instanceof PsiWildcardType);
    assertTrue(typeArguments[2] instanceof PsiWildcardType);
    PsiWildcardType extendsWildcard = (PsiWildcardType)typeArguments[0];
    PsiWildcardType superWildcard = (PsiWildcardType)typeArguments[1];
    PsiWildcardType unboundedWildcard = (PsiWildcardType)typeArguments[2];

    // extends wildcard test
    assertTrue(extendsWildcard.isExtends());
    assertFalse(extendsWildcard.isSuper());
    assertEquals("Y", extendsWildcard.getBound().getCanonicalText());
    assertEquals("Y", extendsWildcard.getExtendsBound().getCanonicalText());
    assertEquals(extendsWildcard.getSuperBound(), PsiType.NULL);

    // super wildcard test
    assertFalse(superWildcard.isExtends());
    assertTrue(superWildcard.isSuper());
    assertEquals("Z<A,B>", superWildcard.getBound().getCanonicalText());
    assertEquals(superWildcard.getExtendsBound(), javaLangObject);
    assertEquals("Z<A,B>", superWildcard.getSuperBound().getCanonicalText());

    // unbounded wildcard test
    assertFalse(unboundedWildcard.isExtends());
    assertFalse(unboundedWildcard.isSuper());
    assertNull(unboundedWildcard.getBound());
    assertEquals(unboundedWildcard.getExtendsBound(), javaLangObject);
    assertEquals(unboundedWildcard.getSuperBound(), PsiType.NULL);
  }

  public void testWildcardTypesAssignable() throws Exception {
    PsiClassType listOfExtendsBase = (PsiClassType)myJavaFacade.getElementFactory().createTypeFromText("test.List<? extends usages.Base>", null);
    PsiClassType.ClassResolveResult classResolveResult = listOfExtendsBase.resolveGenerics();
    PsiClass listClass = classResolveResult.getElement();
    assertNotNull(listClass);
    PsiTypeParameter listTypeParameter = PsiUtil.typeParametersIterator(listClass).next();
    PsiType listParameterTypeValue = classResolveResult.getSubstitutor().substitute(listTypeParameter);
    assertTrue(listParameterTypeValue instanceof PsiWildcardType);
    assertTrue(((PsiWildcardType)listParameterTypeValue).isExtends());
    assertEquals("usages.Base", ((PsiWildcardType)listParameterTypeValue).getBound().getCanonicalText());
    PsiClassType listOfIntermediate = (PsiClassType)myJavaFacade.getElementFactory().createTypeFromText("test.List<usages.Intermediate>", null);
    assertNotNull(listOfIntermediate.resolve());
    assertTrue(listOfExtendsBase.isAssignableFrom(listOfIntermediate));
  }

  public void testEllipsisType() throws Exception {
    PsiElementFactory factory = myJavaFacade.getElementFactory();
    PsiMethod method = factory.createMethodFromText("void foo (int ... args) {}", null);
    PsiType paramType = method.getParameterList().getParameters()[0].getType();
    assertTrue(paramType instanceof PsiEllipsisType);
    PsiType arrayType = ((PsiEllipsisType)paramType).getComponentType().createArrayType();
    assertTrue(paramType.isAssignableFrom(arrayType));
    assertTrue(arrayType.isAssignableFrom(paramType));

    PsiType typeFromText = factory.createTypeFromText("int ...", null);
    assertTrue(typeFromText instanceof PsiEllipsisType);
  }

  public void testBinaryNumericPromotion() throws Exception {
    PsiElementFactory factory = myJavaFacade.getElementFactory();
    PsiExpression conditional = factory.createExpressionFromText("b ? new Integer (0) : new Double(0.0)", null);
    assertEquals(PsiType.DOUBLE, conditional.getType());
    PsiExpression shift = factory.createExpressionFromText("Integer.valueOf(0) << 2", null);
    assertEquals(PsiType.INT, shift.getType());
  }

  public void testUnaryExpressionType() throws Exception {
    PsiElementFactory factory = myJavaFacade.getElementFactory();
    PsiExpression plusPrefix = factory.createExpressionFromText("+Integer.valueOf(1)", null);
    assertEquals(PsiType.INT, plusPrefix.getType());
    PsiExpression plusBytePrefix = factory.createExpressionFromText("+Byte.valueOf(1)", null);
    assertEquals(PsiType.INT, plusBytePrefix.getType());
    PsiStatement declaration = factory.createStatementFromText("Byte b = 1;", null);
    PsiExpression plusPlusPostfix = factory.createExpressionFromText("b++", declaration);
    assertEquals(PsiType.BYTE.getBoxedType(declaration), plusPlusPostfix.getType());
  }
}

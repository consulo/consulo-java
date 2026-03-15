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
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.lexer.JavaLexer;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.psi.impl.light.*;
import com.intellij.java.language.impl.psi.impl.source.JavaDummyElement;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ServiceImpl;
import consulo.java.language.psi.JavaLanguageVersion;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.impl.ast.FileElement;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.impl.psi.DummyHolderFactory;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
@ServiceImpl
public class PsiElementFactoryImpl extends PsiJavaParserFacadeImpl implements PsiElementFactory {
  private PsiClass myArrayClass;
  private PsiClass myArrayClass15;

  @Inject
  public PsiElementFactoryImpl(final PsiManager manager) {
    super(manager);
  }

  @Override
  public PsiClass getArrayClass(final LanguageLevel languageLevel) {
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
      if (myArrayClass == null) {
        final String body = "public class __Array__{\n public final int length;\n public Object clone() {}\n}";
        myArrayClass = createClassFromText(body, null).getInnerClasses()[0];
      }
      return myArrayClass;
    } else {
      if (myArrayClass15 == null) {
        final String body = "public class __Array__<T>{\n public final int length;\n public T[] clone() {}\n}";
        myArrayClass15 = createClassFromText(body, null).getInnerClasses()[0];
      }
      return myArrayClass15;
    }
  }

  @Override
  public PsiClassType getArrayClassType(final PsiType componentType, final LanguageLevel languageLevel) {
    final PsiClass arrayClass = getArrayClass(languageLevel);
    final PsiTypeParameter[] typeParameters = arrayClass.getTypeParameters();

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], componentType);
    }

    return createType(arrayClass, substitutor);
  }

  @Override
  public PsiClassType createType(PsiClass resolve, PsiSubstitutor substitutor) {
    return new PsiImmediateClassType(resolve, substitutor);
  }

  @Override
  public PsiClassType createType(PsiClass resolve, PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel);
  }

  @Override
  public PsiClassType createType(PsiClass resolve, PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel, PsiAnnotation[] annotations) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel, annotations);
  }

  @Override
  public PsiClass createClass(final String name) throws IncorrectOperationException {
    return createClassInner("class", name);
  }

  @Override
  public PsiClass createInterface(final String name) throws IncorrectOperationException {
    return createClassInner("interface", name);
  }

  @Override
  public PsiClass createEnum(final String name) throws IncorrectOperationException {
    return createClassInner("enum", name);
  }

  @Override
  public PsiClass createAnnotationType(String name) throws IncorrectOperationException {
    return createClassInner("@interface", name);
  }

  private PsiClass createClassInner(final String type, String name) {
    PsiUtil.checkIsIdentifier(myManager, name);
    final PsiJavaFile aFile = createDummyJavaFile("public " + type + " " + name + " { }");
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect " + type + " name \"" + name + "\".");
    }
    return classes[0];
  }

  @Override
  public PsiTypeElement createTypeElement(final PsiType psiType) {
    final LightTypeElement element = new LightTypeElement(myManager, psiType);
    CodeEditUtil.setNodeGenerated(element.getNode(), true);
    return element;
  }

  @Override
  public PsiJavaCodeReferenceElement createReferenceElementByType(final PsiClassType type) {
    return type instanceof PsiClassReferenceType ? ((PsiClassReferenceType) type).getReference() : new LightClassTypeReference(myManager, type);
  }

  @Override
  public PsiTypeParameterList createTypeParameterList() {
    final PsiTypeParameterList parameterList = createMethodFromText("void foo()", null).getTypeParameterList();
    assert parameterList != null;
    return parameterList;
  }

  @Override
  public PsiTypeParameter createTypeParameter(String name, PsiClassType[] superTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append("public <").append(name);
    if (superTypes.length > 1 || superTypes.length == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      builder.append(" extends ");
      for (PsiClassType type : superTypes) {
        if (!type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          builder.append(type.getCanonicalText(true)).append('&');
        }
      }
      builder.delete(builder.length() - 1, builder.length());
    }
    builder.append("> void foo(){}");
    try {
      return createMethodFromText(builder.toString(), null).getTypeParameters()[0];
    }
    catch (RuntimeException e) {
      throw new IncorrectOperationException("type parameter text: " + builder, (Throwable)e);
    }
  }

  @Override
  public PsiField createField(final String name, final PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create field with type \"null\".");
    }

    String text =
      "class _Dummy_ { private " + GenericsUtil.getVariableTypeByExpressionType(type).getCanonicalText(true) + " " + name + "; }";
    final PsiJavaFile aFile = createDummyJavaFile(text);
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length < 1) {
      throw new IncorrectOperationException("Class was not created " + text);
    }
    final PsiClass psiClass = classes[0];
    final PsiField[] fields = psiClass.getFields();
    if (fields.length < 1) {
      throw new IncorrectOperationException("Field was not created " + text);
    }
    PsiField field = fields[0];
    field = (PsiField) JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(field);
    return (PsiField) CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
  }

  @Override
  public PsiMethod createMethod(final String name, final PsiType returnType) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(returnType)) {
      throw new IncorrectOperationException("Cannot create method with type \"null\".");
    }

    String canonicalText = GenericsUtil.getVariableTypeByExpressionType(returnType).getCanonicalText(true);
    final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { public " + canonicalText + " " + name + "() {} " +
        "}");
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length < 1) {
      throw new IncorrectOperationException("Class was not created. Method name: " + name + "; return type: " +
          canonicalText);
    }
    final PsiMethod[] methods = classes[0].getMethods();
    if (methods.length < 1) {
      throw new IncorrectOperationException("Method was not created. Method name: " + name + "; return type: " +
          canonicalText);
    }
    PsiMethod method = methods[0];
    method = (PsiMethod) JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(method);
    return (PsiMethod) CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @Override
  public PsiMethod createMethod(String name, PsiType returnType, PsiElement context) throws IncorrectOperationException {
    return createMethodFromText("public " + GenericsUtil.getVariableTypeByExpressionType(returnType)
                                                        .getCanonicalText(true) + " " + name + "() {}", context);
  }

  @Override
  public PsiMethod createConstructor() {
    return createConstructor("_Dummy_");
  }

  @Override
  public PsiMethod createConstructor(final String name) {
    final PsiJavaFile aFile = createDummyJavaFile("class " + name + " { public " + name + "() {} }");
    final PsiMethod method = aFile.getClasses()[0].getMethods()[0];
    return (PsiMethod) CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @Override
  public PsiMethod createConstructor(String name, PsiElement context) {
    return createMethodFromText(name + "() {}", context);
  }

  @Override
  public PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { {} }");
    final PsiClassInitializer classInitializer = aFile.getClasses()[0].getInitializers()[0];
    return (PsiClassInitializer) CodeStyleManager.getInstance(myManager.getProject()).reformat(classInitializer);
  }

  @Override
  public PsiParameter createParameter(final String name, final PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create parameter with type \"null\".");
    }

    final String text = type.getCanonicalText() + " " + name;
    PsiParameter parameter = createParameterFromText(text, null);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalParameters());
    CodeEditUtil.markGenerated(parameter.getNode());
    parameter = (PsiParameter) JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(parameter);
    return (PsiParameter) codeStyleManager.reformat(parameter);
  }

  @Override
  public PsiParameter createParameter(String name, PsiType type, PsiElement context) throws IncorrectOperationException {
    final PsiMethod psiMethod = createMethodFromText("void f(" + type.getCanonicalText() + " " + name + ") {}", context);
    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    return parameters[0];
  }

  @Override
  public PsiCodeBlock createCodeBlock() {
    final PsiCodeBlock block = createCodeBlockFromText("{}", null);
    return (PsiCodeBlock) CodeStyleManager.getInstance(myManager.getProject()).reformat(block);
  }

  @Override
  public PsiClassType createType(final PsiClass aClass) {
    return new PsiImmediateClassType(aClass, aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : createRawSubstitutor(aClass));
  }

  @Override
  public PsiClassType createType(final PsiJavaCodeReferenceElement classReference) {
    return new PsiClassReferenceType(classReference, null);
  }

  @Override
  public PsiClassType createType(final PsiClass aClass, final PsiType parameter) {
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    assert typeParameters.length == 1 : aClass;

    return createType(aClass, PsiSubstitutor.EMPTY.put(typeParameters[0], parameter));
  }

  @Override
  public PsiClassType createType(final PsiClass aClass, final PsiType... parameters) {
    return createType(aClass, PsiSubstitutor.EMPTY.putAll(aClass, parameters));
  }

  @Override
  public PsiSubstitutor createRawSubstitutor(final PsiTypeParameterListOwner owner) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(owner)) {
      if (substitutorMap == null) {
        substitutorMap = new HashMap<PsiTypeParameter, PsiType>();
      }
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substitutorMap);
  }

  @Override
  public PsiSubstitutor createRawSubstitutor(final PsiSubstitutor baseSubstitutor, final PsiTypeParameter[] typeParameters) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : typeParameters) {
      if (substitutorMap == null) {
        substitutorMap = new HashMap<PsiTypeParameter, PsiType>();
      }
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substitutorMap).putAll(baseSubstitutor);
  }

  @Override
  public PsiElement createDummyHolder(final String text, final IElementType type, @Nullable final PsiElement context) {
    final DummyHolder result = DummyHolderFactory.createHolder(myManager, context);
    final FileElement holder = result.getTreeElement();
    final Language language = type.getLanguage();
    final ParserDefinition parserDefinition = ParserDefinition.forLanguage(language);
    assert parserDefinition != null : "No parser definition for language " + language;
    final Project project = myManager.getProject();
    JavaLanguageVersion highestLangVersion = LanguageLevel.HIGHEST.toLangVersion();
    final Lexer lexer = parserDefinition.createLexer(highestLangVersion);
    final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, holder, lexer, language, highestLangVersion, text);
    final ASTNode node = parserDefinition.createParser(highestLangVersion).parse(type, builder, highestLangVersion);
    holder.rawAddChildren((TreeElement) node);
    final PsiElement psi = node.getPsi();
    assert psi != null : text;
    return psi;
  }

  @Override
  public PsiSubstitutor createSubstitutor(final Map<PsiTypeParameter, PsiType> map) {
    return PsiSubstitutorImpl.createSubstitutor(map);
  }

  @Nullable
  @Override
  public PsiPrimitiveType createPrimitiveType(final String text) {
    return PsiJavaParserFacadeImpl.getPrimitiveType(text);
  }

  @Override
  public PsiClassType createTypeByFQClassName(final String qName) {
    return createTypeByFQClassName(qName, GlobalSearchScope.allScope(myManager.getProject()));
  }

  @Override
  public PsiClassType createTypeByFQClassName(final String qName, final GlobalSearchScope resolveScope) {
    return new PsiClassReferenceType(createReferenceElementByFQClassName(qName, resolveScope), null);
  }

  @Override
  public boolean isValidClassName(String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidMethodName(String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidParameterName(String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidFieldName(String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidLocalVariableName(String name) {
    return isIdentifier(name);
  }

  private boolean isIdentifier(String name) {
    return PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(name);
  }

  @Override
  public PsiJavaCodeReferenceElement createClassReferenceElement(final PsiClass aClass) {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass) aClass).getBaseClassType().getPresentableText();
    } else {
      text = aClass.getName();
    }
    return new LightClassReference(myManager, text, aClass);
  }

  @Override
  public PsiJavaCodeReferenceElement createReferenceElementByFQClassName(final String qName, final GlobalSearchScope resolveScope) {
    final String shortName = PsiNameHelper.getShortClassName(qName);
    return new LightClassReference(myManager, shortName, qName, resolveScope);
  }

  @Override
  public PsiJavaCodeReferenceElement createFQClassNameReferenceElement(final String qName, final GlobalSearchScope resolveScope) {
    return new LightClassReference(myManager, qName, qName, resolveScope);
  }

  @Override
  public PsiJavaCodeReferenceElement createPackageReferenceElement(final PsiJavaPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, aPackage);
  }

  @Override
  public PsiPackageStatement createPackageStatement(final String name) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile("package " + name + ";");
    final PsiPackageStatement stmt = aFile.getPackageStatement();
    if (stmt == null) {
      throw new IncorrectOperationException("Incorrect package name: " + name);
    }
    return stmt;
  }

  @Override
  public PsiImportStaticStatement createImportStaticStatement(final PsiClass aClass, final String memberName) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    } else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }

    final PsiJavaFile aFile = createDummyJavaFile("import static " + aClass.getQualifiedName() + "." + memberName + ";");
    final PsiImportStatementBase statement = extractImport(aFile, true);
    return (PsiImportStaticStatement) CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @Override
  public PsiParameterList createParameterList(final String[] names, final PsiType[] types) throws IncorrectOperationException {
    final StringBuilder builder = new StringBuilder();
    builder.append("void method(");
    for (int i = 0; i < names.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(types[i].getCanonicalText()).append(' ').append(names[i]);
    }
    builder.append(");");
    return createMethodFromText(builder.toString(), null).getParameterList();
  }

  @Override
  public PsiReferenceList createReferenceList(final PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException {
    final StringBuilder builder = new StringBuilder();
    builder.append("void method()");
    if (references.length > 0) {
      builder.append(" throws ");
      for (int i = 0; i < references.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(references[i].getCanonicalText());
      }
    }
    builder.append(';');
    return createMethodFromText(builder.toString(), null).getThrowsList();
  }

  @Override
  public PsiJavaCodeReferenceElement createPackageReferenceElement(final String packageName) throws IncorrectOperationException {
    if (packageName.isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, packageName);
  }

  @Override
  public PsiReferenceExpression createReferenceExpression(final PsiClass aClass) throws IncorrectOperationException {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass) aClass).getBaseClassType().getPresentableText();
    } else {
      text = aClass.getName();
    }
    return new LightClassReferenceExpression(myManager, text, aClass);
  }

  @Override
  public PsiReferenceExpression createReferenceExpression(final PsiJavaPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReferenceExpression(myManager, aPackage);
  }

  @Override
  public PsiIdentifier createIdentifier(final String text) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, text);
    return new LightIdentifier(myManager, text);
  }

  @Override
  public PsiKeyword createKeyword(final String text) throws IncorrectOperationException {
    if (!PsiNameHelper.getInstance(myManager.getProject()).isKeyword(text)) {
      throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, text);
  }

  @Override
  public PsiKeyword createKeyword(String keyword, PsiElement context) throws IncorrectOperationException {
    LanguageLevel level = PsiUtil.getLanguageLevel(context);
    if (!JavaLexer.isKeyword(keyword, level) && !JavaLexer.isSoftKeyword(keyword, level)) {
      throw new IncorrectOperationException("\"" + keyword + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, keyword);
  }

  @Override
  public PsiImportStatement createImportStatement(final PsiClass aClass) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    } else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }

    final PsiJavaFile aFile = createDummyJavaFile("import " + aClass.getQualifiedName() + ";");
    final PsiImportStatementBase statement = extractImport(aFile, false);
    return (PsiImportStatement) CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @Override
  public PsiImportStatement createImportStatementOnDemand(final String packageName) throws IncorrectOperationException {
    if (packageName.isEmpty()) {
      throw new IncorrectOperationException("Cannot create import statement for default package.");
    }
    if (!PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName)) {
      throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
    }

    final PsiJavaFile aFile = createDummyJavaFile("import " + packageName + ".*;");
    final PsiImportStatementBase statement = extractImport(aFile, false);
    return (PsiImportStatement) CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @Override
  public PsiDeclarationStatement createVariableDeclarationStatement(final String name, final PsiType type, final PsiExpression initializer) throws IncorrectOperationException {
    if (!PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(name)) {
      throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
    }
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create variable with type \"null\".");
    }

    final String text = "X " + name + (initializer != null ? " = x" : "") + ";";

    final PsiDeclarationStatement statement = (PsiDeclarationStatement) createStatementFromText(text, null);
    final PsiVariable variable = (PsiVariable) statement.getDeclaredElements()[0];
    replace(variable.getTypeElement(), createTypeElement(type), text);
    PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalLocals());
    if (initializer != null) {
      replace(variable.getInitializer(), initializer, text);
    }
    CodeEditUtil.markGenerated(statement.getNode());
    return statement;
  }

  @Override
  public PsiDocTag createParamTag(final String parameterName, final String description) throws IncorrectOperationException {
    final StringBuilder builder = new StringBuilder();
    builder.append(" * @param ");
    builder.append(parameterName);
    builder.append(" ");
    final String[] strings = description.split("\\n");
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) {
        builder.append("\n * ");
      }
      builder.append(strings[i]);
    }
    return createDocTagFromText(builder.toString());
  }

  @Override
  public PsiAnnotation createAnnotationFromText(final String annotationText, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    CodeEditUtil.markGenerated(psiAnnotation.getNode());
    return psiAnnotation;
  }

  @Override
  public PsiCodeBlock createCodeBlockFromText(final CharSequence text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiCodeBlock psiCodeBlock = super.createCodeBlockFromText(text, context);
    CodeEditUtil.markGenerated(psiCodeBlock.getNode());
    return psiCodeBlock;
  }

  @Override
  public PsiEnumConstant createEnumConstantFromText(final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiEnumConstant enumConstant = super.createEnumConstantFromText(text, context);
    CodeEditUtil.markGenerated(enumConstant.getNode());
    return enumConstant;
  }

  @Override
  public PsiExpression createExpressionFromText(final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiExpression expression = super.createExpressionFromText(text, context);
    CodeEditUtil.markGenerated(expression.getNode());
    return expression;
  }

  @Override
  public PsiField createFieldFromText(final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiField psiField = super.createFieldFromText(text, context);
    CodeEditUtil.markGenerated(psiField.getNode());
    return psiField;
  }

  @Override
  public PsiParameter createParameterFromText(final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiParameter parameter = super.createParameterFromText(text, context);
    CodeEditUtil.markGenerated(parameter.getNode());
    return parameter;
  }

  @Override
  public PsiStatement createStatementFromText(final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiStatement statement = super.createStatementFromText(text, context);
    CodeEditUtil.markGenerated(statement.getNode());
    return statement;
  }

  @Override
  public PsiType createTypeFromText(final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, true);
  }

  @Override
  public PsiTypeParameter createTypeParameterFromText(final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiTypeParameter typeParameter = super.createTypeParameterFromText(text, context);
    CodeEditUtil.markGenerated(typeParameter.getNode());
    return typeParameter;
  }

  @Override
  public PsiMethod createMethodFromText(final String text, final PsiElement context, final LanguageLevel level) throws IncorrectOperationException {
    final PsiMethod method = super.createMethodFromText(text, context, level);
    CodeEditUtil.markGenerated(method.getNode());
    return method;
  }

  private static PsiImportStatementBase extractImport(final PsiJavaFile aFile, final boolean isStatic) {
    final PsiImportList importList = aFile.getImportList();
    assert importList != null : aFile;
    final PsiImportStatementBase[] statements = isStatic ? importList.getImportStaticStatements() : importList.getImportStatements();
    assert statements.length == 1 : aFile.getText();
    return statements[0];
  }

  private static void replace(final PsiElement original, final PsiElement replacement, final String message) {
    assert original != null : message;
    original.replace(replacement);
  }

  private static final JavaParserUtil.ParserWrapper CATCH_SECTION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getStatementParser().parseCatchBlock(builder);
    }
  };

  @Override
  public PsiCatchSection createCatchSection(final PsiType exceptionType,
                                            final String exceptionName,
                                            @Nullable final PsiElement context) throws IncorrectOperationException {
    if (!(exceptionType instanceof PsiClassType || exceptionType instanceof PsiDisjunctionType)) {
      throw new IncorrectOperationException("Unexpected type:" + exceptionType);
    }

    final String text = "catch (" + exceptionType.getCanonicalText(true) + " " + exceptionName + ") {}";
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CATCH_SECTION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCatchSection)) {
      throw new IncorrectOperationException("Incorrect catch section '" + text + "'. Parsed element: " + element);
    }

    final Project project = myManager.getProject();
    final JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(project);
    helper.setupCatchBlock(exceptionName, context, (PsiCatchSection) element);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    final PsiCatchSection catchSection = (PsiCatchSection) styleManager.reformat(element);

    CodeEditUtil.markGenerated(catchSection.getNode());
    return catchSection;
  }
}

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
package com.intellij.psi.impl;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jetbrains.annotations.NonNls;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.light.LightClassReferenceExpression;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightKeyword;
import com.intellij.psi.impl.light.LightPackageReference;
import com.intellij.psi.impl.light.LightPackageReferenceExpression;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import consulo.java.module.util.JavaClassNames;
import consulo.java.psi.JavaLanguageVersion;

@Singleton
public class PsiElementFactoryImpl extends PsiJavaParserFacadeImpl implements PsiElementFactory
{
	private PsiClass myArrayClass;
	private PsiClass myArrayClass15;

	@Inject
	public PsiElementFactoryImpl(final PsiManager manager)
	{
		super(manager);
	}

	@Nonnull
	@Override
	public PsiClass getArrayClass(@Nonnull final LanguageLevel languageLevel)
	{
		if(!languageLevel.isAtLeast(LanguageLevel.JDK_1_5))
		{
			if(myArrayClass == null)
			{
				final String body = "public class __Array__{\n public final int length;\n public Object clone() {}\n}";
				myArrayClass = createClassFromText(body, null).getInnerClasses()[0];
			}
			return myArrayClass;
		}
		else
		{
			if(myArrayClass15 == null)
			{
				final String body = "public class __Array__<T>{\n public final int length;\n public T[] clone() {}\n}";
				myArrayClass15 = createClassFromText(body, null).getInnerClasses()[0];
			}
			return myArrayClass15;
		}
	}

	@Nonnull
	@Override
	public PsiClassType getArrayClassType(@Nonnull final PsiType componentType, @Nonnull final LanguageLevel languageLevel)
	{
		final PsiClass arrayClass = getArrayClass(languageLevel);
		final PsiTypeParameter[] typeParameters = arrayClass.getTypeParameters();

		PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
		if(typeParameters.length == 1)
		{
			substitutor = substitutor.put(typeParameters[0], componentType);
		}

		return createType(arrayClass, substitutor);
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull PsiClass resolve, @Nonnull PsiSubstitutor substitutor)
	{
		return new PsiImmediateClassType(resolve, substitutor);
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull PsiClass resolve, @Nonnull PsiSubstitutor substitutor, @javax.annotation.Nullable LanguageLevel languageLevel)
	{
		return new PsiImmediateClassType(resolve, substitutor, languageLevel);
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull PsiClass resolve, @Nonnull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel, @Nonnull PsiAnnotation[] annotations)
	{
		return new PsiImmediateClassType(resolve, substitutor, languageLevel, annotations);
	}

	@Nonnull
	@Override
	public PsiClass createClass(@Nonnull final String name) throws IncorrectOperationException
	{
		return createClassInner("class", name);
	}

	@Nonnull
	@Override
	public PsiClass createInterface(@Nonnull final String name) throws IncorrectOperationException
	{
		return createClassInner("interface", name);
	}

	@Nonnull
	@Override
	public PsiClass createEnum(@Nonnull final String name) throws IncorrectOperationException
	{
		return createClassInner("enum", name);
	}

	@Nonnull
	@Override
	public PsiClass createAnnotationType(@Nonnull @NonNls String name) throws IncorrectOperationException
	{
		return createClassInner("@interface", name);
	}

	private PsiClass createClassInner(@NonNls final String type, @NonNls String name)
	{
		PsiUtil.checkIsIdentifier(myManager, name);
		final PsiJavaFile aFile = createDummyJavaFile("public " + type + " " + name + " { }");
		final PsiClass[] classes = aFile.getClasses();
		if(classes.length != 1)
		{
			throw new IncorrectOperationException("Incorrect " + type + " name \"" + name + "\".");
		}
		return classes[0];
	}

	@Nonnull
	@Override
	public PsiTypeElement createTypeElement(@Nonnull final PsiType psiType)
	{
		final LightTypeElement element = new LightTypeElement(myManager, psiType);
		CodeEditUtil.setNodeGenerated(element.getNode(), true);
		return element;
	}

	@Nonnull
	@Override
	public PsiJavaCodeReferenceElement createReferenceElementByType(@Nonnull final PsiClassType type)
	{
		if(type instanceof PsiClassReferenceType)
		{
			return ((PsiClassReferenceType) type).getReference();
		}

		final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
		final PsiClass refClass = resolveResult.getElement();
		assert refClass != null : type;
		return new LightClassReference(myManager, type.getPresentableText(), refClass, resolveResult.getSubstitutor());
	}

	@Nonnull
	@Override
	public PsiTypeParameterList createTypeParameterList()
	{
		final PsiTypeParameterList parameterList = createMethodFromText("void foo()", null).getTypeParameterList();
		assert parameterList != null;
		return parameterList;
	}

	@Nonnull
	@Override
	public PsiTypeParameter createTypeParameter(String name, PsiClassType[] superTypes)
	{
		StringBuilder builder = new StringBuilder();
		builder.append("public <").append(name);
		if(superTypes.length > 1)
		{
			builder.append(" extends ");
			for(PsiClassType type : superTypes)
			{
				if(type.equalsToText(JavaClassNames.JAVA_LANG_OBJECT))
				{
					continue;
				}
				builder.append(type.getCanonicalText()).append(',');
			}

			builder.delete(builder.length() - 1, builder.length());
		}
		builder.append("> void foo(){}");
		try
		{
			return createMethodFromText(builder.toString(), null).getTypeParameters()[0];
		}
		catch(RuntimeException e)
		{
			throw new IncorrectOperationException("type parameter text: " + builder.toString());
		}
	}

	@Nonnull
	@Override
	public PsiField createField(@Nonnull final String name, @Nonnull final PsiType type) throws IncorrectOperationException
	{
		PsiUtil.checkIsIdentifier(myManager, name);
		if(PsiType.NULL.equals(type))
		{
			throw new IncorrectOperationException("Cannot create field with type \"null\".");
		}

		final String text = "class _Dummy_ { private " + type.getCanonicalText() + " " + name + "; }";
		final PsiJavaFile aFile = createDummyJavaFile(text);
		final PsiClass[] classes = aFile.getClasses();
		if(classes.length < 1)
		{
			throw new IncorrectOperationException("Class was not created " + text);
		}
		final PsiClass psiClass = classes[0];
		final PsiField[] fields = psiClass.getFields();
		if(fields.length < 1)
		{
			throw new IncorrectOperationException("Field was not created " + text);
		}
		PsiField field = fields[0];
		field = (PsiField) JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(field);
		return (PsiField) CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
	}

	@Nonnull
	@Override
	public PsiMethod createMethod(@Nonnull final String name, final PsiType returnType) throws IncorrectOperationException
	{
		PsiUtil.checkIsIdentifier(myManager, name);
		if(PsiType.NULL.equals(returnType))
		{
			throw new IncorrectOperationException("Cannot create method with type \"null\".");
		}

		final String canonicalText = returnType.getCanonicalText();
		final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { public " + canonicalText + " " + name + "() {} " +
				"}");
		final PsiClass[] classes = aFile.getClasses();
		if(classes.length < 1)
		{
			throw new IncorrectOperationException("Class was not created. Method name: " + name + "; return type: " +
					canonicalText);
		}
		final PsiMethod[] methods = classes[0].getMethods();
		if(methods.length < 1)
		{
			throw new IncorrectOperationException("Method was not created. Method name: " + name + "; return type: " +
					canonicalText);
		}
		PsiMethod method = methods[0];
		method = (PsiMethod) JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(method);
		return (PsiMethod) CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
	}

	@Nonnull
	@Override
	public PsiMethod createMethod(@Nonnull @NonNls String name, PsiType returnType, PsiElement context) throws IncorrectOperationException
	{
		return createMethodFromText("public " + returnType.getCanonicalText(true) + " " + name + "() {}", context);
	}

	@Nonnull
	@Override
	public PsiMethod createConstructor()
	{
		return createConstructor("_Dummy_");
	}

	@Nonnull
	@Override
	public PsiMethod createConstructor(@Nonnull @NonNls final String name)
	{
		final PsiJavaFile aFile = createDummyJavaFile("class " + name + " { public " + name + "() {} }");
		final PsiMethod method = aFile.getClasses()[0].getMethods()[0];
		return (PsiMethod) CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
	}

	@Override
	public PsiMethod createConstructor(@Nonnull @NonNls String name, PsiElement context)
	{
		return createMethodFromText(name + "() {}", context);
	}

	@Nonnull
	@Override
	public PsiClassInitializer createClassInitializer() throws IncorrectOperationException
	{
		final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { {} }");
		final PsiClassInitializer classInitializer = aFile.getClasses()[0].getInitializers()[0];
		return (PsiClassInitializer) CodeStyleManager.getInstance(myManager.getProject()).reformat(classInitializer);
	}

	@Nonnull
	@Override
	public PsiParameter createParameter(@Nonnull final String name, @Nonnull final PsiType type) throws IncorrectOperationException
	{
		PsiUtil.checkIsIdentifier(myManager, name);
		if(PsiType.NULL.equals(type))
		{
			throw new IncorrectOperationException("Cannot create parameter with type \"null\".");
		}

		final String text = type.getCanonicalText() + " " + name;
		PsiParameter parameter = createParameterFromText(text, null);
		final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
		PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalParameters());
		GeneratedMarkerVisitor.markGenerated(parameter);
		parameter = (PsiParameter) JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(parameter);
		return (PsiParameter) codeStyleManager.reformat(parameter);
	}

	@Override
	public PsiParameter createParameter(@Nonnull @NonNls String name, PsiType type, PsiElement context) throws IncorrectOperationException
	{
		final PsiMethod psiMethod = createMethodFromText("void f(" + type.getCanonicalText() + " " + name + ") {}", context);
		final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
		return parameters[0];
	}

	@Nonnull
	@Override
	public PsiCodeBlock createCodeBlock()
	{
		final PsiCodeBlock block = createCodeBlockFromText("{}", null);
		return (PsiCodeBlock) CodeStyleManager.getInstance(myManager.getProject()).reformat(block);
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull final PsiClass aClass)
	{
		return new PsiImmediateClassType(aClass, aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : createRawSubstitutor(aClass));
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull final PsiJavaCodeReferenceElement classReference)
	{
		return new PsiClassReferenceType(classReference, null);
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull final PsiClass aClass, final PsiType parameter)
	{
		final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
		assert typeParameters.length == 1 : aClass;

		return createType(aClass, PsiSubstitutor.EMPTY.put(typeParameters[0], parameter));
	}

	@Nonnull
	@Override
	public PsiClassType createType(@Nonnull final PsiClass aClass, final PsiType... parameters)
	{
		return createType(aClass, PsiSubstitutor.EMPTY.putAll(aClass, parameters));
	}

	@Nonnull
	@Override
	public PsiSubstitutor createRawSubstitutor(@Nonnull final PsiTypeParameterListOwner owner)
	{
		Map<PsiTypeParameter, PsiType> substitutorMap = null;
		for(PsiTypeParameter parameter : PsiUtil.typeParametersIterable(owner))
		{
			if(substitutorMap == null)
			{
				substitutorMap = new HashMap<PsiTypeParameter, PsiType>();
			}
			substitutorMap.put(parameter, null);
		}
		return PsiSubstitutorImpl.createSubstitutor(substitutorMap);
	}

	@Nonnull
	@Override
	public PsiSubstitutor createRawSubstitutor(@Nonnull final PsiSubstitutor baseSubstitutor, @Nonnull final PsiTypeParameter[] typeParameters)
	{
		Map<PsiTypeParameter, PsiType> substitutorMap = null;
		for(PsiTypeParameter parameter : typeParameters)
		{
			if(substitutorMap == null)
			{
				substitutorMap = new HashMap<PsiTypeParameter, PsiType>();
			}
			substitutorMap.put(parameter, null);
		}
		return PsiSubstitutorImpl.createSubstitutor(substitutorMap).putAll(baseSubstitutor);
	}

	@Nonnull
	@Override
	public PsiElement createDummyHolder(@Nonnull final String text, @Nonnull final IElementType type, @Nullable final PsiElement context)
	{
		final DummyHolder result = DummyHolderFactory.createHolder(myManager, context);
		final FileElement holder = result.getTreeElement();
		final Language language = type.getLanguage();
		final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
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

	@Nonnull
	@Override
	public PsiSubstitutor createSubstitutor(@Nonnull final Map<PsiTypeParameter, PsiType> map)
	{
		return PsiSubstitutorImpl.createSubstitutor(map);
	}

	@Nullable
	@Override
	public PsiPrimitiveType createPrimitiveType(@Nonnull final String text)
	{
		return PsiJavaParserFacadeImpl.getPrimitiveType(text);
	}

	@Nonnull
	@Override
	public PsiClassType createTypeByFQClassName(@Nonnull final String qName)
	{
		return createTypeByFQClassName(qName, GlobalSearchScope.allScope(myManager.getProject()));
	}

	@Nonnull
	@Override
	public PsiClassType createTypeByFQClassName(@Nonnull final String qName, @Nonnull final GlobalSearchScope resolveScope)
	{
		return new PsiClassReferenceType(createReferenceElementByFQClassName(qName, resolveScope), null);
	}

	@Override
	public boolean isValidClassName(@Nonnull String name)
	{
		return isIdentifier(name);
	}

	@Override
	public boolean isValidMethodName(@Nonnull String name)
	{
		return isIdentifier(name);
	}

	@Override
	public boolean isValidParameterName(@Nonnull String name)
	{
		return isIdentifier(name);
	}

	@Override
	public boolean isValidFieldName(@Nonnull String name)
	{
		return isIdentifier(name);
	}

	@Override
	public boolean isValidLocalVariableName(@Nonnull String name)
	{
		return isIdentifier(name);
	}

	private boolean isIdentifier(@Nonnull String name)
	{
		return PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(name);
	}

	@Nonnull
	@Override
	public PsiJavaCodeReferenceElement createClassReferenceElement(@Nonnull final PsiClass aClass)
	{
		final String text;
		if(aClass instanceof PsiAnonymousClass)
		{
			text = ((PsiAnonymousClass) aClass).getBaseClassType().getPresentableText();
		}
		else
		{
			text = aClass.getName();
		}
		return new LightClassReference(myManager, text, aClass);
	}

	@Nonnull
	@Override
	public PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@Nonnull final String qName, @Nonnull final GlobalSearchScope resolveScope)
	{
		final String shortName = PsiNameHelper.getShortClassName(qName);
		return new LightClassReference(myManager, shortName, qName, resolveScope);
	}

	@Nonnull
	@Override
	public PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@Nonnull final String qName, @Nonnull final GlobalSearchScope resolveScope)
	{
		return new LightClassReference(myManager, qName, qName, resolveScope);
	}

	@Nonnull
	@Override
	public PsiJavaCodeReferenceElement createPackageReferenceElement(@Nonnull final PsiJavaPackage aPackage) throws IncorrectOperationException
	{
		if(aPackage.getQualifiedName().isEmpty())
		{
			throw new IncorrectOperationException("Cannot create reference to default package.");
		}
		return new LightPackageReference(myManager, aPackage);
	}

	@Nonnull
	@Override
	public PsiPackageStatement createPackageStatement(@Nonnull final String name) throws IncorrectOperationException
	{
		final PsiJavaFile aFile = createDummyJavaFile("package " + name + ";");
		final PsiPackageStatement stmt = aFile.getPackageStatement();
		if(stmt == null)
		{
			throw new IncorrectOperationException("Incorrect package name: " + name);
		}
		return stmt;
	}

	@Nonnull
	@Override
	public PsiImportStaticStatement createImportStaticStatement(@Nonnull final PsiClass aClass, @Nonnull final String memberName) throws IncorrectOperationException
	{
		if(aClass instanceof PsiAnonymousClass)
		{
			throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
		}
		else if(aClass.getParent() instanceof PsiDeclarationStatement)
		{
			throw new IncorrectOperationException("Cannot create import statement for local class.");
		}

		final PsiJavaFile aFile = createDummyJavaFile("import static " + aClass.getQualifiedName() + "." + memberName + ";");
		final PsiImportStatementBase statement = extractImport(aFile, true);
		return (PsiImportStaticStatement) CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
	}

	@Nonnull
	@Override
	public PsiParameterList createParameterList(@Nonnull final String[] names, @Nonnull final PsiType[] types) throws IncorrectOperationException
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("void method(");
		for(int i = 0; i < names.length; i++)
		{
			if(i > 0)
			{
				builder.append(", ");
			}
			builder.append(types[i].getCanonicalText()).append(' ').append(names[i]);
		}
		builder.append(");");
		return createMethodFromText(builder.toString(), null).getParameterList();
	}

	@Nonnull
	@Override
	public PsiReferenceList createReferenceList(@Nonnull final PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("void method()");
		if(references.length > 0)
		{
			builder.append(" throws ");
			for(int i = 0; i < references.length; i++)
			{
				if(i > 0)
				{
					builder.append(", ");
				}
				builder.append(references[i].getCanonicalText());
			}
		}
		builder.append(';');
		return createMethodFromText(builder.toString(), null).getThrowsList();
	}

	@Nonnull
	@Override
	public PsiJavaCodeReferenceElement createPackageReferenceElement(@Nonnull final String packageName) throws IncorrectOperationException
	{
		if(packageName.isEmpty())
		{
			throw new IncorrectOperationException("Cannot create reference to default package.");
		}
		return new LightPackageReference(myManager, packageName);
	}

	@Nonnull
	@Override
	public PsiReferenceExpression createReferenceExpression(@Nonnull final PsiClass aClass) throws IncorrectOperationException
	{
		final String text;
		if(aClass instanceof PsiAnonymousClass)
		{
			text = ((PsiAnonymousClass) aClass).getBaseClassType().getPresentableText();
		}
		else
		{
			text = aClass.getName();
		}
		return new LightClassReferenceExpression(myManager, text, aClass);
	}

	@Nonnull
	@Override
	public PsiReferenceExpression createReferenceExpression(@Nonnull final PsiJavaPackage aPackage) throws IncorrectOperationException
	{
		if(aPackage.getQualifiedName().isEmpty())
		{
			throw new IncorrectOperationException("Cannot create reference to default package.");
		}
		return new LightPackageReferenceExpression(myManager, aPackage);
	}

	@Nonnull
	@Override
	public PsiIdentifier createIdentifier(@Nonnull final String text) throws IncorrectOperationException
	{
		PsiUtil.checkIsIdentifier(myManager, text);
		return new LightIdentifier(myManager, text);
	}

	@Nonnull
	@Override
	public PsiKeyword createKeyword(@Nonnull final String text) throws IncorrectOperationException
	{
		if(!PsiNameHelper.getInstance(myManager.getProject()).isKeyword(text))
		{
			throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
		}
		return new LightKeyword(myManager, text);
	}

	@Nonnull
	@Override
	public PsiKeyword createKeyword(@Nonnull @NonNls String keyword, PsiElement context) throws IncorrectOperationException
	{
		LanguageLevel level = PsiUtil.getLanguageLevel(context);
		if(!JavaLexer.isKeyword(keyword, level) && !JavaLexer.isSoftKeyword(keyword, level))
		{
			throw new IncorrectOperationException("\"" + keyword + "\" is not a keyword.");
		}
		return new LightKeyword(myManager, keyword);
	}

	@Nonnull
	@Override
	public PsiImportStatement createImportStatement(@Nonnull final PsiClass aClass) throws IncorrectOperationException
	{
		if(aClass instanceof PsiAnonymousClass)
		{
			throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
		}
		else if(aClass.getParent() instanceof PsiDeclarationStatement)
		{
			throw new IncorrectOperationException("Cannot create import statement for local class.");
		}

		final PsiJavaFile aFile = createDummyJavaFile("import " + aClass.getQualifiedName() + ";");
		final PsiImportStatementBase statement = extractImport(aFile, false);
		return (PsiImportStatement) CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
	}

	@Nonnull
	@Override
	public PsiImportStatement createImportStatementOnDemand(@Nonnull final String packageName) throws IncorrectOperationException
	{
		if(packageName.isEmpty())
		{
			throw new IncorrectOperationException("Cannot create import statement for default package.");
		}
		if(!PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName))
		{
			throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
		}

		final PsiJavaFile aFile = createDummyJavaFile("import " + packageName + ".*;");
		final PsiImportStatementBase statement = extractImport(aFile, false);
		return (PsiImportStatement) CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
	}

	@Nonnull
	@Override
	public PsiDeclarationStatement createVariableDeclarationStatement(@Nonnull final String name, @Nonnull final PsiType type, final PsiExpression initializer) throws IncorrectOperationException
	{
		if(!PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(name))
		{
			throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
		}
		if(PsiType.NULL.equals(type))
		{
			throw new IncorrectOperationException("Cannot create variable with type \"null\".");
		}

		final String text = "X " + name + (initializer != null ? " = x" : "") + ";";

		final PsiDeclarationStatement statement = (PsiDeclarationStatement) createStatementFromText(text, null);
		final PsiVariable variable = (PsiVariable) statement.getDeclaredElements()[0];
		replace(variable.getTypeElement(), createTypeElement(type), text);
		PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalLocals());
		if(initializer != null)
		{
			replace(variable.getInitializer(), initializer, text);
		}
		GeneratedMarkerVisitor.markGenerated(statement);
		return statement;
	}

	@Nonnull
	@Override
	public PsiDocTag createParamTag(@Nonnull final String parameterName, @NonNls final String description) throws IncorrectOperationException
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(" * @param ");
		builder.append(parameterName);
		builder.append(" ");
		final String[] strings = description.split("\\n");
		for(int i = 0; i < strings.length; i++)
		{
			if(i > 0)
			{
				builder.append("\n * ");
			}
			builder.append(strings[i]);
		}
		return createDocTagFromText(builder.toString());
	}

	@Nonnull
	@Override
	public PsiAnnotation createAnnotationFromText(@Nonnull final String annotationText, @javax.annotation.Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
		GeneratedMarkerVisitor.markGenerated(psiAnnotation);
		return psiAnnotation;
	}

	@Nonnull
	@Override
	public PsiCodeBlock createCodeBlockFromText(@Nonnull final CharSequence text, @Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiCodeBlock psiCodeBlock = super.createCodeBlockFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(psiCodeBlock);
		return psiCodeBlock;
	}

	@Nonnull
	@Override
	public PsiEnumConstant createEnumConstantFromText(@Nonnull final String text, @javax.annotation.Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiEnumConstant enumConstant = super.createEnumConstantFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(enumConstant);
		return enumConstant;
	}

	@Nonnull
	@Override
	public PsiExpression createExpressionFromText(@Nonnull final String text, @Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiExpression expression = super.createExpressionFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(expression);
		return expression;
	}

	@Nonnull
	@Override
	public PsiField createFieldFromText(@Nonnull final String text, @Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiField psiField = super.createFieldFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(psiField);
		return psiField;
	}

	@Nonnull
	@Override
	public PsiParameter createParameterFromText(@Nonnull final String text, @javax.annotation.Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiParameter parameter = super.createParameterFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(parameter);
		return parameter;
	}

	@Nonnull
	@Override
	public PsiStatement createStatementFromText(@Nonnull final String text, @Nullable final PsiElement context) throws IncorrectOperationException
	{
		final PsiStatement statement = super.createStatementFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(statement);
		return statement;
	}

	@Nonnull
	@Override
	public PsiType createTypeFromText(@Nonnull final String text, @Nullable final PsiElement context) throws IncorrectOperationException
	{
		return createTypeInner(text, context, true);
	}

	@Nonnull
	@Override
	public PsiTypeParameter createTypeParameterFromText(@Nonnull final String text, final PsiElement context) throws IncorrectOperationException
	{
		final PsiTypeParameter typeParameter = super.createTypeParameterFromText(text, context);
		GeneratedMarkerVisitor.markGenerated(typeParameter);
		return typeParameter;
	}

	@Nonnull
	@Override
	public PsiMethod createMethodFromText(@Nonnull final String text, final PsiElement context, final LanguageLevel level) throws IncorrectOperationException
	{
		final PsiMethod method = super.createMethodFromText(text, context, level);
		GeneratedMarkerVisitor.markGenerated(method);
		return method;
	}

	private static PsiImportStatementBase extractImport(final PsiJavaFile aFile, final boolean isStatic)
	{
		final PsiImportList importList = aFile.getImportList();
		assert importList != null : aFile;
		final PsiImportStatementBase[] statements = isStatic ? importList.getImportStaticStatements() : importList.getImportStatements();
		assert statements.length == 1 : aFile.getText();
		return statements[0];
	}

	private static void replace(final PsiElement original, final PsiElement replacement, final String message)
	{
		assert original != null : message;
		original.replace(replacement);
	}

	private static final JavaParserUtil.ParserWrapper CATCH_SECTION = new JavaParserUtil.ParserWrapper()
	{
		@Override
		public void parse(final PsiBuilder builder)
		{
			JavaParser.INSTANCE.getStatementParser().parseCatchBlock(builder);
		}
	};

	@Nonnull
	@Override
	public PsiCatchSection createCatchSection(@Nonnull final PsiType exceptionType, @Nonnull final String exceptionName, @javax.annotation.Nullable final PsiElement context) throws IncorrectOperationException
	{
		if(!(exceptionType instanceof PsiClassType || exceptionType instanceof PsiDisjunctionType))
		{
			throw new IncorrectOperationException("Unexpected type:" + exceptionType);
		}

		@NonNls final String text = "catch (" + exceptionType.getCanonicalText(true) + " " + exceptionName + ") {}";
		final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CATCH_SECTION, level(context)), context);
		final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
		if(!(element instanceof PsiCatchSection))
		{
			throw new IncorrectOperationException("Incorrect catch section '" + text + "'. Parsed element: " + element);
		}

		final Project project = myManager.getProject();
		final JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(project);
		helper.setupCatchBlock(exceptionName, context, (PsiCatchSection) element);
		final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
		final PsiCatchSection catchSection = (PsiCatchSection) styleManager.reformat(element);

		GeneratedMarkerVisitor.markGenerated(catchSection);
		return catchSection;
	}
}

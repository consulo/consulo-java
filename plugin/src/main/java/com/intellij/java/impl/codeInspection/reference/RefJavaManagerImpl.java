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
package com.intellij.java.impl.codeInspection.reference;

import com.intellij.java.analysis.codeInspection.BatchSuppressManager;
import com.intellij.java.analysis.codeInspection.ex.EntryPointsManager;
import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionState;
import com.intellij.java.analysis.impl.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.java.analysis.impl.codeInspection.reference.RefClassImpl;
import com.intellij.java.analysis.impl.codeInspection.reference.RefMethodImpl;
import com.intellij.java.analysis.impl.codeInspection.reference.RefPackageImpl;
import com.intellij.java.analysis.impl.codeInspection.reference.RefParameterImpl;
import com.intellij.java.impl.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.java.impl.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.UserDataCache;
import consulo.disposer.Disposer;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.impl.inspection.reference.RefManagerImpl;
import consulo.language.editor.impl.inspection.reference.RefProjectImpl;
import consulo.language.editor.inspection.InspectionTool;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefFile;
import consulo.language.editor.inspection.reference.RefVisitor;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.inspection.scheme.Tools;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.function.Conditions;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author anna
 * Date: 20-Dec-2007
 */
public class RefJavaManagerImpl extends RefJavaManager
{
	private static final Logger LOG = Logger.getInstance(RefJavaManagerImpl.class);
	private static final Predicate<PsiElement> PROBLEM_ELEMENT_CONDITION = Conditions.or(
		Conditions.instanceOf(PsiFile.class, PsiJavaModule.class),
		Conditions.and(
			Conditions.notInstanceOf(PsiTypeParameter.class),
			psi -> (psi instanceof PsiField || !(psi instanceof PsiVariable)) && !(psi instanceof PsiClassInitializer)
		)
	);

	private PsiMethod myAppMainPattern;
	private PsiMethod myAppPremainPattern;
	private PsiClass myApplet;
	private PsiClass myServlet;
	private RefPackage myDefaultPackage;
	private Map<String, RefPackage> myPackages;
	private final RefManagerImpl myRefManager;
	private PsiElementVisitor myProjectIterator;
	private EntryPointsManager myEntryPointsManager;

	public RefJavaManagerImpl(@Nonnull RefManagerImpl manager)
	{
		myRefManager = manager;
		final Project project = manager.getProject();
		final PsiManager psiManager = PsiManager.getInstance(project);
		JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
		PsiElementFactory factory = javaPsiFacade.getElementFactory();
		try
		{
			myAppMainPattern = factory.createMethodFromText("void main(String[] args);", null);
			myAppPremainPattern = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
		}
		catch (IncorrectOperationException e)
		{
			LOG.error(e);
		}

		myApplet = javaPsiFacade.findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
		myServlet = javaPsiFacade.findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
	}

	@Override
	public RefPackage getPackage(String packageName)
	{
		if (myPackages == null)
		{
			myPackages = new HashMap<>();
		}

		RefPackage refPackage = myPackages.get(packageName);
		if (refPackage == null)
		{
			refPackage = new RefPackageImpl(packageName, myRefManager);
			myPackages.put(packageName, refPackage);

			int dotIndex = packageName.lastIndexOf('.');
			if (dotIndex >= 0)
			{
				((RefPackageImpl) getPackage(packageName.substring(0, dotIndex))).add(refPackage);
			}
			else
			{
				((RefProjectImpl) myRefManager.getRefProject()).add(refPackage);
			}
		}

		return refPackage;
	}


	public boolean isEntryPoint(final RefElement element)
	{
		Pair<UnusedDeclarationInspection, UnusedDeclarationInspectionState> pair = getDeadCodeTool(element);
		return pair != null && pair.getFirst() != null && pair.getFirst().isEntryPoint(element, pair.getSecond());
	}

	@Nullable
	private Pair<UnusedDeclarationInspection, UnusedDeclarationInspectionState> getDeadCodeTool(RefElement element)
	{
		PsiFile file = element.getContainingFile();
		return file == null ? null : getDeadCodeTool(file);
	}

	private static final UserDataCache<Pair<UnusedDeclarationInspection, UnusedDeclarationInspectionState>, PsiFile, RefManagerImpl>
		DEAD_CODE_TOOL = new UserDataCache<>("DEAD_CODE_TOOL")
	{
		@Override
		protected Pair<UnusedDeclarationInspection, UnusedDeclarationInspectionState> compute(PsiFile file, RefManagerImpl refManager)
		{
			Tools tools = refManager.getContext().getTools(UnusedDeclarationInspection.SHORT_NAME);
			InspectionToolWrapper toolWrapper = tools == null ? null : tools.getEnabledTool(file);
			InspectionTool tool = toolWrapper == null ? null : toolWrapper.getTool();
			Object state = toolWrapper == null ? null : toolWrapper.getState();
			return tool instanceof UnusedDeclarationInspection inspection
				? Pair.createNonNull(inspection, (UnusedDeclarationInspectionState)state)
				: Pair.empty();
		}
	};

	@Nullable
	private Pair<UnusedDeclarationInspection, UnusedDeclarationInspectionState> getDeadCodeTool(PsiElement element)
	{
		PsiFile file = element.getContainingFile();
		return file != null ? DEAD_CODE_TOOL.get(file, myRefManager) : null;
	}

	@Nullable
	@Override
	public PsiNamedElement getElementContainer(@Nonnull PsiElement psiElement)
	{
		return (PsiNamedElement) PsiTreeUtil.findFirstParent(psiElement, PROBLEM_ELEMENT_CONDITION);
	}

	@Override
	public boolean shouldProcessExternalFile(@Nonnull PsiFile file)
	{
		return file instanceof PsiClassOwner;
	}

	@Nonnull
	@Override
	public Stream<? extends PsiElement> extractExternalFileImplicitReferences(@Nonnull PsiFile psiFile)
	{
		return Arrays.stream(((PsiClassOwner) psiFile).getClasses())
			.flatMap(c -> Arrays.stream(c.getSuperTypes()))
			.map(PsiClassType::resolve)
			.filter(Objects::nonNull);
	}

	@Override
	public void markExternalReferencesProcessed(@Nonnull RefElement file)
	{
		getEntryPointsManager().addEntryPoint(file, false);
	}

	@Override
	public RefPackage getDefaultPackage()
	{
		if (myDefaultPackage == null)
		{
			myDefaultPackage = getPackage(InspectionLocalize.inspectionReferenceDefaultPackage().get());
		}
		return myDefaultPackage;
	}

	@Override
	public PsiMethod getAppMainPattern()
	{
		return myAppMainPattern;
	}

	@Override
	public PsiMethod getAppPremainPattern()
	{
		return myAppPremainPattern;
	}

	@Override
	public PsiClass getApplet()
	{
		return myApplet;
	}

	@Override
	public PsiClass getServlet()
	{
		return myServlet;
	}

	@Override
	public RefParameter getParameterReference(PsiParameter param, int index, RefMethod refMethod)
	{
		LOG.assertTrue(myRefManager.isValidPointForReference(), "References may become invalid after process is finished");

		return myRefManager.getFromRefTableOrCache(param, () -> {
			RefParameterImpl ref = new RefParameterImpl(param, index, myRefManager, refMethod);
			ref.initialize();
			return ref;
		});
	}

	@Override
	public void iterate(@Nonnull final RefVisitor visitor)
	{
		if (myPackages != null)
		{
			for (RefPackage refPackage : myPackages.values())
			{
				refPackage.accept(visitor);
			}
		}
		for (RefElement refElement : myRefManager.getSortedElements())
		{
			if (refElement instanceof RefClass refClass)
			{
				RefMethod refDefaultConstructor = refClass.getDefaultConstructor();
				if (refDefaultConstructor instanceof RefImplicitConstructor)
				{
					refClass.getDefaultConstructor().accept(visitor);
				}
			}
		}
	}

	@Override
	public void cleanup()
	{
		if (myEntryPointsManager != null)
		{
			Disposer.dispose(myEntryPointsManager);
			myEntryPointsManager = null;
		}
		myPackages = null;
		myApplet = null;
		myAppMainPattern = null;
		myAppPremainPattern = null;
		myServlet = null;
		myDefaultPackage = null;
		myProjectIterator = null;
	}

	@Override
	public void removeReference(@Nonnull final RefElement refElement)
	{
		if (refElement instanceof RefMethod refMethod)
		{
			RefParameter[] params = refMethod.getParameters();
			for (RefParameter param : params)
			{
				myRefManager.removeReference(param);
			}
		}
	}

	@Override
	@Nullable
	public RefElement createRefElement(@Nonnull final PsiElement elem)
	{
		if (elem instanceof PsiClass psiClass)
		{
			return new RefClassImpl(psiClass, myRefManager);
		}
		else if (elem instanceof PsiMethod method)
		{
			final RefElement ref = myRefManager.getReference(method.getContainingClass(), true);
			if (ref instanceof RefClass refClass)
			{
				return new RefMethodImpl(refClass, method, myRefManager);
			}
		}
		else if (elem instanceof PsiField field)
		{
			final RefElement ref = myRefManager.getReference(field.getContainingClass(), true);
			if (ref instanceof RefClass refClass)
			{
				return new RefFieldImpl(refClass, field, myRefManager);
			}
		}
		else if (elem instanceof PsiJavaFile javaFile)
		{
			return new RefJavaFileImpl(javaFile, myRefManager);
		}
		return null;
	}

	@Override
	@Nullable
	public RefEntity getReference(final String type, final String fqName)
	{
		switch (type) {
			case METHOD:
				return RefMethodImpl.methodFromExternalName(myRefManager, fqName);
			case CLASS:
				return RefClassImpl.classFromExternalName(myRefManager, fqName);
			case FIELD:
				return RefFieldImpl.fieldFromExternalName(myRefManager, fqName);
			case PARAMETER:
				return RefParameterImpl.parameterFromExternalName(myRefManager, fqName);
			case PACKAGE:
				return RefPackageImpl.packageFromFQName(myRefManager, fqName);
		}
		return null;
	}

	@Override
	@Nullable
	public String getType(@Nonnull final RefEntity ref)
	{
		if (ref instanceof RefMethod)
		{
			return METHOD;
		}
		else if (ref instanceof RefClass)
		{
			return CLASS;
		}
		else if (ref instanceof RefField)
		{
			return FIELD;
		}
		else if (ref instanceof RefParameter)
		{
			return PARAMETER;
		}
		else if (ref instanceof RefPackage)
		{
			return PACKAGE;
		}
		return null;
	}

	@Nonnull
	@Override
	public RefEntity getRefinedElement(@Nonnull final RefEntity ref)
	{
		return ref instanceof RefImplicitConstructor implicitConstructor ? implicitConstructor.getOwnerClass() : ref;
	}

	@Override
	public void visitElement(@Nonnull final PsiElement element)
	{
		if (myProjectIterator == null)
		{
			myProjectIterator = new MyJavaElementVisitor();
		}
		element.accept(myProjectIterator);
	}

	@Override
	@Nullable
	public String getGroupName(@Nonnull final RefEntity entity)
	{
		return entity instanceof RefFile && !(entity instanceof RefJavaFileImpl)
			? null : RefJavaUtil.getInstance().getPackageName(entity);
	}

	@Override
	public boolean belongsToScope(@Nonnull final PsiElement psiElement)
	{
		return !(psiElement instanceof PsiTypeParameter);
	}

	@RequiredReadAction
	@Override
	public void export(@Nonnull final RefEntity refEntity, @Nonnull final Element element)
	{
		if (refEntity instanceof RefElement refElement)
		{
			final SmartPsiElementPointer pointer = refElement.getPointer();
			if (pointer != null)
			{
				final PsiFile psiFile = pointer.getContainingFile();
				if (psiFile instanceof PsiJavaFile javaFile)
				{
					appendPackageElement(element, javaFile.getPackageName());
				}
			}
		}
	}

	@Override
	public void onEntityInitialized(@Nonnull RefElement refElement, @Nonnull PsiElement psiElement)
	{
		if (isEntryPoint(refElement))
		{
			getEntryPointsManager().addEntryPoint(refElement, false);
		}

		if (psiElement instanceof PsiClass psiClass)
		{
			EntryPointsManager entryPointsManager = getEntryPointsManager();
			if (psiClass.isAnnotationType())
			{
				entryPointsManager.addEntryPoint(refElement, false);
				for (PsiMethod psiMethod : psiClass.getMethods())
				{
					entryPointsManager.addEntryPoint(myRefManager.getReference(psiMethod), false);
				}
			}
			else if (psiClass.isEnum())
			{
				entryPointsManager.addEntryPoint(refElement, false);
			}
		}
	}

	private static void appendPackageElement(final Element element, final String packageName)
	{
		final Element packageElement = new Element("package");
		packageElement.addContent(packageName.isEmpty() ? InspectionLocalize.inspectionExportResultsDefault().get() : packageName);
		element.addContent(packageElement);
	}

	@Override
	public EntryPointsManager getEntryPointsManager()
	{
		if (myEntryPointsManager == null)
		{
			final Project project = myRefManager.getProject();
			myEntryPointsManager = new EntryPointsManagerImpl(project);
			((EntryPointsManagerBase) myEntryPointsManager).addAllPersistentEntries(EntryPointsManagerBase.getInstance(project));
		}
		return myEntryPointsManager;
	}

	private class MyJavaElementVisitor extends JavaElementVisitor
	{
		private final RefJavaUtil myRefUtil;

		public MyJavaElementVisitor()
		{
			myRefUtil = RefJavaUtil.getInstance();
		}

		@Override
		public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression)
		{
			visitElement(expression);
		}

		@Override
		public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference)
		{
		}

		@Override
		public void visitReferenceParameterList(@Nonnull final PsiReferenceParameterList list)
		{
			super.visitReferenceParameterList(list);
			final PsiMember member = PsiTreeUtil.getParentOfType(list, PsiMember.class);
			final PsiType[] typeArguments = list.getTypeArguments();
			for (PsiType type : typeArguments)
			{
				myRefUtil.addTypeReference(member, type, myRefManager);
			}
		}

		@Override
		public void visitClass(@Nonnull PsiClass aClass)
		{
			if (!(aClass instanceof PsiTypeParameter))
			{
				super.visitClass(aClass);
				RefElement refClass = myRefManager.getReference(aClass);
				if (refClass != null)
				{
					((RefClassImpl) refClass).buildReferences();
				}
			}
		}

		@Override
		public void visitMethod(@Nonnull final PsiMethod method)
		{
			super.visitMethod(method);
			final RefElement refElement = myRefManager.getReference(method);
			if (refElement instanceof RefMethodImpl refMethod)
			{
				refMethod.buildReferences();
			}
		}

		@Override
		public void visitField(@Nonnull final PsiField field)
		{
			super.visitField(field);
			final RefElement refElement = myRefManager.getReference(field);
			if (refElement instanceof RefFieldImpl refField)
			{
				refField.buildReferences();
			}
		}

		@Override
		@RequiredReadAction
		public void visitDocComment(@Nonnull PsiDocComment comment)
		{
			super.visitDocComment(comment);
			final PsiDocTag[] tags = comment.getTags();
			for (PsiDocTag tag : tags)
			{
				if (Comparing.strEqual(tag.getName(), SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME))
				{
					final PsiElement[] dataElements = tag.getDataElements();
					if (dataElements != null && dataElements.length > 0)
					{
						final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(comment, PsiModifierListOwner.class);
						if (listOwner != null)
						{
							final RefElementImpl element = (RefElementImpl) myRefManager.getReference(listOwner);
							if (element != null)
							{
								String suppression = "";
								for (PsiElement dataElement : dataElements)
								{
									suppression += "," + dataElement.getText();
								}
								element.addSuppression(suppression);
							}
						}
					}
				}
			}
		}

		@Override
		@RequiredReadAction
		public void visitAnnotation(@Nonnull PsiAnnotation annotation)
		{
			super.visitAnnotation(annotation);
			if (Comparing.strEqual(annotation.getQualifiedName(), BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME))
			{
				final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
				if (listOwner != null)
				{
					final RefElementImpl element = (RefElementImpl) myRefManager.getReference(listOwner);
					if (element != null)
					{
						StringBuilder buf = new StringBuilder();
						final PsiNameValuePair[] nameValuePairs = annotation.getParameterList().getAttributes();
						for (PsiNameValuePair nameValuePair : nameValuePairs)
						{
							buf.append(",").append(nameValuePair.getText().replaceAll("[{}\"\"]", ""));
						}
						if (buf.length() > 0)
						{
							element.addSuppression(buf.substring(1));
						}
					}
				}
			}
		}

		@Override
		public void visitVariable(@Nonnull PsiVariable variable)
		{
			super.visitVariable(variable);
			myRefUtil.addTypeReference(variable, variable.getType(), myRefManager);
		}

		@Override
		public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression expression)
		{
			super.visitInstanceOfExpression(expression);
			final PsiTypeElement typeElement = expression.getCheckType();
			if (typeElement != null)
			{
				myRefUtil.addTypeReference(expression, typeElement.getType(), myRefManager);
			}
		}

		@Override
		@RequiredReadAction
		public void visitThisExpression(@Nonnull PsiThisExpression expression)
		{
			super.visitThisExpression(expression);
			final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
			if (qualifier != null)
			{
				myRefUtil.addTypeReference(expression, expression.getType(), myRefManager);
				RefClass ownerClass = myRefUtil.getOwnerClass(myRefManager, expression);
				if (ownerClass != null)
				{
					RefClassImpl refClass = (RefClassImpl) myRefManager.getReference(qualifier.resolve());
					if (refClass != null)
					{
						refClass.addInstanceReference(ownerClass);
					}
				}
			}
		}
	}
}

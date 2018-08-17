/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import static com.intellij.util.ObjectUtils.notNull;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import consulo.psi.PsiPackage;

public class LightJavaModule extends LightElement implements PsiJavaModule
{
	private final LightJavaModuleReferenceElement myRefElement;
	private final VirtualFile myJarRoot;
	private final NotNullLazyValue<List<PsiPackageAccessibilityStatement>> myExports = AtomicNotNullLazyValue.createValue(() -> findExports());

	private LightJavaModule(@Nonnull PsiManager manager, @Nonnull VirtualFile jarRoot)
	{
		super(manager, JavaLanguage.INSTANCE);
		myJarRoot = jarRoot;
		myRefElement = new LightJavaModuleReferenceElement(manager, moduleName(jarRoot.getNameWithoutExtension()));
	}

	@Nonnull
	public VirtualFile getRootVirtualFile()
	{
		return myJarRoot;
	}

	@javax.annotation.Nullable
	@Override
	public PsiDocComment getDocComment()
	{
		return null;
	}

	@Nonnull
	@Override
	public Iterable<PsiRequiresStatement> getRequires()
	{
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<PsiPackageAccessibilityStatement> getExports()
	{
		return myExports.getValue();
	}

	private List<PsiPackageAccessibilityStatement> findExports()
	{
		List<PsiPackageAccessibilityStatement> exports = ContainerUtil.newArrayList();

		VfsUtilCore.visitChildrenRecursively(myJarRoot, new VirtualFileVisitor()
		{
			private JavaDirectoryService service = JavaDirectoryService.getInstance();

			@Override
			public boolean visitFile(@Nonnull VirtualFile file)
			{
				if(file.isDirectory() && !myJarRoot.equals(file))
				{
					PsiDirectory directory = getManager().findDirectory(file);
					if(directory != null)
					{
						PsiPackage pkg = service.getPackage(directory);
						if(pkg != null)
						{
							String packageName = pkg.getQualifiedName();
							if(!packageName.isEmpty() && !PsiUtil.isPackageEmpty(new PsiDirectory[]{directory}, packageName))
							{
								exports.add(new LightPackageAccessibilityStatement(getManager(), packageName));
							}
						}
					}
				}
				return true;
			}
		});

		return exports;
	}

	@Nonnull
	@Override
	public Iterable<PsiPackageAccessibilityStatement> getOpens()
	{
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<PsiUsesStatement> getUses()
	{
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<PsiProvidesStatement> getProvides()
	{
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public PsiJavaModuleReferenceElement getNameIdentifier()
	{
		return myRefElement;
	}

	@Nonnull
	@Override
	public String getName()
	{
		return myRefElement.getReferenceText();
	}

	@Override
	public PsiElement setName(@Nonnull String name) throws IncorrectOperationException
	{
		throw new IncorrectOperationException("Cannot modify automatic module '" + getName() + "'");
	}

	@Override
	public PsiModifierList getModifierList()
	{
		return null;
	}

	@Override
	public boolean hasModifierProperty(@Nonnull String name)
	{
		return false;
	}

	@Override
	public ItemPresentation getPresentation()
	{
		return ItemPresentationProviders.getItemPresentation(this);
	}

	@Nonnull
	@Override
	public PsiElement getNavigationElement()
	{
		return notNull(myManager.findDirectory(myJarRoot), super.getNavigationElement());
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof LightJavaModule && myJarRoot.equals(((LightJavaModule) obj).myJarRoot) && getManager() == ((LightJavaModule) obj).getManager();
	}

	@Override
	public int hashCode()
	{
		return getName().hashCode() * 31 + getManager().hashCode();
	}

	@Override
	public String toString()
	{
		return "PsiJavaModule:" + getName();
	}

	private static class LightJavaModuleReferenceElement extends LightElement implements PsiJavaModuleReferenceElement
	{
		private final String myText;

		public LightJavaModuleReferenceElement(@Nonnull PsiManager manager, @Nonnull String text)
		{
			super(manager, JavaLanguage.INSTANCE);
			myText = text;
		}

		@Nonnull
		@Override
		public String getReferenceText()
		{
			return myText;
		}

		@javax.annotation.Nullable
		@Override
		public PsiPolyVariantReference getReference()
		{
			return null;
		}

		@Override
		public String toString()
		{
			return "PsiJavaModuleReference";
		}
	}

	private static class LightPackageAccessibilityStatement extends LightElement implements PsiPackageAccessibilityStatement
	{
		private final String myPackageName;

		public LightPackageAccessibilityStatement(@Nonnull PsiManager manager, @Nonnull String packageName)
		{
			super(manager, JavaLanguage.INSTANCE);
			myPackageName = packageName;
		}

		@Nonnull
		@Override
		public Role getRole()
		{
			return Role.EXPORTS;
		}

		@javax.annotation.Nullable
		@Override
		public PsiJavaCodeReferenceElement getPackageReference()
		{
			return null;
		}

		@javax.annotation.Nullable
		@Override
		public String getPackageName()
		{
			return myPackageName;
		}

		@Nonnull
		@Override
		public Iterable<PsiJavaModuleReferenceElement> getModuleReferences()
		{
			return Collections.emptyList();
		}

		@Nonnull
		@Override
		public List<String> getModuleNames()
		{
			return Collections.emptyList();
		}

		@Override
		public String toString()
		{
			return "PsiPackageAccessibilityStatement";
		}
	}

	@Nonnull
	public static LightJavaModule getModule(@Nonnull final PsiManager manager, @Nonnull final VirtualFile jarRoot)
	{
		final PsiDirectory directory = manager.findDirectory(jarRoot);
		assert directory != null : jarRoot;
		return CachedValuesManager.getCachedValue(directory, () ->
		{
			LightJavaModule module = new LightJavaModule(manager, jarRoot);
			return CachedValueProvider.Result.create(module, directory);
		});
	}

	/**
	 * Implements a name deriving for  automatic modules as described in ModuleFinder.of(Path...) method documentation.
	 *
	 * @param name a .jar file name without extension
	 * @see <a href="http://download.java.net/java/jdk9/docs/api/java/lang/module/ModuleFinder.html#of-java.nio.file.Path...-">ModuleFinder.of(Path...)</a>
	 */
	@Nonnull
	public static String moduleName(@Nonnull String name)
	{
		// If the name matches the regular expression "-(\\d+(\\.|$))" then the module name will be derived from the sub-sequence
		// preceding the hyphen of the first occurrence.
		Matcher m = Patterns.VERSION.matcher(name);
		if(m.find())
		{
			name = name.substring(0, m.start());
		}

		// For the module name, then any trailing digits and dots are removed ...
		name = Patterns.TAIL_VERSION.matcher(name).replaceFirst("");
		// ... all non-alphanumeric characters ([^A-Za-z0-9]) are replaced with a dot (".") ...
		name = Patterns.NON_NAME.matcher(name).replaceAll(".");
		// ... all repeating dots are replaced with one dot ...
		name = Patterns.DOT_SEQUENCE.matcher(name).replaceAll(".");
		// ... and all leading and trailing dots are removed
		name = StringUtil.trimLeading(StringUtil.trimTrailing(name, '.'), '.');

		return name;
	}

	private static class Patterns
	{
		private static final Pattern VERSION = Pattern.compile("-(\\d+(\\.|$))");
		private static final Pattern TAIL_VERSION = Pattern.compile("[0-9.]+$");
		private static final Pattern NON_NAME = Pattern.compile("[^A-Za-z0-9]");
		private static final Pattern DOT_SEQUENCE = Pattern.compile("\\.{2,}");
	}
}
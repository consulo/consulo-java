/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.util;

import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.ide.ServiceManager;
import consulo.language.psi.*;
import consulo.language.psi.path.FileReference;
import consulo.project.Project;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.image.Image;
import consulo.util.collection.SLRUMap;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolve small icons located in project for use in UI (e.g. gutter preview icon, lookups).
 *
 * @since 15
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class ProjectIconsAccessor implements Disposable
{
	@jakarta.annotation.Nonnull
	public static ProjectIconsAccessor getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, ProjectIconsAccessor.class);
	}

	private static final int ICON_MAX_WEIGHT = 16;

	private static final int ICON_MAX_HEIGHT = 16;

	private static final String JAVAX_SWING_ICON = "javax.swing.Icon";

	private static final int ICON_MAX_SIZE = 2 * 1024 * 1024; // 2Kb

	private static final List<String> ICON_EXTENSIONS = List.of("png", "ico", "bmp", "gif", "jpg", "svg");

	private final SLRUMap<String, Pair<Long, Image>> myIconsCache = new SLRUMap<>(500, 1000);

	@Inject
	ProjectIconsAccessor(Project project)
	{
	}

	@Nullable
	public VirtualFile resolveIconFile(PsiType type, @Nullable PsiExpression initializer)
	{
		if(initializer == null || !initializer.isValid() || !isIconClassType(type))
		{
			return null;
		}

		final List<FileReference> refs = new ArrayList<FileReference>();
		initializer.accept(new JavaRecursiveElementWalkingVisitor()
		{
			@Override
			public void visitElement(PsiElement element)
			{
				if(element instanceof PsiLiteralExpression)
				{
					for(PsiReference ref : element.getReferences())
					{
						if(ref instanceof FileReference)
						{
							refs.add((FileReference) ref);
						}
					}
				}
				super.visitElement(element);
			}
		});

		for(FileReference ref : refs)
		{
			final PsiFileSystemItem psiFileSystemItem = ref.resolve();
			VirtualFile file = null;
			if(psiFileSystemItem == null)
			{
				final ResolveResult[] results = ref.multiResolve(false);
				for(ResolveResult result : results)
				{
					final PsiElement element = result.getElement();
					if(element instanceof PsiBinaryFile)
					{
						file = ((PsiFile) element).getVirtualFile();
						break;
					}
				}
			}
			else
			{
				file = psiFileSystemItem.getVirtualFile();
			}

			if(file == null || file.isDirectory() || !isIconFileExtension(file.getExtension()) || file.getLength() > ICON_MAX_SIZE)
			{
				continue;
			}

			return file;
		}
		return null;
	}

	@Nullable
	public Image getIcon(@jakarta.annotation.Nonnull VirtualFile file, @Nullable PsiElement element)
	{
		final String path = file.getPath();
		final long stamp = file.getModificationStamp();
		Pair<Long, Image> iconInfo = myIconsCache.get(path);
		if(iconInfo == null || iconInfo.getFirst() < stamp)
		{
			try
			{
				final Image icon = createOrFindBetterIcon(file);
				iconInfo = new Pair<>(stamp, hasProperSize(icon) ? icon : null);
				myIconsCache.put(file.getPath(), iconInfo);
			}
			catch(Exception e)
			{
				iconInfo = null;
				myIconsCache.remove(path);
			}
		}
		return iconInfo == null ? null : iconInfo.getSecond();
	}

	public static boolean isIconClassType(PsiType type)
	{
		return InheritanceUtil.isInheritor(type, JAVAX_SWING_ICON) || InheritanceUtil.isInheritor(type, Image.class.getName());
	}

	private static boolean isIconFileExtension(String extension)
	{
		return extension != null && ICON_EXTENSIONS.contains(extension.toLowerCase(Locale.US));
	}

	private static boolean hasProperSize(Image icon)
	{
		return icon.getHeight() <= JBUI.scale(ICON_MAX_WEIGHT) && icon.getWidth() <= JBUI.scale(ICON_MAX_HEIGHT);
	}

	private static Image createOrFindBetterIcon(VirtualFile file) throws IOException
	{
		Image.ImageType imageType = Image.ImageType.PNG;
		if("svg".equalsIgnoreCase(file.getExtension()))
		{
			imageType = Image.ImageType.SVG;
		}

		try (InputStream stream = file.getInputStream())
		{
			return Image.fromStream(imageType, stream);
		}
	}

	@Override
	public void dispose()
	{
		myIconsCache.clear();
	}
}

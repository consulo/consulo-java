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
package consulo.java.impl.library;

import java.util.Collection;

import javax.annotation.Nonnull;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.RootDetector;
import com.intellij.java.impl.openapi.roots.ui.configuration.JavaVfsSourceRootDetectionUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class JavaSourceRootDetector extends RootDetector
{
	public JavaSourceRootDetector()
	{
		super(OrderRootType.SOURCES, false, "java sources");
	}

	@Nonnull
	@Override
	public Collection<VirtualFile> detectRoots(@Nonnull VirtualFile virtualFile, @Nonnull ProgressIndicator progressIndicator)
	{
		return JavaVfsSourceRootDetectionUtil.suggestRoots(virtualFile, progressIndicator);
	}
}

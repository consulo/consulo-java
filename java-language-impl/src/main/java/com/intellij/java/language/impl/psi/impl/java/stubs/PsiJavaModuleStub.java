/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiJavaModule;
import consulo.language.psi.stub.StubElement;

public interface PsiJavaModuleStub extends StubElement<PsiJavaModule>
{
	/**
	 * The module is not resolved by default from the class path.
	 */
	int DO_NOT_RESOLVE_BY_DEFAULT = 1;

	/**
	 * The module is marked as deprecated.
	 */
	int WARN_DEPRECATED = 2;

	/**
	 * The module is deprecated and will be removed in a future release.
	 */
	int WARN_DEPRECATED_FOR_REMOVAL = 4;

	/**
	 * The module is in incubating mode and not yet standardized.
	 */
	int WARN_INCUBATING = 8;

	String getName();

	/**
	 * Represents the attributes of a {@code module-info.class} file, as specified by
	 * <a href="https://openjdk.org/jeps/11#Relationship-to-other-modules">JEP 11</a>.
	 *
	 * @return a bitmask of the module resolution attributes, or {@code 0} if none are set
	 */
	int getResolution();
}

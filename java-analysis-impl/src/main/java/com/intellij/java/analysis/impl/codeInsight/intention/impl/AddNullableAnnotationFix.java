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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:59 PM
 */
package com.intellij.java.analysis.impl.codeInsight.intention.impl;

import java.util.List;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.util.collection.ArrayUtil;

public class AddNullableAnnotationFix extends AddNullableNotNullAnnotationFix {
	public AddNullableAnnotationFix(@Nonnull PsiModifierListOwner owner) {
		super(NullableNotNullManager.getInstance(owner.getProject()).getDefaultNullable(),
				owner,
				getNotNulls(owner));
	}

	@Nonnull
	private static String[] getNotNulls(@Nonnull PsiModifierListOwner owner) {
		final List<String> notnulls = NullableNotNullManager.getInstance(owner.getProject()).getNotNulls();
		return ArrayUtil.toStringArray(notnulls);
	}
}

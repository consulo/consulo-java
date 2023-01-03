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
package com.intellij.java.debugger.impl.memory.utils;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import consulo.util.lang.StringUtil;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ObjectReference;

class NamesUtils
{
	@Nonnull
	static String getUniqueName(@Nonnull ObjectReference ref)
	{
		String shortName = StringUtil.getShortName(ref.referenceType().name());
		String name = shortName.replace("[]", "Array");
		return String.format("%s@%d", name, ref.uniqueID());
	}

	@Nonnull
	static String getArrayUniqueName(@Nonnull ArrayReference ref)
	{
		String shortName = StringUtil.getShortName(ref.referenceType().name());
		int length = ref.length();

		String name = shortName.replaceFirst(Pattern.quote("[]"), String.format("[%d]", length));
		return String.format("%s@%d", name, ref.uniqueID());
	}
}

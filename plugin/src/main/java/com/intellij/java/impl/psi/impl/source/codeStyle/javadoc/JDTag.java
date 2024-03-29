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
package com.intellij.java.impl.psi.impl.source.codeStyle.javadoc;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.util.lang.StringUtil;

/**
 * User: Lepenkin Y.
 * Date: 7/1/13
 * Time: 4:15 PM
 */
public enum JDTag
{
	SEE("see"),
	AUTHOR("author"),
	VERSION("version"),
	THROWS("throws"),
	EXCEPTION("exception"),
	RETURN("return"),
	PARAM("param"),
	SINCE("since"),
	DEPRECATED("deprecated");

	@Nonnull
	private final String myTag;

	JDTag(@Nonnull String tag)
	{
		this.myTag = tag;
	}

	@Nonnull
	public String getDescriptionPrefix(@Nonnull String prefix)
	{
		return prefix + StringUtil.repeatSymbol(' ', getWithEndWhitespace().length());
	}

	@Nonnull
	public String getWithEndWhitespace()
	{
		return "@" + myTag + " ";
	}

	public boolean tagEqual(@Nullable String tag)
	{
		return myTag.equals(tag);
	}
}

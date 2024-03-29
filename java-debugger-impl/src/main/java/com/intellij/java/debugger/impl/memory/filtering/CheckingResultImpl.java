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
package com.intellij.java.debugger.impl.memory.filtering;

import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class CheckingResultImpl implements CheckingResult
{
	public static final CheckingResult SUCCESS = new CheckingResultImpl(CheckingResultImpl.Result.MATCH, "");
	public static final CheckingResult FAIL = new CheckingResultImpl(CheckingResultImpl.Result.NO_MATCH, "");

	private final Result myResult;
	private final String myDescription;

	private CheckingResultImpl(@Nonnull Result result, @Nonnull String description)
	{
		myResult = result;
		myDescription = description;
	}

	public static CheckingResult error(@Nonnull String description)
	{
		return new CheckingResultImpl(Result.ERROR, description);
	}

	@Override
	@Nonnull
	public Result getResult()
	{
		return myResult;
	}

	@Override
	@Nonnull
	public String getFailureDescription()
	{
		return myDescription;
	}
}

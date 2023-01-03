/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.analysis.impl.codeInspection.dataFlow;

/**
 * from kotlin
 */
public class ExceptionTransfer implements TransferTarget
{
	private final TypeConstraint throwable;

	public ExceptionTransfer(TypeConstraint throwable)
	{
		this.throwable = throwable;
	}

	public TypeConstraint getThrowable()
	{
		return throwable;
	}

	@Override
	public String toString()
	{
		return "Exception(" + throwable + ")";
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		ExceptionTransfer that = (ExceptionTransfer) o;

		if(throwable != null ? !throwable.equals(that.throwable) : that.throwable != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		return throwable != null ? throwable.hashCode() : 0;
	}
}

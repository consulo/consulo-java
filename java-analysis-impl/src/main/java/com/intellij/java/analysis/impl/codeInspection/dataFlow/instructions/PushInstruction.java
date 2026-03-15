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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.language.psi.PsiExpression;

/**
 * An instruction that pushes given value to the stack
 */
public class PushInstruction extends EvalInstruction
{
	private final
	DfaValue myValue;
	private final boolean myReferenceWrite;

	public PushInstruction(DfaValue value, PsiExpression place)
	{
		this(value, place, false);
	}

	public PushInstruction(DfaValue value, PsiExpression place, final boolean isReferenceWrite)
	{
		super(place, 0);
		myValue = value;
		myReferenceWrite = isReferenceWrite;
	}

	public boolean isReferenceWrite()
	{
		return myReferenceWrite;
	}

	public DfaValue getValue()
	{
		return myValue;
	}

	@Override
	public
	DfaValue eval(DfaValueFactory factory, DfaMemoryState state, DfaValue ... arguments)
	{
		return myValue;
	}

	public String toString()
	{
		return "PUSH " + myValue;
	}
}

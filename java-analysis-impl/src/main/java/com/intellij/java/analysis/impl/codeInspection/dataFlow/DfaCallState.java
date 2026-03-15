// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;


final class DfaCallState
{
	final DfaMemoryState myMemoryState;
	final DfaCallArguments myCallArguments;

	DfaCallState(DfaMemoryState state, DfaCallArguments arguments)
	{
		myMemoryState = state;
		myCallArguments = arguments;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(!(o instanceof DfaCallState))
			return false;
		DfaCallState that = (DfaCallState) o;
		return myMemoryState.equals(that.myMemoryState) && myCallArguments.equals(that.myCallArguments);
	}

	@Override
	public int hashCode()
	{
		return 31 * myMemoryState.hashCode() + myCallArguments.hashCode();
	}
}

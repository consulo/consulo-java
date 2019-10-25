package com.intellij.codeInspection.bytecodeAnalysis;

import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

final class Calls extends BasicValue
{
	private static final Type CallType = Type.getObjectType("/Call");

	final int mergedLabels;

	Calls(int mergedLabels)
	{
		super(CallType);
		this.mergedLabels = mergedLabels;
	}

	@Override
	public boolean equals(Object o)
	{
		if(o == null || getClass() != o.getClass())
			return false;
		Calls calls = (Calls) o;
		return mergedLabels == calls.mergedLabels;
	}

	@Override
	public int hashCode()
	{
		return mergedLabels;
	}
}

package com.intellij.codeInspection.bytecodeAnalysis;

import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

final class LabeledNull extends BasicValue
{
	private static final Type NullType = Type.getObjectType("null");

	final int origins;

	LabeledNull(int origins)
	{
		super(NullType);
		this.origins = origins;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		LabeledNull that = (LabeledNull) o;
		return origins == that.origins;
	}

	@Override
	public int hashCode()
	{
		return origins;
	}
}
package com.intellij.codeInspection.bytecodeAnalysis;


final class Constraint
{
	final static Constraint EMPTY = new Constraint(0, 0);

	final int calls;
	final int nulls;

	Constraint(int calls, int nulls)
	{
		this.calls = calls;
		this.nulls = nulls;
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

		Constraint that = (Constraint) o;

		if(calls != that.calls)
		{
			return false;
		}
		if(nulls != that.nulls)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = calls;
		result = 31 * result + nulls;
		return result;
	}
}
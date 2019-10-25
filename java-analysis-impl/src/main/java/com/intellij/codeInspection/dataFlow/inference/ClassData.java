package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.lang.LighterASTNode;

import java.util.Map;
import java.util.Objects;

/**
 * from kotlin
 */
class ClassData
{
	private final boolean hasSuper;
	private final boolean hasPureInitializer;
	private final Map<String, LighterASTNode> fieldModifiers;

	ClassData(boolean hasSuper, boolean hasPureInitializer, Map<String, LighterASTNode> fieldModifiers)
	{
		this.hasSuper = hasSuper;
		this.hasPureInitializer = hasPureInitializer;
		this.fieldModifiers = fieldModifiers;
	}

	public boolean isHasSuper()
	{
		return hasSuper;
	}

	public boolean isHasPureInitializer()
	{
		return hasPureInitializer;
	}

	public Map<String, LighterASTNode> getFieldModifiers()
	{
		return fieldModifiers;
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
		ClassData classData = (ClassData) o;
		return hasSuper == classData.hasSuper &&
				hasPureInitializer == classData.hasPureInitializer &&
				Objects.equals(fieldModifiers, classData.fieldModifiers);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(hasSuper, hasPureInitializer, fieldModifiers);
	}
}

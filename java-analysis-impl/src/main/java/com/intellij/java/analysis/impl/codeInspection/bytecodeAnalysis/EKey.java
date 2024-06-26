// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import jakarta.annotation.Nonnull;

import java.security.MessageDigest;

/**
 * Equation key (or variable)
 */
public final class EKey
{
	final
	@Nonnull
	MemberDescriptor member;
	final int dirKey;
	final boolean stable;
	final boolean negated;

	public EKey(@Nonnull MemberDescriptor member, Direction direction, boolean stable)
	{
		this(member, direction, stable, false);
	}

	EKey(@Nonnull MemberDescriptor member, Direction direction, boolean stable, boolean negated)
	{
		this(member, direction.asInt(), stable, negated);
	}

	EKey(@Nonnull MemberDescriptor member, int dirKey, boolean stable, boolean negated)
	{
		this.member = member;
		this.dirKey = dirKey;
		this.stable = stable;
		this.negated = negated;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;

		EKey key = (EKey) o;

		if(stable != key.stable)
			return false;
		if(negated != key.negated)
			return false;
		if(dirKey != key.dirKey)
			return false;
		if(!member.equals(key.member))
			return false;
		return true;
	}

	@Override
	public int hashCode()
	{
		int result = member.hashCode();
		result = 31 * result + dirKey;
		result = 31 * result + (stable ? 1 : 0);
		result = 31 * result + (negated ? 1 : 0);
		return result;
	}

	EKey invertStability()
	{
		return new EKey(member, dirKey, !stable, negated);
	}

	EKey mkStable()
	{
		return stable ? this : new EKey(member, dirKey, true, negated);
	}

	EKey mkUnstable()
	{
		return stable ? new EKey(member, dirKey, false, negated) : this;
	}

	public EKey mkBase()
	{
		return withDirection(Direction.Out);
	}

	EKey withDirection(Direction dir)
	{
		return dirKey == dir.asInt() ? this : new EKey(member, dir, stable, false);
	}

	EKey negate()
	{
		return new EKey(member, dirKey, stable, true);
	}

	public EKey hashed(MessageDigest md)
	{
		HMember hMember = member.hashed(md);
		return hMember == member ? this : new EKey(hMember, dirKey, stable, negated);
	}

	public Direction getDirection()
	{
		return Direction.fromInt(dirKey);
	}

	@Override
	public String toString()
	{
		return "Key [" + member + "|" + (stable ? "S" : "-") + (negated ? "N" : "-") + "|" + Direction.fromInt(dirKey) + "]";
	}
}
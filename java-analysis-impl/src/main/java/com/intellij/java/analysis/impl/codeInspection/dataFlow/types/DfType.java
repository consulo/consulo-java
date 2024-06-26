// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a domain of possible values within data flow analysis
 */
public interface DfType
{

	/**
	 * Checks whether this type is the supertype of the supplied type, i.e. every value from the other type belongs to this type as well.
	 * if A.isSuperType(B) then A.join(B) is A and A.meet(B) is B.
	 *
	 * @param other other type
	 * @return true if this type is the supertype of other.
	 */
	boolean isSuperType(@Nonnull DfType other);

	default boolean isMergeable(@Nonnull DfType other)
	{
		return isSuperType(other);
	}

	/**
	 * Return the most specific type that contains all values from this type and from other type.
	 *
	 * @param other type to join
	 * @return the result of the join operation
	 */
	@Nonnull
	DfType join(@Nonnull DfType other);

	/**
	 * Returns the least specific type that contains all values that belong both to this type and to other type.
	 *
	 * @param other type to meet
	 * @return the result of the meet operation.
	 */
	@Nonnull
	DfType meet(@Nonnull DfType other);

	/**
	 * @return a type that contains all the values of the corresponding JVM type except the values of given type;
	 * may return null if the corresponding type is not supported by our type system.
	 */
	@Nullable
	default DfType tryNegate()
	{
		return null;
	}

	@Override
	String toString();
}

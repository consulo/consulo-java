// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.security.MessageDigest;

/**
 * An uniquely method (class name+method name+signature) identifier: either {@link Member} or {@link HMember}.
 */
public interface MemberDescriptor
{
	/**
	 * Creates and returns the hashed representation of this method descriptor.
	 * May return itself if already hashed. Note that hashed descriptor is not equal to
	 * non-hashed one.
	 *
	 * @param md message digest to use for hashing (could be null to use the default one)
	 * @return a corresponding HMethod.
	 */
	@Nonnull
	HMember hashed(@Nullable MessageDigest md);
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import one.util.streamex.IntStreamEx;
import jakarta.annotation.Nonnull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter.getMessageDigest;

/**
 * Hashed representation of method.
 */
public final class HMember implements MemberDescriptor
{
	// how many bytes are taken from class fqn digest
	private static final int CLASS_HASH_SIZE = Long.BYTES + Short.BYTES;
	// how many bytes are taken from signature digest
	private static final int SIGNATURE_HASH_SIZE = Integer.BYTES;
	static final int HASH_SIZE = CLASS_HASH_SIZE + SIGNATURE_HASH_SIZE;

	final long myClassHi;
	final short myClassLo;
	final int myMethod;

	HMember(Member method, MessageDigest md)
	{
		if(md == null)
		{
			md = getMessageDigest();
		}
		byte[] classDigest = md.digest(method.internalClassName.getBytes(StandardCharsets.UTF_8));
		ByteBuffer classBuffer = ByteBuffer.wrap(classDigest);
		myClassHi = classBuffer.getLong();
		myClassLo = classBuffer.getShort();

		md.update(method.methodName.getBytes(StandardCharsets.UTF_8));
		md.update(method.methodDesc.getBytes(StandardCharsets.UTF_8));
		byte[] sigDigest = md.digest();
		myMethod = ByteBuffer.wrap(sigDigest).getInt();
	}

	public HMember(@Nonnull byte[] bytes)
	{
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		myClassHi = buffer.getLong();
		myClassLo = buffer.getShort();
		myMethod = buffer.getInt();
	}

	@Nonnull
	byte[] asBytes()
	{
		ByteBuffer bytes = ByteBuffer.allocate(HASH_SIZE);
		bytes.putLong(myClassHi).putShort(myClassLo).putInt(myMethod);
		return bytes.array();
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;
		HMember that = (HMember) o;
		return that.myClassHi == myClassHi && that.myClassLo == myClassLo && that.myMethod == myMethod;
	}

	@Override
	public int hashCode()
	{
		// Must work as Arrays.hashCode(asBytes()) to preserve compatibility with old caches
		int result = 1;
		for(int i = Long.BYTES - 1; i >= 0; i--)
			result = result * 31 + (byte) ((myClassHi >>> (i * 8)) & 0xFF);
		for(int i = Short.BYTES - 1; i >= 0; i--)
			result = result * 31 + (byte) ((myClassLo >>> (i * 8)) & 0xFF);
		for(int i = Integer.BYTES - 1; i >= 0; i--)
			result = result * 31 + (byte) ((myMethod >>> (i * 8)) & 0xFF);
		return result;
	}

	@Nonnull
	@Override
	public HMember hashed(MessageDigest md)
	{
		return this;
	}

	public String toString()
	{
		return bytesToString(asBytes());
	}

	static String bytesToString(byte[] key)
	{
		return IntStreamEx.of(key).mapToObj(b -> String.format("%02x", b & 0xFF)).joining(".");
	}
}
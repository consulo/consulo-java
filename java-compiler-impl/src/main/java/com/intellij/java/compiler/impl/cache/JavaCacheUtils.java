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
package com.intellij.java.compiler.impl.cache;

import com.intellij.java.compiler.impl.classParsing.MethodInfo;
import consulo.compiler.CacheCorruptedException;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nullable;
import java.util.ArrayList;

public class JavaCacheUtils {
    private static final Logger LOGGER = Logger.getInstance(JavaCacheUtils.class);

    public static boolean areArraysContentsEqual(int[] exceptions1, int[] exceptions2) {
        if (exceptions1.length != exceptions2.length) {
            return false;
        }
        if (exceptions1.length != 0) { // optimization
            IntSet exceptionsSet = IntSets.newHashSet(exceptions1);
            for (int exception : exceptions2) {
                if (!exceptionsSet.contains(exception)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String getMethodSignature(String name, String descriptor) {
        final StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append(descriptor.substring(0, descriptor.indexOf(')') + 1));
        return builder.toString();
    }

    public static String[] getParameterSignatures(MethodInfo methodDeclarationId, SymbolTable symbolTable) throws CacheCorruptedException {
        String descriptor = symbolTable.getSymbol(methodDeclarationId.getDescriptor());
        int endIndex = descriptor.indexOf(')');
        if (endIndex <= 0) {
            LOGGER.error("Corrupted method descriptor: " + descriptor);
        }
        return parseSignature(descriptor.substring(1, endIndex));
    }

    private static String[] parseSignature(String signature) {
        final ArrayList<String> list = new ArrayList<String>();
        String paramSignature = parseParameterSignature(signature);
        while (paramSignature != null && !"".equals(paramSignature)) {
            list.add(paramSignature);
            signature = signature.substring(paramSignature.length());
            paramSignature = parseParameterSignature(signature);
        }
        return ArrayUtil.toStringArray(list);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private static
    @Nullable
    String parseParameterSignature(String signature) {
        if (StringUtil.startsWithChar(signature, 'B')) {
            return "B";
        }
        if (StringUtil.startsWithChar(signature, 'C')) {
            return "C";
        }
        if (StringUtil.startsWithChar(signature, 'D')) {
            return "D";
        }
        if (StringUtil.startsWithChar(signature, 'F')) {
            return "F";
        }
        if (StringUtil.startsWithChar(signature, 'I')) {
            return "I";
        }
        if (StringUtil.startsWithChar(signature, 'J')) {
            return "J";
        }
        if (StringUtil.startsWithChar(signature, 'S')) {
            return "S";
        }
        if (StringUtil.startsWithChar(signature, 'Z')) {
            return "Z";
        }
        if (StringUtil.startsWithChar(signature, 'L')) {
            return signature.substring(0, signature.indexOf(";") + 1);
        }
        if (StringUtil.startsWithChar(signature, '[')) {
            String s = parseParameterSignature(signature.substring(1));
            return (s != null) ? ("[" + s) : null;
        }
        return null;
    }
}

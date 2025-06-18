package com.intellij.java.coverage;

import consulo.internal.org.objectweb.asm.ClassReader;
import consulo.util.collection.primitive.ints.IntObjectMap;

import java.util.List;

/**
 * @author anna
 * @since 2011-12-30
 */
public class SourceLineCounterUtil {
    public static boolean collectNonCoveredClassInfo(
        PackageAnnotator.ClassCoverageInfo classCoverageInfo,
        PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
        byte[] content,
        boolean excludeLines
    ) {
        if (content == null) {
            return false;
        }
        ClassReader reader = new ClassReader(content, 0, content.length);

        SourceLineCounter counter = new SourceLineCounter(null, excludeLines, null);
        reader.accept(counter, 0);
        classCoverageInfo.totalLineCount += counter.getNSourceLines();
        classCoverageInfo.totalMethodCount += counter.getNMethodsWithCode();
        packageCoverageInfo.totalLineCount += counter.getNSourceLines();
        packageCoverageInfo.totalMethodCount += counter.getNMethodsWithCode();
        if (!counter.isInterface()) {
            packageCoverageInfo.totalClassCount++;
        }
        return false;
    }

    public static void collectSrcLinesForUntouchedFiles(List<Integer> uncoveredLines, byte[] content, boolean excludeLines) {
        ClassReader reader = new ClassReader(content);
        SourceLineCounter collector = new SourceLineCounter(null, excludeLines, null);
        reader.accept(collector, 0);
        IntObjectMap lines = collector.getSourceLines();
        lines.keySet().forEach(line -> {
            line--;
            uncoveredLines.add(line);
        });
    }
}

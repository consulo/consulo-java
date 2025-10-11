package com.siyeh.igtest.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

public class One extends BaseInspection {

    public BaseInspectionVisitor buildVisitor() {
        return null;
    }

    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.empty();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return null;
    }

    public LocalizeValue getDisplayName() {
        return LocalizeValue.of();
    }

    private static class Inner {
        private static boolean test() {
            return false;
        }

    }
}
class TestVariableScope {
    public static void main(String[] args) {
        int foo;
        // blah
        if (true) {
            foo = 5;
        }

        doStuff(foo);
    }

    private static void doStuff(int foo) {
    }

}
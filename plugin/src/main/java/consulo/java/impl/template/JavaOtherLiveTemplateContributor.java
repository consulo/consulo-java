package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.JavaCommentContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeCodeContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeDeclarationContextType;
import com.intellij.java.impl.codeInsight.template.JavaLikeStatementContextType;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.localize.JavaLiveTemplateLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;

@ExtensionImpl
public class JavaOtherLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    public String groupId() {
        return "javaother";
    }

    @Override
    public LocalizeValue groupName() {
        return JavaLiveTemplateLocalize.groupNameJavaOther();
    }

    @Override
    public void contribute(LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder(
            "javaT",
            "t",
            "<$TAG$>$END$</$TAG_NAME$>",
            JavaLiveTemplateLocalize.descriptionTagPair()
        )) {
            builder.withReformat();

            builder.withVariable("TAG", "", "", true);
            builder.withVariable("ATTRS", "", "\"\"", false);
            builder.withVariable("TAG_NAME", "firstWord(TAG)", "\"\"", false);

            builder.withContext(JavaCommentContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherInst",
            "inst",
            "if ($EXPR$ instanceof $TYPE$) {\r\n"
                + "  $TYPE$ $VAR1$ = ($TYPE$)$EXPR$;\r\n"
                + "  $END$\r\n"
                + "}",
            JavaLiveTemplateLocalize.descriptionInst()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("EXPR", "variableOfType(\"Object\")", "expr", true);
            builder.withVariable("TYPE", "\"Object\"", "", true);
            builder.withVariable("VAR1", "suggestVariableName()", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherLst",
            "lst",
            "$ARRAY$[$ARRAY$.length - 1]",
            JavaLiveTemplateLocalize.descriptionLst()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("ARRAY", "arrayVariable()", "array", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherMn",
            "mn",
            "$VAR$ = Math.min($VAR$, $END$);",
            JavaLiveTemplateLocalize.descriptionMn()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "variableOfType(\"double\")", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherMx",
            "mx",
            "$VAR$ = Math.max($VAR$, $END$);",
            JavaLiveTemplateLocalize.descriptionMx()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "variableOfType(\"double\")", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherPsvm",
            "psvm",
            "public static void main(String[] args){\r\n"
                + "  $END$\r\n"
                + "}",
            JavaLiveTemplateLocalize.descriptionPsvm()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withContextsOf(JavaLikeCodeContextType.class, true);
            builder.withContextsOf(JavaLikeStatementContextType.class, false);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherToar",
            "toar",
            "$COLLECTION$.toArray(new $COMPONENT_TYPE$[$COLLECTION$.size()])$END$",
            JavaLiveTemplateLocalize.descriptionToar()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("COMPONENT_TYPE", "componentTypeOf(expectedType())", "\"Object\"", true);
            builder.withVariable("COLLECTION", "variableOfType(\"java.util.Collection\")", "collection", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherLazy",
            "lazy",
            "if ($VAR$ == null) {\n"
                + "  $VAR$ = new $TYPE$($END$);\n"
                + "}",
            JavaLiveTemplateLocalize.descriptionLazy()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "variableOfType(\"java.lang.Object\")", "", true);
            builder.withVariable("TYPE", "subtypes(typeOfVariable(VAR))", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherInn",
            "inn",
            "if ($VAR$ != null) {\n"
                + "$END$\n"
                + "}",
            JavaLiveTemplateLocalize.descriptionIfNotNull()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "suggestFirstVariableName(\"Object\")", "var", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherIfn",
            "ifn",
            "if ($VAR$ == null) {\n"
                + "$END$\n"
                + "}",
            JavaLiveTemplateLocalize.descriptionIfNull()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "suggestFirstVariableName(\"Object\")", "var", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder(
            "javaotherGeti",
            "geti",
            "public static $CLASS_NAME$ getInstance() {\r\n"
                + "  return $VALUE$;\r\n"
                + "}",
            JavaLiveTemplateLocalize.descriptionGeti()
        )) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("CLASS_NAME", "className", "", false);
            builder.withVariable("VALUE", "variableOfType(CLASS_NAME)", "null", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeDeclarationContextType.class, true);
        }
    }
}

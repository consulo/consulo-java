package consulo.java.impl.template;

import com.intellij.java.impl.codeInsight.template.*;
import com.intellij.java.impl.codeInsight.template.impl.ShortenFQNamesProcessor;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaOtherLiveTemplateContributor implements LiveTemplateContributor {
    @Override
    @Nonnull
    public String groupId() {
        return "javaother";
    }

    @Override
    @Nonnull
    public LocalizeValue groupName() {
        return LocalizeValue.localizeTODO("Java Other");
    }

    @Override
    public void contribute(@Nonnull LiveTemplateContributor.Factory factory) {
        try (Builder builder = factory.newBuilder("javaT", "t", "<$TAG$>$END$</$TAG_NAME$>", CodeInsightLocalize.livetemplateDescriptionTagPair())) {
            builder.withReformat();

            builder.withVariable("TAG", "", "", true);
            builder.withVariable("ATTRS", "", "\"\"", false);
            builder.withVariable("TAG_NAME", "firstWord(TAG)", "\"\"", false);

            builder.withContext(JavaCommentContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherInst", "inst", "if ($EXPR$ instanceof $TYPE$) {\r\n"
            + "  $TYPE$ $VAR1$ = ($TYPE$)$EXPR$;\r\n"
            + "  $END$\r\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionInst())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("EXPR", "variableOfType(\"Object\")", "expr", true);
            builder.withVariable("TYPE", "\"Object\"", "", true);
            builder.withVariable("VAR1", "suggestVariableName()", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherLst", "lst", "$ARRAY$[$ARRAY$.length - 1]", CodeInsightLocalize.livetemplateDescriptionLst())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("ARRAY", "arrayVariable()", "array", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherMn", "mn", "$VAR$ = Math.min($VAR$, $END$);", CodeInsightLocalize.livetemplateDescriptionMn())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "variableOfType(\"double\")", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherMx", "mx", "$VAR$ = Math.max($VAR$, $END$);", CodeInsightLocalize.livetemplateDescriptionMx())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "variableOfType(\"double\")", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherPsvm", "psvm", "public static void main(String[] args){\r\n"
            + "  $END$\r\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionPsvm())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);


            builder.withContextsOf(JavaLikeCodeContextType.class, true);
            builder.withContextsOf(JavaLikeStatementContextType.class, false);
        }

        try (Builder builder = factory.newBuilder("javaotherToar", "toar", "$COLLECTION$.toArray(new $COMPONENT_TYPE$[$COLLECTION$.size()])$END$", CodeInsightLocalize.livetemplateDescriptionToar())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("COMPONENT_TYPE", "componentTypeOf(expectedType())", "\"Object\"", true);
            builder.withVariable("COLLECTION", "variableOfType(\"java.util.Collection\")", "collection", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherLazy", "lazy", "if ($VAR$ == null) {\n"
            + "  $VAR$ = new $TYPE$($END$);\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionLazy())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "variableOfType(\"java.lang.Object\")", "", true);
            builder.withVariable("TYPE", "subtypes(typeOfVariable(VAR))", "", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherInn", "inn", "if ($VAR$ != null) {\n"
            + "$END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionIfNotNull())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "suggestFirstVariableName(\"Object\")", "var", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherIfn", "ifn", "if ($VAR$ == null) {\n"
            + "$END$\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionIfNull())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("VAR", "suggestFirstVariableName(\"Object\")", "var", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeStatementContextType.class, true);
        }

        try (Builder builder = factory.newBuilder("javaotherGeti", "geti", "public static $CLASS_NAME$ getInstance() {\r\n"
            + "  return $VALUE$;\r\n"
            + "}", CodeInsightLocalize.livetemplateDescriptionGeti())) {
            builder.withReformat();

            builder.withOption(ShortenFQNamesProcessor.KEY, true);

            builder.withVariable("CLASS_NAME", "className", "", false);
            builder.withVariable("VALUE", "variableOfType(CLASS_NAME)", "null", true);

            builder.withContextsOf(JavaLikeCodeContextType.class, false);
            builder.withContextsOf(JavaLikeDeclarationContextType.class, true);
        }

    }
}

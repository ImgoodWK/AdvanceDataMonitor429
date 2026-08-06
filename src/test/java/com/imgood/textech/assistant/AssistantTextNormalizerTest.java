package com.imgood.textech.assistant;

import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/** Regression contracts for bounded, linear-time assistant text normalization. */
public class AssistantTextNormalizerTest {

    @Test
    public void migratesLegacySymmetricEdgePatternWithoutChangingItsCharacterClass() {
        String legacy = "^[\\s,.;:!?]+|[\\s,.;:!?]+$";
        String hardened = AssistantLexicon.hardenLegacyEdgePunctuationRegex(legacy);

        Assert.assertEquals("^[\\s,.;:!?]+|(?<![\\s,.;:!?])[\\s,.;:!?]+$", hardened);
        Assert.assertEquals(hardened, AssistantLexicon.hardenLegacyEdgePunctuationRegex(hardened));
    }

    @Test(timeout = 1000)
    public void bundledEdgePatternHandlesLongWhitespaceWithoutQuadraticBacktracking() {
        Pattern pattern = Pattern.compile("^[\\s,.;:!?]+|(?<![\\s,.;:!?])[\\s,.;:!?]+$");
        StringBuilder input = new StringBuilder(20000);
        input.append('x');
        for (int i = 0; i < 20000; i++) {
            input.append('\t');
        }
        input.append('x');

        Assert.assertEquals(
            input.toString(),
            pattern.matcher(input)
                .replaceAll(""));
        Assert.assertEquals(
            "value",
            pattern.matcher(" \t,value!... ")
                .replaceAll(""));
    }
}

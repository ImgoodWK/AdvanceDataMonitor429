package com.imgood.textech.webae.recipe;

import org.junit.Assert;
import org.junit.Test;

public class RecipeUploadSessionTest {

    @Test
    public void expiredSessionDoesNotBlockTheNextUpload() {
        String player = "00000000-0000-0000-0000-000000000001";
        RecipeUploadSession.clearAllForTests();
        try {
            RecipeUploadSession.BatchDecision first = RecipeUploadSession.acceptBatch(player, 0, 2, true, false);
            Assert.assertTrue(first.accepted);
            Assert.assertTrue(first.newSession);
            Assert.assertTrue(RecipeUploadSession.isActive(player));

            RecipeUploadSession.pruneExpired(
                System.currentTimeMillis() + RecipeUploadSession.SESSION_TTL_MS + 1L);
            Assert.assertFalse(RecipeUploadSession.isActive(player));

            RecipeUploadSession.BatchDecision restarted = RecipeUploadSession.acceptBatch(player, 0, 1, true, true);
            Assert.assertTrue(restarted.accepted);
            Assert.assertTrue(restarted.newSession);
            Assert.assertTrue(restarted.completed);
            Assert.assertFalse(RecipeUploadSession.isActive(player));
        } finally {
            RecipeUploadSession.clearAllForTests();
        }
    }

    @Test
    public void rejectedBatchCannotAdvanceOrReplaceAnActiveSession() {
        String player = "00000000-0000-0000-0000-000000000002";
        RecipeUploadSession.clearAllForTests();
        try {
            Assert.assertTrue(RecipeUploadSession.acceptBatch(player, 0, 2, true, false).accepted);
            Assert.assertFalse(RecipeUploadSession.acceptBatch(player, 0, 2, true, false).accepted);
            Assert.assertFalse(RecipeUploadSession.acceptBatch(player, 0, 2, false, false).accepted);
            Assert.assertTrue(RecipeUploadSession.isActive(player));
            Assert.assertTrue(RecipeUploadSession.acceptBatch(player, 1, 2, false, true).completed);
        } finally {
            RecipeUploadSession.clearAllForTests();
        }
    }
}

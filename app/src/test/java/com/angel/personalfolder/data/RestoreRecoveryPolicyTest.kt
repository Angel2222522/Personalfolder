package com.angel.personalfolder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreRecoveryPolicyTest {
    private val ids = setOf("doc-1")

    @Test
    fun preparedJournalNeverDeletesOnlyLiveRoot() {
        val action = RestoreRecoveryPolicy.decide(
            RestoreRecoveryState(
                phase = "prepared",
                currentDocumentIds = ids,
                expectedDocumentIds = ids,
                rootExists = true,
                previousRootExists = false,
                stagingRootExists = true,
                rootMatchesExpected = true,
                databaseGenerationMatches = true
            )
        )
        assertEquals(RestoreRecoveryAction.PRESERVE_AND_RETRY, action)
    }

    @Test
    fun databaseCommittedMatchingGenerationCanBeFinalized() {
        val action = RestoreRecoveryPolicy.decide(
            RestoreRecoveryState("database_committed", ids, ids, true, true, false, true, true)
        )
        assertEquals(RestoreRecoveryAction.FINALIZE_NEW_GENERATION, action)
    }

    @Test
    fun filesInstalledWithPreviousGenerationRollsBackWhenDatabaseDidNotCommit() {
        val action = RestoreRecoveryPolicy.decide(
            RestoreRecoveryState("files_installed", setOf("old"), ids, true, true, true, false, false)
        )
        assertEquals(RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION, action)
    }

    @Test
    fun filesInstalledWithoutPreviousGenerationPreservesReplacement() {
        val action = RestoreRecoveryPolicy.decide(
            RestoreRecoveryState("files_installed", setOf("old"), ids, true, false, false, true, false)
        )
        assertEquals(RestoreRecoveryAction.PRESERVE_AND_RETRY, action)
    }

    @Test
    fun preparedJournalAfterFilesystemSwapRollsBackBeforeDatabaseCommit() {
        val action = RestoreRecoveryPolicy.decide(
            RestoreRecoveryState("prepared", ids, ids, true, true, false, true, true)
        )
        assertEquals(RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION, action)
    }

    @Test
    fun sameDocumentIdsDoNotProveTheDatabaseGenerationMatches() {
        val action = RestoreRecoveryPolicy.decide(
            RestoreRecoveryState("files_installed", ids, ids, true, true, false, true, false)
        )
        assertEquals(RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION, action)
    }
}

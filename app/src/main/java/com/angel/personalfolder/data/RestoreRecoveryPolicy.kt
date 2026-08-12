package com.angel.personalfolder.data

/** The only safe actions available while completing an interrupted restore. */
enum class RestoreRecoveryAction {
    FINALIZE_NEW_GENERATION,
    ROLLBACK_TO_PREVIOUS_GENERATION,
    PRESERVE_AND_RETRY
}

data class RestoreRecoveryState(
    val phase: String,
    val currentDocumentIds: Set<String>,
    val expectedDocumentIds: Set<String>,
    val rootExists: Boolean,
    val previousRootExists: Boolean,
    val stagingRootExists: Boolean,
    val rootMatchesExpected: Boolean
)

/**
 * Decides recovery without ever assuming that a missing previous directory
 * is permission to delete the current root.
 */
object RestoreRecoveryPolicy {
    fun decide(state: RestoreRecoveryState): RestoreRecoveryAction {
        val databaseMatches = state.currentDocumentIds == state.expectedDocumentIds
        val newGenerationIsValid = databaseMatches && state.rootExists && state.rootMatchesExpected

        return when (state.phase) {
            "database_committed" -> if (newGenerationIsValid) {
                RestoreRecoveryAction.FINALIZE_NEW_GENERATION
            } else {
                RestoreRecoveryAction.PRESERVE_AND_RETRY
            }

            "files_installed" -> when {
                newGenerationIsValid -> RestoreRecoveryAction.FINALIZE_NEW_GENERATION
                state.previousRootExists -> RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION
                else -> RestoreRecoveryAction.PRESERVE_AND_RETRY
            }

            "prepared" -> when {
                // At "prepared" the live root has not been atomically moved
                // yet. A matching ID set can still be the old generation, so
                // it is never enough evidence to finalize without a previous
                // root proving that the swap happened.
                state.previousRootExists && newGenerationIsValid -> RestoreRecoveryAction.FINALIZE_NEW_GENERATION
                state.previousRootExists -> RestoreRecoveryAction.ROLLBACK_TO_PREVIOUS_GENERATION
                // The old root may still be the only valid library. Preserve it.
                else -> RestoreRecoveryAction.PRESERVE_AND_RETRY
            }

            else -> RestoreRecoveryAction.PRESERVE_AND_RETRY
        }
    }
}

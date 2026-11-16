package com.snowdos.app.domain.strategy

import com.snowdos.app.domain.model.Task

enum class ProofType {
    SIMPLE_TAP,
    PHOTO,
    SIGNATURE
}

sealed interface ProofInput {
    data object None : ProofInput
    data class Provided(val reference: String) : ProofInput
}

sealed interface ProofResult {
    data object Verified : ProofResult
    data class RequiresInteraction(
        val proofType: ProofType,
        val promptMessage: String
    ) : ProofResult
    data class Rejected(val reason: String) : ProofResult
}

/**
 * Extension point for task completion verification (simple tap today, camera/S-Pen proof later).
 */
interface CompletionProofStrategy {
    suspend fun verify(task: Task, input: ProofInput = ProofInput.None): ProofResult
}

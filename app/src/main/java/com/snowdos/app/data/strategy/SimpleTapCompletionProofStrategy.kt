package com.snowdos.app.data.strategy

import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.strategy.CompletionProofStrategy
import com.snowdos.app.domain.strategy.ProofInput
import com.snowdos.app.domain.strategy.ProofResult
import javax.inject.Inject

/**
 * Default completion proof: a single tap verifies the task immediately.
 */
class SimpleTapCompletionProofStrategy @Inject constructor() : CompletionProofStrategy {
    override suspend fun verify(task: Task, input: ProofInput): ProofResult = ProofResult.Verified
}

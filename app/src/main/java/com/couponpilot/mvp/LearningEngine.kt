package com.couponpilot.mvp

object LearningEngine {
    fun analyse(feedback: List<CouponFeedback>): List<ImprovementProposal> {
        if (feedback.size < 3) return emptyList()

        val proposals = mutableListOf<ImprovementProposal>()
        val failures = feedback.filter { it.outcome == "FAILED" }

        failures.groupBy { it.sourceApp }.forEach { (source, rows) ->
            if (rows.size >= 3) {
                proposals += ImprovementProposal(
                    proposalType = "SOURCE_PARSER",
                    title = "Improve $source coupon parsing",
                    description = "Add a source-specific parsing rule because multiple captured coupons from $source were reported as unusable.",
                    evidence = "${rows.size} failed recommendations from $source.",
                    confidence = confidence(rows.size, feedback.size),
                    rulePayload = "source=$source;action=review_parser_patterns"
                )
            }
        }

        failures.filter { it.reason.isNotBlank() }
            .groupBy { it.reason.trim().lowercase() }
            .forEach { (reason, rows) ->
                if (rows.size >= 2) {
                    proposals += ImprovementProposal(
                        proposalType = "ELIGIBILITY_RULE",
                        title = "Add eligibility check: ${reason.take(40)}",
                        description = "Use this repeated failure reason as an additional eligibility signal before recommending a coupon.",
                        evidence = "Reported ${rows.size} times across ${rows.map { it.merchant }.distinct().size} merchant(s).",
                        confidence = confidence(rows.size, feedback.size),
                        rulePayload = "blocked_reason=${reason.replace(';', ',')}"
                    )
                }
            }

        feedback.filter { it.outcome == "NOT_BEST" }
            .groupBy { it.merchant.lowercase() }
            .forEach { (merchant, rows) ->
                if (rows.size >= 2) {
                    proposals += ImprovementProposal(
                        proposalType = "RANKING_RULE",
                        title = "Review ranking for ${merchant.replaceFirstChar { it.uppercase() }}",
                        description = "Adjust ranking inputs for this merchant because users repeatedly found a better offer than the recommendation.",
                        evidence = "${rows.size} 'not best' reports.",
                        confidence = confidence(rows.size, feedback.size),
                        rulePayload = "merchant=$merchant;action=review_ranking"
                    )
                }
            }

        return proposals.distinctBy { it.proposalType + it.rulePayload }
    }

    private fun confidence(matches: Int, total: Int): Double =
        (matches.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
}

package dev.arny.githubmanager.domain

/** GitHub keeps `status` for an in-flight run and `conclusion` only after it finishes. */
fun runStatusLabel(run: GitHubWorkflowRun): String = when {
    run.conclusion != null -> when (run.conclusion) {
        "success" -> "Успешно"
        "failure" -> "Ошибка"
        "cancelled" -> "Отменён"
        "skipped" -> "Пропущен"
        "timed_out" -> "Таймаут"
        "action_required" -> "Требуется действие"
        else -> run.conclusion
    }
    run.status == "queued" -> "В очереди"
    run.status == "in_progress" -> "Выполняется"
    run.status == "waiting" -> "Ожидает"
    else -> run.status
}

fun runTitle(run: GitHubWorkflowRun): String =
    run.displayTitle?.takeIf { it.isNotBlank() }
        ?: run.name?.takeIf { it.isNotBlank() }
        ?: "Запуск #${run.runNumber}"

fun runMeta(run: GitHubWorkflowRun): String = buildString {
    append("${run.name ?: "workflow"} #${run.runNumber}")
    run.headBranch?.let { append(" · ").append(it) }
    run.event?.let { append(" · ").append(it) }
    append(" · ").append(run.updatedAt?.take(16)?.replace('T', ' ')?.take(10) ?: "—")
}

/** Only finished runs have a colour: in-flight runs must not look green. */
fun runKind(run: GitHubWorkflowRun): RunKind = when (run.conclusion) {
    "success" -> RunKind.SUCCESS
    "failure", "timed_out", "action_required" -> RunKind.FAILURE
    "cancelled", "skipped" -> RunKind.MUTED
    null -> RunKind.PENDING
    else -> RunKind.MUTED
}

enum class RunKind { SUCCESS, FAILURE, MUTED, PENDING }

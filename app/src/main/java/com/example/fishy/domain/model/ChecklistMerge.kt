package com.example.fishy.domain.model

/**
 * Merges planned shipment-checklist tasks with incomplete prep tasks.
 * Prep items with the same title (trim, case-insensitive) as an existing planned
 * item are skipped. Duplicate titles within prep are kept only once.
 */
fun mergeShipmentChecklist(
    planned: List<ChecklistTask>,
    incompletePrep: List<ChecklistTask>
): List<ChecklistTask> {
    fun key(title: String): String = title.trim().lowercase()

    val result = ArrayList<ChecklistTask>(planned.size + incompletePrep.size)
    val seen = HashSet<String>()

    for (task in planned) {
        result += task
        val k = key(task.title)
        if (k.isNotEmpty()) seen += k
    }
    for (prep in incompletePrep) {
        val k = key(prep.title)
        if (k.isEmpty() || k in seen) continue
        seen += k
        result += ChecklistTask(
            title = prep.title.trim(),
            isCompleted = false
        )
    }
    return result
}

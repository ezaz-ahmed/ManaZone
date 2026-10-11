package dev.ezaz.manazone.dnd

import android.app.NotificationManager
import android.content.Context

/** Interruption filter values, mirroring [NotificationManager.INTERRUPTION_FILTER_ALL] etc. */
object InterruptionFilter {
    const val UNKNOWN = 0
    const val ALL = 1
    const val PRIORITY = 2
    const val NONE = 3
    const val ALARMS = 4
}

/** What Manazone changed: the filter it found and the filter it applied. */
data class DndOwnership(val previousFilter: Int, val appliedFilter: Int)

/** Thin, fake-able wrapper over the platform DND API. */
interface DndController {
    fun hasAccess(): Boolean
    fun currentFilter(): Int
    fun setFilter(filter: Int)
}

/**
 * Pure DND decisions. Manazone only takes over DND when it is currently off, and only gives it back
 * if the filter is still exactly what Manazone set, so a later change by the user or another app
 * is never overwritten.
 */
object DndPolicy {
    fun engage(enabled: Boolean, controller: DndController): DndOwnership? {
        if (!enabled || !controller.hasAccess()) return null
        val current = controller.currentFilter()
        if (current != InterruptionFilter.ALL) return null // already in DND (or unknown): leave it alone
        controller.setFilter(InterruptionFilter.PRIORITY)
        return DndOwnership(previousFilter = current, appliedFilter = InterruptionFilter.PRIORITY)
    }

    /** Returns true if the previous filter was restored. */
    fun release(ownership: DndOwnership, controller: DndController): Boolean {
        if (!controller.hasAccess()) return false // access revoked: nothing we can safely do
        if (controller.currentFilter() != ownership.appliedFilter) return false // someone else changed it
        controller.setFilter(ownership.previousFilter)
        return true
    }
}

class AndroidDndController(context: Context) : DndController {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun hasAccess(): Boolean = manager.isNotificationPolicyAccessGranted

    override fun currentFilter(): Int = manager.currentInterruptionFilter

    override fun setFilter(filter: Int) {
        manager.setInterruptionFilter(filter)
    }
}

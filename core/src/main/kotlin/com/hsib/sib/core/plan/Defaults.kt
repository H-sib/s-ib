package com.hsib.sib.core.plan

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Importance

/** Starting points only. Every value is shown to the user and can be changed in one tap. */
object Defaults {
    fun effort(c: Category): Effort = when (c) {
        Category.EXAM -> Effort(480, 720, 1200)
        Category.ASSIGNMENT -> Effort(120, 240, 420)
        Category.APPLICATION -> Effort(60, 150, 300)
        Category.MEETING -> Effort(15, 45, 90)
        Category.APPOINTMENT -> Effort(10, 30, 60)
        Category.BILL -> Effort(5, 15, 30)
        Category.PERSONAL -> Effort(15, 45, 120)
        Category.OTHER -> Effort(20, 60, 150)
        Category.ITEM -> Effort(60, 120, 240)
        Category.CARD -> Effort(240, 480, 900)
        Category.TERM -> Effort(1200, 2400, 3600)
        Category.PROF, Category.SUPPLEMENTARY -> Effort(2400, 4800, 7200)
        Category.PRACTICAL, Category.WARD_FINAL -> Effort(300, 600, 1200)
        Category.REPORT -> Effort(120, 240, 480)
        else -> Effort(0, 0, 0)
    }

    fun importance(c: Category): Importance = when (c) {
        Category.EXAM, Category.APPLICATION, Category.CARD, Category.WARD_FINAL, Category.PRACTICAL -> Importance.HIGH
        Category.TERM, Category.PROF, Category.SUPPLEMENTARY -> Importance.CRITICAL
        Category.ANNIVERSARY, Category.BIRTHDAY -> Importance.HIGH
        else -> Importance.NORMAL
    }
}

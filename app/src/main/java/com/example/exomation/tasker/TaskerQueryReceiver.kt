package com.example.exomation.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.room.Room
import com.example.exomation.data.database.FitnessDatabase
import kotlinx.coroutines.runBlocking

/**
 * Responds to QUERY_CONDITION by returning
 * com.twofortyfouram.locale.intent.extra.RESULT_CONDITION_SATISFIED (1) or not (0).
 */
class TaskerQueryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras: Bundle = intent.extras ?: return
        val type = extras.getString(TaskerContracts.EXTRA_CONDITION_TYPE) ?: return
        val goalType = extras.getString(TaskerContracts.EXTRA_GOAL_TYPE) ?: "DAILY_STEPS"

        val db = FitnessDatabase.getInstance(context)
        val satisfied = when (type) {
            TaskerContracts.CONDITION_GOAL_REACHED -> runBlocking {
                val goal = db.exerciseGoalDao().getGoalByType(goalType)
                goal?.let { it.currentValue >= it.targetValue } ?: false
            }
            else -> false
        }

        val resultIntent = Intent().apply {
            putExtra("com.twofortyfouram.locale.intent.extra.RESULT_CODE", 1)
            putExtra(
                "com.twofortyfouram.locale.intent.extra.RESULT_CONDITION_SATISFIED",
                if (satisfied) 1 else 0
            )
        }
        setResult(1, null, resultIntent.extras)
    }
}



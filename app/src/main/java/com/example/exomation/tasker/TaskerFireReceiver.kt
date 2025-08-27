package com.example.exomation.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.exomation.services.StepCounterService

class TaskerFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.extras ?: return
        val action = bundle.getString(TaskerContracts.EXTRA_ACTION)
        Log.d("TaskerFire", "Received action: $action")
        when (action) {
            TaskerContracts.ACTION_START_TRACKING -> StepCounterService.startService(context)
            TaskerContracts.ACTION_STOP_TRACKING -> StepCounterService.stopService(context)
        }
    }
}



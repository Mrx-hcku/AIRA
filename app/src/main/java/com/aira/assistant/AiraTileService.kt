package com.aira.assistant

import android.content.Intent
import android.service.quicksettings.TileService

class AiraTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("AUTO_LISTEN", true)
        }
        startActivityAndCollapse(intent)
    }
}

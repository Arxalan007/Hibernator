package com.example.hibernator.services

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.hibernator.accessibility.HibernatorAccessibilityService

/**
 * HibernatorTileService
 * ======================
 * Provides a Quick Settings tile for fast access to hibernation.
 * Tapping it opens the main app.
 * Long-pressing it could be used to trigger last-used selection.
 */
class HibernatorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Open the main app
        val intent = Intent(this, com.example.hibernator.presentation.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (HibernatorAccessibilityService.isRunning()) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}

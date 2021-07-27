package com.shankar.tgp_arcore.util

import android.Manifest
import com.google.ar.sceneform.ux.ArFragment

class CustomArFragment : ArFragment() {
    override fun getAdditionalPermissions(): Array<String> {
        val additionalPermissions = super.getAdditionalPermissions()
        val permissions = Array(additionalPermissions.size + 1) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (additionalPermissions.isNotEmpty()) {
            System.arraycopy(
                additionalPermissions,
                0,
                permissions,
                1,
                additionalPermissions.size
            )
        }
        return permissions
    }
}
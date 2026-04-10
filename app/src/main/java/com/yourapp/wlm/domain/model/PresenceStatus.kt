package com.yourapp.wlm.domain.model

enum class PresenceStatus(val msnpCode: String, val displayName: String) {
    ONLINE("NLN", "Online"),
    AWAY("AWY", "Away"),
    BUSY("BSY", "Busy"),
    BE_RIGHT_BACK("BRB", "Be Right Back"),
    ON_THE_PHONE("PHN", "On the Phone"),
    OUT_TO_LUNCH("LUN", "Out to Lunch"),
    APPEAR_OFFLINE("HDN", "Appear Offline"),
    IDLE("IDL", "Idle"),
    OFFLINE("FLN", "Offline");

    companion object {
        fun fromMsnpCode(code: String): PresenceStatus {
            return entries.find { it.msnpCode == code } ?: OFFLINE
        }

        fun defaultStatus(): PresenceStatus = ONLINE
    }
}

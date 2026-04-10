package com.yourapp.wlm.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.presentation.theme.*

@Composable
fun StatusBadge(
    status: PresenceStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        PresenceStatus.ONLINE -> statusOnline
        PresenceStatus.AWAY -> statusAway
        PresenceStatus.BUSY -> statusBusy
        PresenceStatus.BE_RIGHT_BACK -> statusBRB
        PresenceStatus.ON_THE_PHONE -> statusPhone
        PresenceStatus.OUT_TO_LUNCH -> statusLunch
        PresenceStatus.APPEAR_OFFLINE -> statusHidden
        PresenceStatus.IDLE -> statusAway
        PresenceStatus.OFFLINE -> statusOffline
    }

    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White, CircleShape)
    )
}

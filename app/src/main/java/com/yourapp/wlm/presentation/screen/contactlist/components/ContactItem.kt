package com.yourapp.wlm.presentation.screen.contactlist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourapp.wlm.domain.model.Contact
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.presentation.common.AvatarImage
import com.yourapp.wlm.presentation.common.StatusBadge

@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AvatarImage(
                imageUrl = contact.avatarUrl,
                contentDescription = contact.displayName,
                size = 48
            )
            StatusBadge(
                status = contact.status,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = contact.displayName,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = if (contact.status == PresenceStatus.OFFLINE)
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                else
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            if (contact.personalMessage.isNotBlank()) {
                Text(
                    text = contact.personalMessage,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (contact.unreadCount > 0) {
            androidx.compose.material3.Badge {
                Text(text = contact.unreadCount.toString())
            }
        }
    }
}

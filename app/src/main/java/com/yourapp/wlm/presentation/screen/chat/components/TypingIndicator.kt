package com.yourapp.wlm.presentation.screen.chat.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TypingIndicator(
    contactName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThreeDotAnimation()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$contactName is typing...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThreeDotAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val color by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        targetValue = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(8.dp)
            ) {
                drawCircle(
                    color = color.copy(alpha = 0.3f + (index * 0.3f)),
                    radius = 4.dp.toPx()
                )
            }
        }
    }
}

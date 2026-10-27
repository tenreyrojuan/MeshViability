package com.example.meshviability.ui

import androidx.compose.ui.graphics.Color
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.LinkLevel

fun LinkLevel.color(): Color = when (this) {
    LinkLevel.SOLID -> Color(0xFF2E7D32)
    LinkLevel.ACCEPTABLE -> Color(0xFF9E9D24)
    LinkLevel.FRAGILE -> Color(0xFFEF6C00)
    LinkLevel.UNRELIABLE -> Color(0xFFC62828)
}

fun MessageStatus.color(): Color = when (this) {
    MessageStatus.PENDING -> Color(0xFF757575)
    MessageStatus.DELIVERED -> Color(0xFF2E7D32)
    MessageStatus.FAILED -> Color(0xFFC62828)
}

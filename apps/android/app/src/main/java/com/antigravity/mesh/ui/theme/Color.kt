package com.antigravity.mesh.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Antigravity Deep Obsidian Canvas & Surfaces
val BgDark = Color(0xFF080B14)
val SurfaceDark = Color(0xFF111728)
val SurfaceVariantDark = Color(0xFF182238)
val SurfaceElevated = Color(0xFF202C48)
val BorderDark = Color(0xFF212E48)
val BorderHighlight = Color(0xFF00D2FF)

// Antigravity & DeepMind Signature Accents
val AccentCyan = Color(0xFF00D2FF)       // Electric Cyan / Neon Teal
val AccentIndigo = Color(0xFF6366F1)     // Electric Indigo
val AccentViolet = Color(0xFF8B5CF6)     // Antigravity Purple / Sparkle
val AccentGreen = Color(0xFF10B981)      // Neon Emerald (Online)
val AccentAmber = Color(0xFFF59E0B)      // Warning / Attention
val AccentRed = Color(0xFFF43F5E)        // Rose Red (Offline / Error)

// High-Contrast Typography
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Antigravity Branded Gradients
val AntigravityGradient = Brush.horizontalGradient(
    listOf(Color(0xFF00D2FF), Color(0xFF818CF8), Color(0xFFA855F7))
)

val AntigravityButtonGradient = Brush.horizontalGradient(
    listOf(Color(0xFF0099FF), Color(0xFF7C3AED))
)

val AntigravityCardBorder = Brush.horizontalGradient(
    listOf(Color(0xFF00D2FF).copy(alpha = 0.7f), Color(0xFF8B5CF6).copy(alpha = 0.7f))
)

val AntigravityAvatarGradient = Brush.linearGradient(
    listOf(Color(0xFF00D2FF).copy(alpha = 0.25f), Color(0xFF8B5CF6).copy(alpha = 0.25f))
)

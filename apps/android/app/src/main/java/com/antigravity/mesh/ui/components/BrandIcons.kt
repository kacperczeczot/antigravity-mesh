package com.antigravity.mesh.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Custom vector brand icons for programming languages not included in standard Material Icons.
 */
object BrandIcons {

    /**
     * Official Python interlocking dual-snake vector icon (centered in 24x24 viewport).
     */
    val Python: ImageVector by lazy {
        ImageVector.Builder(
            name = "Python",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = PathParser().parsePathString(
                "M11.914 0C5.834 0 6.2 2.645 6.2 2.645l.006 2.742h5.81v.823H3.882s-3.882.44-3.882 5.707c0 5.267 3.39 5.093 3.39 5.093h2.025v-2.846s-.11-3.39 3.333-3.39h5.727s3.224.053 3.224-3.13V2.645S18.17 0 11.914 0zM8.7 1.702a1.05 1.05 0 1 1 0 2.1 1.05 1.05 0 0 1 0-2.1zm3.386 22.298c6.08 0 5.714-2.645 5.714-2.645l-.006-2.742h-5.81v-.823h8.134s3.882-.44 3.882-5.707c0-5.267-3.39-5.093-3.39-5.093h-2.025v2.846s.11 3.39-3.333 3.39H9.435s-3.224-.053-3.224 3.13v5.006s-.475 2.645 5.781 2.645zm3.214-1.702a1.05 1.05 0 1 1 0-2.1 1.05 1.05 0 0 1 0 2.1z"
            ).toNodes(),
            fill = SolidColor(Color.White)
        ).build()
    }
}

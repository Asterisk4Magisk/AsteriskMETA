package ui.theme

import androidx.compose.ui.graphics.Shape

internal enum class FocusDensity { Large, Medium, Compact }

internal enum class FocusTone { Primary, Inactive, Warning, Error, ReadOnly }

internal enum class ExpressiveShapeRole {
    FocusLarge,
    FocusMedium,
    FocusCompact,
    GroupLarge,
    ContentCard,
    NestedContainer,
    DenseContainer,
    Pill,
}

internal enum class ExpressiveInteractionState { Rest, Selected, Expanded, Disabled }

internal fun focusShapeRole(density: FocusDensity): ExpressiveShapeRole = when (density) {
    FocusDensity.Large -> ExpressiveShapeRole.FocusLarge
    FocusDensity.Medium -> ExpressiveShapeRole.FocusMedium
    FocusDensity.Compact -> ExpressiveShapeRole.FocusCompact
}

internal fun expressiveShape(role: ExpressiveShapeRole): Shape = when (role) {
    ExpressiveShapeRole.FocusLarge,
    ExpressiveShapeRole.FocusMedium,
    ExpressiveShapeRole.GroupLarge,
    -> AsteriskShapeTokens.HeroContainer

    ExpressiveShapeRole.FocusCompact,
    ExpressiveShapeRole.ContentCard,
    -> AsteriskShapeTokens.PageCard

    ExpressiveShapeRole.NestedContainer -> AsteriskShapeTokens.InnerContainer
    ExpressiveShapeRole.DenseContainer -> AsteriskShapeTokens.SmallContainer
    ExpressiveShapeRole.Pill -> AsteriskShapeTokens.Pill
}

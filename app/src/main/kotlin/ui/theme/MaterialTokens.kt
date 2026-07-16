@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val AsteriskShapes = Shapes(
    extraSmall = AsteriskShapeTokens.ExtraSmallContainer,
    small = AsteriskShapeTokens.SmallContainer,
    medium = AsteriskShapeTokens.InnerContainer,
    large = AsteriskShapeTokens.ListCard,
    extraLarge = AsteriskShapeTokens.PageCard,
    largeIncreased = AsteriskShapeTokens.PageCard,
    extraLargeIncreased = AsteriskShapeTokens.HeroContainer,
    extraExtraLarge = AsteriskShapeTokens.HeroContainer,
)

internal val AsteriskTypography = Typography()

internal object AsteriskShapeTokens {
    val ExtraSmallContainerRadius = 12.dp
    val SmallContainerRadius = 16.dp
    val InnerContainerRadius = 20.dp
    val ListCardRadius = 28.dp
    val PageCardRadius = 28.dp
    val SheetRadius = 28.dp
    val HeroContainerRadius = 28.dp

    val ExtraSmallContainer = RoundedCornerShape(ExtraSmallContainerRadius)
    val SmallContainer = RoundedCornerShape(SmallContainerRadius)
    val InnerContainer = RoundedCornerShape(InnerContainerRadius)
    val ListCard = RoundedCornerShape(ListCardRadius)
    val PageCard = RoundedCornerShape(PageCardRadius)
    val HeroContainer = RoundedCornerShape(HeroContainerRadius)
    val Sheet = RoundedCornerShape(
        topStart = SheetRadius,
        topEnd = SheetRadius,
    )
    val Pill = CircleShape
}

@Immutable
internal data class AsteriskSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val largePlus: Dp = 20.dp,
    val extraLarge: Dp = 24.dp,
    val huge: Dp = 32.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { AsteriskSpacing() }

package com.lottiefiles.example.core.util

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

@Preview(
    name = "Light Mode",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
// Annotation to generate previews for the views in Light and Dark Mode
@Deprecated("Use MobilePreview instead")
annotation class CombinedThemePreviews

@PreviewParameter(BooleanProvider::class)
@Preview(
    name = "Light Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
// Annotation to generate previews for the views in Light and Dark Mode with background

@Deprecated("Use MobilePreview instead")
annotation class CombinedThemePreviewsWithBackground

// Simple boolean [true, false] parameter provider for compose previews

class BooleanProvider : PreviewParameterProvider<Boolean> {
    override val values = listOf(true, false).asSequence()
}

// Simple String parameter provider for compose previews

class StringProvider(text: String) : PreviewParameterProvider<String?> {
    override val values = listOf(text, "", null).asSequence()
}

// Annotation for previews on mobile devices portrait (phones) in both Light and Dark mode, with background settings.
@Preview(
    name = "Phone Portrait- Light Mode with Background",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    device = "spec:width=411dp,height=891dp",
    showBackground = true,
    group = "themes",
    backgroundColor = 0xFFFFFFFF
)
@Preview(
    name = "Phone Portrait- Dark Mode with Background",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = "spec:width=411dp,height=891dp",
    showBackground = true,
    group = "themes",
    backgroundColor = 0xFF000000
)
annotation class MobilePortraitPreview

// Annotation for previews on tablet devices in both Light and Dark mode, with background settings.
@Preview(
    name = "Tablet Portrait- Light Mode with Background",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    device = "spec:width=800dp,height=1280dp",
    showBackground = true,
    group = "themes",
    backgroundColor = 0xFFFFFFFF
)
@Preview(
    name = "Tablet Portrait - Dark Mode with Background",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = "spec:width=800dp,height=1280dp",
    showBackground = true,
    group = "themes",
    backgroundColor = 0xFF000000
)
annotation class TabletPortraitPreview

// Annotation for previews on tablet devices (Landscape) in both Light and Dark mode, with background settings.
@Preview(
    name = "Tablet Landscape- Light Mode with Background",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    device = "spec:width=1280dp,height=800dp",
    showBackground = true,
    group = "themes",
    backgroundColor = 0xFFFFFFFF
)
@Preview(
    name = "Tablet Landscape- Dark Mode with Background",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = "spec:width=1280dp,height=800dp",
    showBackground = true,
    group = "themes",
    backgroundColor = 0xFF000000
)
annotation class TabletLandscapePreview

@MobilePortraitPreview
@TabletPortraitPreview
@TabletLandscapePreview
annotation class AllPreviews

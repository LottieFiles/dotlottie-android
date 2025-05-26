package com.lottiefiles.example.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lottiefiles.example.R
import com.lottiefiles.example.home.data.AnimationBundle
import com.lottiefiles.example.home.data.SectionType
import com.lottiefiles.example.home.data.User
import com.lottiefiles.example.util.PerformanceMonitor
import com.lottiefiles.example.performancetest.PerformanceOverlay
import com.lottiefiles.example.util.MobilePortraitPreview

enum class LottieLibraryType {
    DOT_LOTTIE,
    AIRBNB_LOTTIE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUIState,
    onAnimationClick: (AnimationBundle) -> Unit,
    onBackClick: (() -> Unit)? = null,
    libraryType: LottieLibraryType = LottieLibraryType.DOT_LOTTIE
) {
    val scrollState = rememberScrollState()
    val performanceMonitor = remember { PerformanceMonitor() }

    // Start performance monitoring
    DisposableEffect(Unit) {
        performanceMonitor.startMonitoring()
        onDispose {
            performanceMonitor.stopMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home (${libraryType.name})") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(paddingValues)
                        .padding(bottom = 60.dp)
            ) {
                // Featured Banner
                if (uiState.featuredAnimations.isNotEmpty()) {
                    val firstFeaturedAnimation = uiState.featuredAnimations.firstOrNull()?.jsonUrl

                    firstFeaturedAnimation?.let {
                        when (libraryType) {
                            LottieLibraryType.DOT_LOTTIE -> {
                                DotLottieView(
                                    url = it,
                                    modifier = Modifier.clickable { onAnimationClick(uiState.featuredAnimations.first()) }
                                )
                            }

                            LottieLibraryType.AIRBNB_LOTTIE -> {
                                AirbnbLottieView(
                                    url = it,
                                    modifier = Modifier.clickable { onAnimationClick(uiState.featuredAnimations.first()) }
                                )
                            }
                        }
                    }
                }
                // Rest of the sections
                if (uiState.featuredAnimations.isNotEmpty()) {
                    AnimationSection(
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        sectionType = SectionType.FeaturedAnimations,
                        animations =
                            uiState.featuredAnimations.filter {
                                !it.lottieUrl.isNullOrEmpty()
                            },
                        onAnimationClick = onAnimationClick,
                        libraryType = libraryType
                    )
                }

                if (uiState.popularAnimations.isNotEmpty()) {
                    AnimationSection(
                        sectionType = SectionType.PopularAnimations,
                        animations =
                            uiState.popularAnimations.filter {
                                !it.lottieUrl.isNullOrEmpty()
                            },
                        onAnimationClick = onAnimationClick,
                        libraryType = libraryType
                    )
                }

                if (uiState.recentAnimations.isNotEmpty()) {
                    AnimationSection(
                        sectionType = SectionType.RecentAnimations,
                        animations =
                            uiState.recentAnimations.filter {
                                !it.lottieUrl.isNullOrEmpty()
                            },
                        onAnimationClick = onAnimationClick,
                        libraryType = libraryType
                    )
                }
            }

            // Performance overlay
            PerformanceOverlay(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 56.dp)
            )
        }
    }
}

@Composable
fun AnimationSection(
    sectionType: SectionType,
    animations: List<AnimationBundle>,
    onAnimationClick: (AnimationBundle) -> Unit,
    libraryType: LottieLibraryType,
    modifier: Modifier = Modifier.padding(vertical = 16.dp)
) {
    val lazyListState = rememberLazyListState()

    val title =
        when (sectionType) {
            SectionType.FeaturedAnimations -> stringResource(R.string.home_featured_animations)
            SectionType.PopularAnimations -> stringResource(R.string.popular_animations)
            SectionType.RecentAnimations -> stringResource(R.string.recent_animations)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = animations,
            ) { animation ->
                AnimationCard(
                    animation = animation,
                    onClick = { onAnimationClick(animation) },
                    libraryType = libraryType
                )
            }
        }
    }
}

@Composable
fun AnimationCard(
    animation: AnimationBundle,
    onClick: () -> Unit,
    libraryType: LottieLibraryType
) {
    Card(
        modifier =
            Modifier
                .clickable { onClick() }
                .width(120.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick) {
        Column {
            when (libraryType) {
                LottieLibraryType.DOT_LOTTIE -> {
                    DotLottieView(
                        url = animation.jsonUrl ?: "",
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .align(Alignment.CenterHorizontally)
                    )
                }

                LottieLibraryType.AIRBNB_LOTTIE -> {
                    AirbnbLottieView(
                        url = animation.jsonUrl ?: "",
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .align(Alignment.CenterHorizontally)
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
            ) {
                Text(
                    text = animation.name ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = animation.createdBy?.firstName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@MobilePortraitPreview
@Composable
private fun HomeScreenPreview() {
    val previewState =
        HomeUIState(
            featuredAnimations = List(5) { AnimationBundle() },
            popularAnimations = List(3) { AnimationBundle() },
            recentAnimations = List(4) { AnimationBundle() }
        )

    HomeScreen(
        previewState,
        onAnimationClick = { _ -> },
        onBackClick = null,
        libraryType = LottieLibraryType.DOT_LOTTIE
    )
}

@MobilePortraitPreview
@Composable
private fun AnimationCardPreview() {
    AnimationCard(
        animation =
            AnimationBundle(
                name = "Cool Animation",
                createdBy = User(username = "John Doe")
            ),
        onClick = {},
        libraryType = LottieLibraryType.DOT_LOTTIE
    )
}


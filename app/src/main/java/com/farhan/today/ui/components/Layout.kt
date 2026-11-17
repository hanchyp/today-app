package com.farhan.today.ui.components

import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.statusBarsPadding

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.farhan.today.R
import com.farhan.today.ui.theme.Spacing
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType

/**
 * Inset standar sebuah layar: status bar di atas; di bawah, keyboard atau navigation bar,
 * mana yang lebih tinggi. Bottom nav (bila tampil) sudah dikonsumsi oleh Scaffold.
 */
fun Modifier.screenInsets(): Modifier = this.statusBarsPadding().bottomInsets()

/** Inset bawah saja (keyboard ∪ navigation bar), untuk layar yang top bar-nya mengurus status bar. */
// navigationBarsPadding mengonsumsi inset nav bar, sehingga imePadding sesudahnya hanya menambah selisihnya.
fun Modifier.bottomInsets(): Modifier = this.navigationBarsPadding().imePadding()

/**
 * Kolom gulir untuk layar tab utama. Satu-satunya sumber padding layar,
 * jadi konten tidak bergeser saat pindah tab, dan keyboard tidak menutupi field.
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    background: Color = TodayColors.Background,
    state: ScrollState = rememberScrollState(),
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .screenInsets()
            .verticalScroll(state)
            .padding(start = Spacing.ScreenH, end = Spacing.ScreenH, top = Spacing.ScreenTop, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Kerangka layar turunan: top app bar M3 yang menempel (panah kembali, judul, aksi),
 * isi yang bisa digulir, dan [bottomBar] opsional untuk aksi utama yang selalu terlihat
 * — termasuk saat keyboard terbuka.
 *
 * @param scrollable false bila [content] mengurus guliran sendiri (mis. LazyColumn dengan weight 1f).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = TodayColors.Background,
    contentColor: Color = TodayColors.Ink,
    scrollable: Boolean = true,
    state: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .bottomInsets()
            .nestedScroll(scroll.nestedScrollConnection),
    ) {
        TopAppBar(
            title = { Text(title, style = TodayType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), "Kembali")
                }
            },
            actions = actions,
            scrollBehavior = scroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = background,
                scrolledContainerColor = if (background == TodayColors.Background) TodayColors.Surface else background,
                titleContentColor = contentColor,
                navigationIconContentColor = contentColor,
                actionIconContentColor = contentColor,
            ),
        )
        if (scrollable) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(state)
                    .padding(start = Spacing.ScreenH, end = Spacing.ScreenH, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
        if (bottomBar != null) {
            HorizontalDivider(color = if (background == TodayColors.Background) TodayColors.NavStroke else Color.White.copy(alpha = 0.08f))
            Column(
                Modifier.fillMaxWidth().background(background).padding(horizontal = Spacing.ScreenH, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = bottomBar,
            )
        }
    }
}

/** Tombol ikon untuk slot aksi top bar. */
@Composable
fun TopBarAction(@DrawableRes icon: Int, description: String, tint: Color = TodayColors.Ink, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(icon), description, tint = tint) }
}

/** Kerangka abu-abu berdenyut selama data dimuat, supaya halaman tidak masuk dalam keadaan kosong. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 4, showHeader: Boolean = true) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
    )
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Memuat" }.graphicsLayer { alpha = pulse },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHeader) {
            Box(Modifier.fillMaxWidth(0.55f).height(32.dp).clip(TodayShapes.Chip).background(TodayColors.Chip))
            Box(Modifier.fillMaxWidth(0.8f).height(16.dp).clip(TodayShapes.Chip).background(TodayColors.Chip))
        }
        Box(Modifier.fillMaxWidth().height(112.dp).clip(TodayShapes.Hero).background(TodayColors.Chip))
        repeat(rows) { Box(Modifier.fillMaxWidth().height(64.dp).clip(TodayShapes.Card).background(TodayColors.Chip)) }
    }
}

/** Layar turunan yang masih memuat: top bar sudah tampil, isi berupa kerangka. */
@Composable
fun LoadingDetail(title: String, onBack: () -> Unit, background: Color = TodayColors.Background) {
    DetailScaffold(title, onBack, background = background) { LoadingSkeleton(showHeader = false) }
}

/** FAB "Tambah" yang sama di semua tab; [expanded] false = hanya ikon (saat digulir turun). */
@Composable
fun TodayFab(text: String, expanded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        text = { Text(text, style = TodayType.Label) },
        icon = { Icon(painterResource(R.drawable.ic_add), null) },
        onClick = onClick,
        expanded = expanded,
        modifier = modifier.padding(16.dp),
        containerColor = TodayColors.BrandHero,
        contentColor = TodayColors.OnBrandHero,
    )
}

/** FAB melebar saat di atas atau saat menggulir ke atas. */
val ScrollState.fabExpanded: Boolean get() = !canScrollBackward || lastScrolledBackward

/** Kolom pencarian: ikon kaca pembesar, tombol hapus, dan tombol "Cari" di keyboard. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = TodayType.Body) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp), tint = TodayColors.Muted) },
        trailingIcon = if (query.isEmpty()) null else ({
            IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(R.drawable.ic_close), "Hapus pencarian", tint = TodayColors.Muted)
            }
        }),
        singleLine = true,
        shape = TodayShapes.Chip,
        textStyle = TodayType.Body,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TodayColors.Surface,
            unfocusedContainerColor = TodayColors.Surface,
            unfocusedBorderColor = TodayColors.NavStroke,
            focusedBorderColor = TodayColors.Brand,
        ),
    )
}

/**
 * Penjaga perubahan yang belum disimpan. Gestur/tombol kembali sistem dan lambda yang
 * dikembalikan (untuk panah top bar) sama-sama menanyakan konfirmasi bila [dirty].
 */
@Composable
fun rememberBackGuard(dirty: Boolean, onBack: () -> Unit): () -> Unit {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { ask = true }
    if (ask) {
        ConfirmDialog(
            "Buang perubahan?", "Isian yang belum disimpan akan hilang.", "Buang",
            onConfirm = { ask = false; onBack() }, onDismiss = { ask = false }, destructive = true,
        )
    }
    return { if (dirty) ask = true else onBack() }
}

/** Layar tidak mati selama composable ini tampil (mis. sesi latihan). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Ikon status bar terang di atas layar gelap; dikembalikan saat layar ditinggalkan. */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/** Getar singkat untuk konfirmasi (centang set/tugas, timer habis). */
@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }
    }
}
